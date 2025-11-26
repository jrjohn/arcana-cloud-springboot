package com.arcana.cloud.plugin.runtime.distributed;

import com.arcana.cloud.plugin.lifecycle.PluginState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

/**
 * HTTP-based distributed plugin registry client.
 *
 * <p>This client provides an alternative to Redis-based distribution
 * for environments where Redis is not available. It uses HTTP calls
 * to synchronize plugin state across instances.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>HTTP-based plugin state synchronization</li>
 *   <li>Works in environments without Redis</li>
 *   <li>Compatible with Kubernetes service discovery</li>
 *   <li>Supports multiple service instances via K8s headless services</li>
 * </ul>
 */
public class HttpPluginRegistryClient {

    private static final Logger log = LoggerFactory.getLogger(HttpPluginRegistryClient.class);

    private final RestTemplate restTemplate;
    private final List<String> peerUrls;
    private final String instanceId;
    private final Map<String, PluginRegistryEntry> localRegistry;

    public HttpPluginRegistryClient(RestTemplate restTemplate, List<String> peerUrls, String instanceId) {
        this.restTemplate = restTemplate;
        this.peerUrls = new ArrayList<>(peerUrls);
        this.instanceId = instanceId;
        this.localRegistry = new LinkedHashMap<>();
    }

    /**
     * Registers a plugin locally and broadcasts to peers.
     *
     * @param pluginKey the plugin key
     * @param name the plugin name
     * @param version the plugin version
     * @param state the plugin state
     */
    public void registerPlugin(String pluginKey, String name, String version, PluginState state) {
        PluginRegistryEntry entry = new PluginRegistryEntry(
            pluginKey, name, version, state, instanceId, Instant.now()
        );
        localRegistry.put(pluginKey, entry);

        // Broadcast to peers
        broadcastPluginEvent("INSTALLED", entry);
    }

    /**
     * Updates plugin state locally and broadcasts to peers.
     *
     * @param pluginKey the plugin key
     * @param state the new state
     */
    public void updatePluginState(String pluginKey, PluginState state) {
        PluginRegistryEntry entry = localRegistry.get(pluginKey);
        if (entry != null) {
            entry.setState(state);
            entry.setLastModified(Instant.now());
            entry.setLastModifiedBy(instanceId);

            String eventType = state == PluginState.ACTIVE ? "ENABLED" : "DISABLED";
            broadcastPluginEvent(eventType, entry);
        }
    }

    /**
     * Unregisters a plugin locally and broadcasts to peers.
     *
     * @param pluginKey the plugin key
     */
    public void unregisterPlugin(String pluginKey) {
        PluginRegistryEntry entry = localRegistry.remove(pluginKey);
        if (entry != null) {
            entry.setState(PluginState.UNINSTALLED);
            broadcastPluginEvent("UNINSTALLED", entry);
        }
    }

    /**
     * Synchronizes plugin registry with all peers.
     *
     * @return list of plugins from all peers
     */
    @SuppressWarnings("unchecked")
    public Map<String, PluginRegistryEntry> synchronizeWithPeers() {
        Map<String, PluginRegistryEntry> allPlugins = new LinkedHashMap<>(localRegistry);

        for (String peerUrl : peerUrls) {
            try {
                String url = peerUrl + "/api/v1/plugins/registry/entries";
                ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    List<Map<String, Object>> entries =
                        (List<Map<String, Object>>) response.getBody().get("data");

                    if (entries != null) {
                        for (Map<String, Object> entryData : entries) {
                            PluginRegistryEntry entry = mapToEntry(entryData);

                            // Merge: keep the most recent entry
                            PluginRegistryEntry existing = allPlugins.get(entry.getPluginKey());
                            if (existing == null ||
                                entry.getLastModified().isAfter(existing.getLastModified())) {
                                allPlugins.put(entry.getPluginKey(), entry);
                            }
                        }
                    }
                }
            } catch (RestClientException e) {
                log.warn("Failed to sync with peer {}: {}", peerUrl, e.getMessage());
            }
        }

        return allPlugins;
    }

    /**
     * Gets all local registry entries.
     *
     * @return list of local entries
     */
    public List<PluginRegistryEntry> getLocalEntries() {
        return new ArrayList<>(localRegistry.values());
    }

    /**
     * Receives a plugin event from a peer.
     *
     * @param eventType the event type
     * @param entry the plugin entry
     */
    public void receivePluginEvent(String eventType, PluginRegistryEntry entry) {
        // Skip events from this instance
        if (instanceId.equals(entry.getLastModifiedBy())) {
            return;
        }

        log.info("Received plugin event from peer: {} for {}", eventType, entry.getPluginKey());

        switch (eventType) {
            case "INSTALLED", "ENABLED", "DISABLED", "UPDATED" -> {
                PluginRegistryEntry existing = localRegistry.get(entry.getPluginKey());
                if (existing == null ||
                    entry.getLastModified().isAfter(existing.getLastModified())) {
                    localRegistry.put(entry.getPluginKey(), entry);
                }
            }
            case "UNINSTALLED" -> localRegistry.remove(entry.getPluginKey());
        }
    }

    /**
     * Broadcasts a plugin event to all peers.
     */
    private void broadcastPluginEvent(String eventType, PluginRegistryEntry entry) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", eventType);
        event.put("pluginKey", entry.getPluginKey());
        event.put("name", entry.getName());
        event.put("version", entry.getVersion());
        event.put("state", entry.getState().name());
        event.put("sourceInstance", instanceId);
        event.put("timestamp", Instant.now().toString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(event, headers);

        for (String peerUrl : peerUrls) {
            try {
                String url = peerUrl + "/api/v1/plugins/registry/events";
                restTemplate.postForEntity(url, request, Void.class);
                log.debug("Broadcasted {} event to {}", eventType, peerUrl);
            } catch (RestClientException e) {
                log.warn("Failed to broadcast to peer {}: {}", peerUrl, e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private PluginRegistryEntry mapToEntry(Map<String, Object> data) {
        return new PluginRegistryEntry(
            (String) data.get("pluginKey"),
            (String) data.get("name"),
            (String) data.get("version"),
            PluginState.valueOf((String) data.getOrDefault("state", "INSTALLED")),
            (String) data.get("lastModifiedBy"),
            Instant.parse((String) data.getOrDefault("lastModified", Instant.now().toString()))
        );
    }

    /**
     * Plugin registry entry for HTTP distribution.
     */
    public static class PluginRegistryEntry {
        private String pluginKey;
        private String name;
        private String version;
        private PluginState state;
        private String lastModifiedBy;
        private Instant lastModified;

        public PluginRegistryEntry() {}

        public PluginRegistryEntry(String pluginKey, String name, String version,
                                   PluginState state, String lastModifiedBy, Instant lastModified) {
            this.pluginKey = pluginKey;
            this.name = name;
            this.version = version;
            this.state = state;
            this.lastModifiedBy = lastModifiedBy;
            this.lastModified = lastModified;
        }

        public String getPluginKey() { return pluginKey; }
        public void setPluginKey(String pluginKey) { this.pluginKey = pluginKey; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public PluginState getState() { return state; }
        public void setState(PluginState state) { this.state = state; }

        public String getLastModifiedBy() { return lastModifiedBy; }
        public void setLastModifiedBy(String lastModifiedBy) { this.lastModifiedBy = lastModifiedBy; }

        public Instant getLastModified() { return lastModified; }
        public void setLastModified(Instant lastModified) { this.lastModified = lastModified; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("pluginKey", pluginKey);
            map.put("name", name);
            map.put("version", version);
            map.put("state", state != null ? state.name() : "INSTALLED");
            map.put("lastModifiedBy", lastModifiedBy);
            map.put("lastModified", lastModified != null ? lastModified.toString() : null);
            return map;
        }
    }
}
