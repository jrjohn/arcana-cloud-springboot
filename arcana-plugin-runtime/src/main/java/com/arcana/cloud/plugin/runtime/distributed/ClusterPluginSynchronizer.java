package com.arcana.cloud.plugin.runtime.distributed;

import com.arcana.cloud.plugin.lifecycle.PluginState;
import com.arcana.cloud.plugin.runtime.PluginManager;
import com.arcana.cloud.plugin.runtime.distributed.DistributedPluginRegistry.PluginEvent;
import com.arcana.cloud.plugin.runtime.distributed.DistributedPluginRegistry.PluginRegistryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Synchronizes plugins across cluster instances.
 *
 * <p>This component ensures plugin consistency across all pods in a Kubernetes deployment:</p>
 * <ul>
 *   <li>Periodically checks for missing plugins and installs them</li>
 *   <li>Listens for plugin events from other instances</li>
 *   <li>Manages plugin state synchronization</li>
 *   <li>Handles leader election for cluster-wide operations</li>
 * </ul>
 */
public class ClusterPluginSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(ClusterPluginSynchronizer.class);

    private final PluginManager pluginManager;
    private final DistributedPluginRegistry distributedRegistry;
    private final PluginBinaryStore binaryStore;
    @SuppressWarnings("unused")
    private final String instanceId;

    private volatile boolean enabled = true;

    public ClusterPluginSynchronizer(
            PluginManager pluginManager,
            DistributedPluginRegistry distributedRegistry,
            PluginBinaryStore binaryStore,
            String instanceId) {
        this.pluginManager = pluginManager;
        this.distributedRegistry = distributedRegistry;
        this.binaryStore = binaryStore;
        this.instanceId = instanceId;

        // Register for plugin events
        distributedRegistry.addEventListener(this::handlePluginEvent);
    }

    /**
     * Enables or disables synchronization.
     *
     * @param enabled true to enable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Periodically synchronizes plugins across the cluster.
     */
    @Scheduled(fixedRateString = "${arcana.plugin.sync.interval:30000}")
    public void synchronizePlugins() {
        if (!enabled) {
            return;
        }

        try {
            // Refresh leadership
            distributedRegistry.tryAcquireLeadership();

            // Get local plugins
            Set<String> localPlugins = pluginManager.getPlugins().stream()
                .map(p -> p.getDescriptor().getKey())
                .collect(Collectors.toSet());

            // Check for missing plugins
            Set<String> missing = distributedRegistry.getMissingPlugins(localPlugins);
            if (!missing.isEmpty()) {
                log.info("Found {} plugins missing from this instance: {}", missing.size(), missing);
                for (String pluginKey : missing) {
                    installMissingPlugin(pluginKey);
                }
            }

            // Sync plugin states
            synchronizePluginStates(localPlugins);

            // Cleanup stale entries if leader
            if (distributedRegistry.isLeader()) {
                cleanupStaleEntries();
            }

        } catch (Exception e) {
            log.error("Error during plugin synchronization", e);
        }
    }

    /**
     * Handles plugin events from other instances.
     *
     * @param event the plugin event
     */
    private void handlePluginEvent(PluginEvent event) {
        if (!enabled) {
            return;
        }

        log.info("Received plugin event: {} for {} from {}",
            event.getType(), event.getPluginKey(), event.getSourceInstance());

        try {
            switch (event.getType()) {
                case INSTALLED -> handlePluginInstalled(event);
                case UNINSTALLED -> handlePluginUninstalled(event);
                case ENABLED -> handlePluginEnabled(event);
                case DISABLED -> handlePluginDisabled(event);
                case UPDATED -> handlePluginUpdated(event);
            }
        } catch (Exception e) {
            log.error("Error handling plugin event: {}", event, e);
        }
    }

    private void handlePluginInstalled(PluginEvent event) {
        String pluginKey = event.getPluginKey();

        // Check if already installed locally
        if (pluginManager.isPluginInstalled(pluginKey)) {
            log.debug("Plugin {} already installed locally", pluginKey);
            return;
        }

        // Install from shared storage
        installMissingPlugin(pluginKey);
    }

    private void handlePluginUninstalled(PluginEvent event) {
        String pluginKey = event.getPluginKey();

        // Uninstall locally if installed
        if (pluginManager.isPluginInstalled(pluginKey)) {
            try {
                pluginManager.uninstallPlugin(pluginKey);
                log.info("Plugin {} uninstalled locally after cluster event", pluginKey);
            } catch (Exception e) {
                log.error("Failed to uninstall plugin {} locally", pluginKey, e);
            }
        }
    }

    private void handlePluginEnabled(PluginEvent event) {
        String pluginKey = event.getPluginKey();

        // Enable locally if installed but not enabled
        if (pluginManager.isPluginInstalled(pluginKey) && !pluginManager.isPluginEnabled(pluginKey)) {
            try {
                pluginManager.enablePlugin(pluginKey);
                log.info("Plugin {} enabled locally after cluster event", pluginKey);
            } catch (Exception e) {
                log.error("Failed to enable plugin {} locally", pluginKey, e);
            }
        }
    }

    private void handlePluginDisabled(PluginEvent event) {
        String pluginKey = event.getPluginKey();

        // Disable locally if enabled
        if (pluginManager.isPluginEnabled(pluginKey)) {
            try {
                pluginManager.disablePlugin(pluginKey);
                log.info("Plugin {} disabled locally after cluster event", pluginKey);
            } catch (Exception e) {
                log.error("Failed to disable plugin {} locally", pluginKey, e);
            }
        }
    }

    private void handlePluginUpdated(PluginEvent event) {
        String pluginKey = event.getPluginKey();

        // Check if update is needed
        PluginRegistryEntry clusterEntry = distributedRegistry.getPlugin(pluginKey);
        if (clusterEntry == null) {
            return;
        }

        // Get local version
        pluginManager.getPlugin(pluginKey).ifPresent(localPlugin -> {
            String localVersion = localPlugin.getDescriptor().getVersion();
            String clusterVersion = clusterEntry.getVersion();

            if (!localVersion.equals(clusterVersion)) {
                log.info("Plugin {} version mismatch (local: {}, cluster: {}), updating...",
                    pluginKey, localVersion, clusterVersion);
                installMissingPlugin(pluginKey);
            }
        });
    }

    private void installMissingPlugin(String pluginKey) {
        try {
            // Download plugin binary from shared storage
            Path pluginPath = binaryStore.downloadPlugin(pluginKey);
            if (pluginPath == null) {
                log.warn("Plugin {} binary not found in shared storage", pluginKey);
                return;
            }

            // Install locally
            pluginManager.installPlugin(pluginPath);

            // Get expected state from cluster
            PluginRegistryEntry entry = distributedRegistry.getPlugin(pluginKey);
            if (entry != null && entry.getState() == PluginState.ACTIVE) {
                pluginManager.enablePlugin(pluginKey);
            }

            log.info("Plugin {} synchronized from cluster", pluginKey);

        } catch (Exception e) {
            log.error("Failed to install missing plugin: {}", pluginKey, e);
        }
    }

    private void synchronizePluginStates(Set<String> localPlugins) {
        Map<String, PluginRegistryEntry> clusterPlugins = distributedRegistry.getAllPlugins();

        for (String pluginKey : localPlugins) {
            PluginRegistryEntry clusterEntry = clusterPlugins.get(pluginKey);
            if (clusterEntry == null) {
                // Plugin installed locally but not in cluster - register it
                pluginManager.getPlugin(pluginKey).ifPresent(plugin -> {
                    distributedRegistry.registerPlugin(
                        pluginKey,
                        plugin.getDescriptor(),
                        pluginManager.getPluginState(pluginKey)
                    );
                });
                continue;
            }

            // Sync state
            PluginState localState = pluginManager.getPluginState(pluginKey);
            PluginState clusterState = clusterEntry.getState();

            if (localState != clusterState) {
                log.debug("Plugin {} state mismatch (local: {}, cluster: {})",
                    pluginKey, localState, clusterState);

                // Follow cluster state
                try {
                    if (clusterState == PluginState.ACTIVE && localState != PluginState.ACTIVE) {
                        pluginManager.enablePlugin(pluginKey);
                    } else if (clusterState != PluginState.ACTIVE && localState == PluginState.ACTIVE) {
                        pluginManager.disablePlugin(pluginKey);
                    }
                } catch (Exception e) {
                    log.error("Failed to sync plugin {} state", pluginKey, e);
                }
            }
        }
    }

    private void cleanupStaleEntries() {
        // This runs only on the leader instance
        // Remove entries for plugins that no longer exist in any instance
        log.debug("Running stale entry cleanup as leader");
        // Implementation would check instance heartbeats and remove orphaned entries
    }
}
