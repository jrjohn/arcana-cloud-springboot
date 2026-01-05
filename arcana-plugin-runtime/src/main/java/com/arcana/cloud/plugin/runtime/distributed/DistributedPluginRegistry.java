package com.arcana.cloud.plugin.runtime.distributed;

import com.arcana.cloud.plugin.api.PluginDescriptor;
import com.arcana.cloud.plugin.lifecycle.PluginState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.listener.ChannelTopic;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Distributed plugin registry using Redis.
 *
 * <p>This registry maintains plugin state across multiple instances in a Kubernetes cluster.
 * It uses Redis for persistence and pub/sub for real-time synchronization.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Plugin state synchronization across cluster</li>
 *   <li>Real-time notifications via Redis pub/sub</li>
 *   <li>Leader election for plugin management operations</li>
 *   <li>Automatic cleanup of stale entries</li>
 * </ul>
 */
public class DistributedPluginRegistry {

    private static final Logger log = LoggerFactory.getLogger(DistributedPluginRegistry.class);

    private static final String PLUGINS_HASH_KEY = "arcana:plugins";
    @SuppressWarnings("unused")
    private static final String PLUGIN_STATES_HASH_KEY = "arcana:plugin:states";
    private static final String PLUGIN_INSTANCES_KEY = "arcana:plugin:instances";
    private static final String PLUGIN_EVENTS_CHANNEL = "arcana:plugin:events";
    private static final String LEADER_KEY = "arcana:plugin:leader";
    private static final Duration LEADER_TTL = Duration.ofSeconds(30);
    private static final Duration INSTANCE_TTL = Duration.ofSeconds(60);

    private final RedisTemplate<String, Object> redisTemplate;
    private final HashOperations<String, String, PluginRegistryEntry> hashOps;
    private final String instanceId;
    private final List<Consumer<PluginEvent>> eventListeners;
    private final Map<String, PluginRegistryEntry> localCache;

    private volatile boolean isLeader = false;
    @SuppressWarnings("unused")
    private volatile boolean running = false;

    public DistributedPluginRegistry(RedisTemplate<String, Object> redisTemplate, String instanceId) {
        this.redisTemplate = redisTemplate;
        this.hashOps = redisTemplate.opsForHash();
        this.instanceId = instanceId;
        this.eventListeners = new ArrayList<>();
        this.localCache = new ConcurrentHashMap<>();
    }

    /**
     * Initializes the distributed registry.
     */
    public void initialize() {
        running = true;
        registerInstance();
        loadFromRedis();
        log.info("Distributed plugin registry initialized for instance: {}", instanceId);
    }

    /**
     * Shuts down the distributed registry.
     */
    public void shutdown() {
        running = false;
        unregisterInstance();
        releaseLeadership();
        log.info("Distributed plugin registry shutdown for instance: {}", instanceId);
    }

    /**
     * Registers a plugin in the distributed registry.
     *
     * @param pluginKey the plugin key
     * @param descriptor the plugin descriptor
     * @param state the initial state
     */
    public void registerPlugin(String pluginKey, PluginDescriptor descriptor, PluginState state) {
        PluginRegistryEntry entry = new PluginRegistryEntry(
            pluginKey,
            descriptor.getName(),
            descriptor.getVersion(),
            state,
            instanceId,
            Instant.now()
        );

        hashOps.put(PLUGINS_HASH_KEY, pluginKey, entry);
        localCache.put(pluginKey, entry);

        publishEvent(new PluginEvent(PluginEventType.INSTALLED, pluginKey, instanceId, state));
        log.info("Plugin {} registered in distributed registry", pluginKey);
    }

    /**
     * Updates plugin state in the distributed registry.
     *
     * @param pluginKey the plugin key
     * @param state the new state
     */
    public void updatePluginState(String pluginKey, PluginState state) {
        PluginRegistryEntry existing = hashOps.get(PLUGINS_HASH_KEY, pluginKey);
        if (existing != null) {
            PluginRegistryEntry updated = new PluginRegistryEntry(
                existing.getPluginKey(),
                existing.getName(),
                existing.getVersion(),
                state,
                instanceId,
                Instant.now()
            );

            hashOps.put(PLUGINS_HASH_KEY, pluginKey, updated);
            localCache.put(pluginKey, updated);

            PluginEventType eventType = state == PluginState.ACTIVE
                ? PluginEventType.ENABLED
                : PluginEventType.DISABLED;
            publishEvent(new PluginEvent(eventType, pluginKey, instanceId, state));
            log.info("Plugin {} state updated to {} in distributed registry", pluginKey, state);
        }
    }

    /**
     * Unregisters a plugin from the distributed registry.
     *
     * @param pluginKey the plugin key
     */
    public void unregisterPlugin(String pluginKey) {
        hashOps.delete(PLUGINS_HASH_KEY, pluginKey);
        localCache.remove(pluginKey);

        publishEvent(new PluginEvent(PluginEventType.UNINSTALLED, pluginKey, instanceId, PluginState.UNINSTALLED));
        log.info("Plugin {} unregistered from distributed registry", pluginKey);
    }

    /**
     * Returns all registered plugins.
     *
     * @return map of plugin key to registry entry
     */
    public Map<String, PluginRegistryEntry> getAllPlugins() {
        Map<String, PluginRegistryEntry> entries = hashOps.entries(PLUGINS_HASH_KEY);
        return entries != null ? entries : Collections.emptyMap();
    }

    /**
     * Returns a plugin by key.
     *
     * @param pluginKey the plugin key
     * @return the registry entry, or null if not found
     */
    public PluginRegistryEntry getPlugin(String pluginKey) {
        return hashOps.get(PLUGINS_HASH_KEY, pluginKey);
    }

    /**
     * Returns plugins that this instance should have installed.
     *
     * @return set of plugin keys
     */
    public Set<String> getExpectedPlugins() {
        Map<String, PluginRegistryEntry> allPlugins = getAllPlugins();
        return new HashSet<>(allPlugins.keySet());
    }

    /**
     * Returns plugins missing from this instance.
     *
     * @param installedPlugins plugins currently installed locally
     * @return set of missing plugin keys
     */
    public Set<String> getMissingPlugins(Set<String> installedPlugins) {
        Set<String> expected = getExpectedPlugins();
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(installedPlugins);
        return missing;
    }

    /**
     * Attempts to acquire leadership for plugin management operations.
     *
     * @return true if leadership acquired
     */
    public boolean tryAcquireLeadership() {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
            LEADER_KEY,
            instanceId,
            LEADER_TTL
        );

        if (Boolean.TRUE.equals(acquired)) {
            isLeader = true;
            log.info("Instance {} acquired plugin management leadership", instanceId);
            return true;
        }

        // Check if we already hold leadership
        Object currentLeader = redisTemplate.opsForValue().get(LEADER_KEY);
        if (instanceId.equals(currentLeader)) {
            // Refresh TTL
            redisTemplate.expire(LEADER_KEY, LEADER_TTL);
            isLeader = true;
            return true;
        }

        isLeader = false;
        return false;
    }

    /**
     * Releases leadership.
     */
    public void releaseLeadership() {
        if (isLeader) {
            Object currentLeader = redisTemplate.opsForValue().get(LEADER_KEY);
            if (instanceId.equals(currentLeader)) {
                redisTemplate.delete(LEADER_KEY);
                log.info("Instance {} released plugin management leadership", instanceId);
            }
            isLeader = false;
        }
    }

    /**
     * Returns whether this instance is the leader.
     *
     * @return true if leader
     */
    public boolean isLeader() {
        return isLeader;
    }

    /**
     * Adds an event listener.
     *
     * @param listener the listener
     */
    public void addEventListener(Consumer<PluginEvent> listener) {
        eventListeners.add(listener);
    }

    /**
     * Removes an event listener.
     *
     * @param listener the listener
     */
    public void removeEventListener(Consumer<PluginEvent> listener) {
        eventListeners.remove(listener);
    }

    /**
     * Handles incoming plugin events from other instances.
     *
     * @param event the event
     */
    public void handleEvent(PluginEvent event) {
        // Skip events from this instance
        if (instanceId.equals(event.getSourceInstance())) {
            return;
        }

        log.debug("Received plugin event: {} for {} from {}",
            event.getType(), event.getPluginKey(), event.getSourceInstance());

        // Update local cache
        if (event.getType() == PluginEventType.UNINSTALLED) {
            localCache.remove(event.getPluginKey());
        } else {
            PluginRegistryEntry entry = getPlugin(event.getPluginKey());
            if (entry != null) {
                localCache.put(event.getPluginKey(), entry);
            }
        }

        // Notify listeners
        for (Consumer<PluginEvent> listener : eventListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("Error notifying plugin event listener", e);
            }
        }
    }

    private void publishEvent(PluginEvent event) {
        try {
            redisTemplate.convertAndSend(PLUGIN_EVENTS_CHANNEL, event);
        } catch (Exception e) {
            log.error("Failed to publish plugin event", e);
        }
    }

    private void registerInstance() {
        String instanceKey = PLUGIN_INSTANCES_KEY + ":" + instanceId;
        redisTemplate.opsForValue().set(instanceKey, Instant.now().toString(), INSTANCE_TTL);
    }

    private void unregisterInstance() {
        String instanceKey = PLUGIN_INSTANCES_KEY + ":" + instanceId;
        redisTemplate.delete(instanceKey);
    }

    private void loadFromRedis() {
        Map<String, PluginRegistryEntry> entries = getAllPlugins();
        localCache.clear();
        localCache.putAll(entries);
        log.debug("Loaded {} plugins from distributed registry", entries.size());
    }

    /**
     * Returns the channel topic for plugin events.
     *
     * @return the channel topic
     */
    public static ChannelTopic getEventsTopic() {
        return new ChannelTopic(PLUGIN_EVENTS_CHANNEL);
    }

    /**
     * Plugin registry entry stored in Redis.
     */
    public static class PluginRegistryEntry implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

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
    }

    /**
     * Plugin event types.
     */
    public enum PluginEventType {
        INSTALLED,
        UNINSTALLED,
        ENABLED,
        DISABLED,
        UPDATED
    }

    /**
     * Plugin event for pub/sub.
     */
    public static class PluginEvent implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        private PluginEventType type;
        private String pluginKey;
        private String sourceInstance;
        private PluginState state;
        private Instant timestamp;

        public PluginEvent() {}

        public PluginEvent(PluginEventType type, String pluginKey, String sourceInstance, PluginState state) {
            this.type = type;
            this.pluginKey = pluginKey;
            this.sourceInstance = sourceInstance;
            this.state = state;
            this.timestamp = Instant.now();
        }

        public PluginEventType getType() { return type; }
        public void setType(PluginEventType type) { this.type = type; }

        public String getPluginKey() { return pluginKey; }
        public void setPluginKey(String pluginKey) { this.pluginKey = pluginKey; }

        public String getSourceInstance() { return sourceInstance; }
        public void setSourceInstance(String sourceInstance) { this.sourceInstance = sourceInstance; }

        public PluginState getState() { return state; }
        public void setState(PluginState state) { this.state = state; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    }
}
