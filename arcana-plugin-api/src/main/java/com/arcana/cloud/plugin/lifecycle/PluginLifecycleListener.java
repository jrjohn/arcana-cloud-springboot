package com.arcana.cloud.plugin.lifecycle;

import com.arcana.cloud.plugin.api.Plugin;

/**
 * Listener interface for plugin lifecycle events.
 *
 * <p>Implementations can register with the plugin framework to receive
 * notifications about plugin state changes. This is useful for:</p>
 * <ul>
 *   <li>Logging and auditing plugin activity</li>
 *   <li>Triggering dependent actions when plugins change state</li>
 *   <li>Building plugin management UIs</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * public class MyLifecycleListener implements PluginLifecycleListener {
 *     @Override
 *     public void onPluginEnabled(Plugin plugin) {
 *         log.info("Plugin enabled: " + plugin.getDescriptor().getName());
 *     }
 * }
 * }</pre>
 */
public interface PluginLifecycleListener {

    /**
     * Called when a plugin is installed.
     *
     * @param plugin the installed plugin
     */
    default void onPluginInstalled(Plugin plugin) {
    }

    /**
     * Called when a plugin is enabled (started).
     *
     * @param plugin the enabled plugin
     */
    default void onPluginEnabled(Plugin plugin) {
    }

    /**
     * Called when a plugin is disabled (stopped).
     *
     * @param plugin the disabled plugin
     */
    default void onPluginDisabled(Plugin plugin) {
    }

    /**
     * Called when a plugin is uninstalled.
     *
     * @param plugin the uninstalled plugin
     */
    default void onPluginUninstalled(Plugin plugin) {
    }

    /**
     * Called when a plugin is upgraded.
     *
     * @param plugin the upgraded plugin
     * @param previousVersion the previous version
     */
    default void onPluginUpgraded(Plugin plugin, String previousVersion) {
    }

    /**
     * Called when a plugin fails to start.
     *
     * @param plugin the plugin that failed
     * @param cause the failure cause
     */
    default void onPluginStartFailed(Plugin plugin, Throwable cause) {
    }

    /**
     * Called when plugin state changes.
     *
     * @param plugin the plugin
     * @param oldState the previous state
     * @param newState the new state
     */
    default void onPluginStateChanged(Plugin plugin, PluginState oldState, PluginState newState) {
    }
}
