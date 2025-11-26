package com.arcana.cloud.plugin.runtime.http;

import com.arcana.cloud.plugin.lifecycle.PluginState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

/**
 * HTTP client for plugin communication in layered/K8s deployments.
 *
 * <p>This client handles communication between layers when using HTTP protocol
 * instead of gRPC. It provides:</p>
 * <ul>
 *   <li>Plugin installation/uninstallation across layers</li>
 *   <li>Plugin state synchronization</li>
 *   <li>Plugin health checks</li>
 *   <li>Plugin endpoint proxying</li>
 * </ul>
 */
public class PluginHttpClient {

    private static final Logger log = LoggerFactory.getLogger(PluginHttpClient.class);

    private final RestTemplate restTemplate;
    private final String serviceLayerUrl;
    private final int maxRetries;
    private final Duration retryDelay;

    public PluginHttpClient(RestTemplate restTemplate, String serviceLayerUrl) {
        this(restTemplate, serviceLayerUrl, 3, Duration.ofMillis(500));
    }

    public PluginHttpClient(RestTemplate restTemplate, String serviceLayerUrl,
                           int maxRetries, Duration retryDelay) {
        this.restTemplate = restTemplate;
        this.serviceLayerUrl = serviceLayerUrl.endsWith("/")
            ? serviceLayerUrl.substring(0, serviceLayerUrl.length() - 1)
            : serviceLayerUrl;
        this.maxRetries = maxRetries;
        this.retryDelay = retryDelay;
    }

    /**
     * Lists all plugins from the service layer.
     *
     * @return list of plugin information
     */
    @SuppressWarnings("unchecked")
    public List<PluginInfo> listPlugins() {
        String url = serviceLayerUrl + "/api/v1/plugins";
        try {
            ResponseEntity<Map> response = executeWithRetry(
                () -> restTemplate.getForEntity(url, Map.class)
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
                if (data != null) {
                    return data.stream()
                        .map(this::mapToPluginInfo)
                        .toList();
                }
            }
        } catch (Exception e) {
            log.error("Failed to list plugins from service layer", e);
        }
        return Collections.emptyList();
    }

    /**
     * Gets plugin details from the service layer.
     *
     * @param pluginKey the plugin key
     * @return plugin information, or empty if not found
     */
    @SuppressWarnings("unchecked")
    public Optional<PluginInfo> getPlugin(String pluginKey) {
        String url = serviceLayerUrl + "/api/v1/plugins/" + pluginKey;
        try {
            ResponseEntity<Map> response = executeWithRetry(
                () -> restTemplate.getForEntity(url, Map.class)
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                if (data != null) {
                    return Optional.of(mapToPluginInfo(data));
                }
            }
        } catch (Exception e) {
            log.debug("Plugin not found: {}", pluginKey);
        }
        return Optional.empty();
    }

    /**
     * Enables a plugin on the service layer.
     *
     * @param pluginKey the plugin key
     * @return true if successful
     */
    public boolean enablePlugin(String pluginKey) {
        String url = serviceLayerUrl + "/api/v1/plugins/" + pluginKey + "/enable";
        try {
            ResponseEntity<Map> response = executeWithRetry(
                () -> restTemplate.postForEntity(url, null, Map.class)
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to enable plugin: {}", pluginKey, e);
            return false;
        }
    }

    /**
     * Disables a plugin on the service layer.
     *
     * @param pluginKey the plugin key
     * @return true if successful
     */
    public boolean disablePlugin(String pluginKey) {
        String url = serviceLayerUrl + "/api/v1/plugins/" + pluginKey + "/disable";
        try {
            ResponseEntity<Map> response = executeWithRetry(
                () -> restTemplate.postForEntity(url, null, Map.class)
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Failed to disable plugin: {}", pluginKey, e);
            return false;
        }
    }

    /**
     * Checks if the service layer plugins are ready.
     *
     * @return true if plugins are initialized
     */
    @SuppressWarnings("unchecked")
    public boolean isServiceLayerReady() {
        String url = serviceLayerUrl + "/api/v1/plugins/health/ready";
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return "UP".equals(response.getBody().get("status"));
            }
        } catch (Exception e) {
            log.debug("Service layer not ready: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Gets health status from the service layer.
     *
     * @return health status map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getServiceLayerHealth() {
        String url = serviceLayerUrl + "/api/v1/plugins/health";
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Failed to get service layer health", e);
        }
        return Map.of("status", "DOWN", "error", "Unable to reach service layer");
    }

    /**
     * Proxies a request to a plugin endpoint on the service layer.
     *
     * @param pluginKey the plugin key
     * @param path the endpoint path within the plugin
     * @param method the HTTP method
     * @param body the request body (can be null)
     * @param headers the request headers
     * @return the response entity
     */
    public ResponseEntity<String> proxyPluginRequest(
            String pluginKey, String path, HttpMethod method,
            Object body, HttpHeaders headers) {

        String url = serviceLayerUrl + "/api/v1/plugins/" + pluginKey + path;

        try {
            HttpEntity<?> requestEntity = new HttpEntity<>(body, headers);
            return restTemplate.exchange(url, method, requestEntity, String.class);
        } catch (RestClientException e) {
            log.error("Failed to proxy request to plugin {}: {}", pluginKey, path, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("{\"error\":\"Failed to reach plugin: " + e.getMessage() + "\"}");
        }
    }

    /**
     * Synchronizes plugin state with the service layer.
     *
     * @param localPlugins local plugin states
     * @return plugins that need to be synchronized
     */
    public List<PluginSyncAction> synchronizePlugins(Map<String, PluginState> localPlugins) {
        List<PluginSyncAction> actions = new ArrayList<>();

        // Get remote plugins
        List<PluginInfo> remotePlugins = listPlugins();
        Map<String, PluginInfo> remoteMap = new HashMap<>();
        for (PluginInfo plugin : remotePlugins) {
            remoteMap.put(plugin.getKey(), plugin);
        }

        // Find plugins to install locally
        for (PluginInfo remote : remotePlugins) {
            if (!localPlugins.containsKey(remote.getKey())) {
                actions.add(new PluginSyncAction(
                    PluginSyncAction.Action.INSTALL,
                    remote.getKey(),
                    remote
                ));
            }
        }

        // Find plugins to remove locally
        for (String localKey : localPlugins.keySet()) {
            if (!remoteMap.containsKey(localKey)) {
                actions.add(new PluginSyncAction(
                    PluginSyncAction.Action.UNINSTALL,
                    localKey,
                    null
                ));
            }
        }

        // Find state mismatches
        for (Map.Entry<String, PluginState> entry : localPlugins.entrySet()) {
            PluginInfo remote = remoteMap.get(entry.getKey());
            if (remote != null) {
                PluginState remoteState = parseState(remote.getState());
                if (remoteState != entry.getValue()) {
                    actions.add(new PluginSyncAction(
                        remoteState == PluginState.ACTIVE
                            ? PluginSyncAction.Action.ENABLE
                            : PluginSyncAction.Action.DISABLE,
                        entry.getKey(),
                        remote
                    ));
                }
            }
        }

        return actions;
    }

    private <T> T executeWithRetry(java.util.function.Supplier<T> action) {
        Exception lastException = null;
        for (int i = 0; i < maxRetries; i++) {
            try {
                return action.get();
            } catch (RestClientException e) {
                lastException = e;
                log.debug("Request failed (attempt {}), retrying...", i + 1);
                try {
                    Thread.sleep(retryDelay.toMillis() * (i + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
        }
        throw new RuntimeException("Request failed after " + maxRetries + " retries", lastException);
    }

    @SuppressWarnings("unchecked")
    private PluginInfo mapToPluginInfo(Map<String, Object> data) {
        PluginInfo info = new PluginInfo();
        info.setKey((String) data.get("key"));
        info.setName((String) data.get("name"));
        info.setVersion((String) data.get("version"));
        info.setState((String) data.get("state"));
        info.setMetadata((Map<String, String>) data.get("metadata"));
        return info;
    }

    private PluginState parseState(String state) {
        if (state == null) return PluginState.INSTALLED;
        return switch (state.toUpperCase()) {
            case "ACTIVE" -> PluginState.ACTIVE;
            case "RESOLVED" -> PluginState.RESOLVED;
            case "INSTALLED" -> PluginState.INSTALLED;
            case "UNINSTALLED" -> PluginState.UNINSTALLED;
            default -> PluginState.INSTALLED;
        };
    }

    /**
     * Plugin information from HTTP response.
     */
    public static class PluginInfo {
        private String key;
        private String name;
        private String version;
        private String state;
        private Map<String, String> metadata;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public String getState() { return state; }
        public void setState(String state) { this.state = state; }

        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    }

    /**
     * Plugin synchronization action.
     */
    public static class PluginSyncAction {
        public enum Action { INSTALL, UNINSTALL, ENABLE, DISABLE }

        private final Action action;
        private final String pluginKey;
        private final PluginInfo pluginInfo;

        public PluginSyncAction(Action action, String pluginKey, PluginInfo pluginInfo) {
            this.action = action;
            this.pluginKey = pluginKey;
            this.pluginInfo = pluginInfo;
        }

        public Action getAction() { return action; }
        public String getPluginKey() { return pluginKey; }
        public PluginInfo getPluginInfo() { return pluginInfo; }
    }
}
