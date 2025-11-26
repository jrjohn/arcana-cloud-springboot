package com.arcana.cloud.plugin.api;

import com.arcana.cloud.plugin.lifecycle.PluginState;

/**
 * Core interface for Arcana Cloud plugins.
 *
 * <p>Plugins implement this interface to integrate with the Arcana Cloud platform.
 * The platform manages plugin lifecycle through OSGi bundle states and provides
 * access to platform services via {@link PluginContext}.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * public class MyPlugin implements Plugin {
 *     private PluginContext context;
 *
 *     @Override
 *     public void onInstall(PluginContext context) {
 *         this.context = context;
 *         // Initialize plugin resources
 *     }
 *
 *     @Override
 *     public void onEnable() {
 *         // Start plugin services
 *     }
 *
 *     @Override
 *     public void onDisable() {
 *         // Stop plugin services
 *     }
 * }
 * }</pre>
 *
 * @see PluginContext
 * @see PluginDescriptor
 */
public interface Plugin {

    /**
     * Called when the plugin is first installed.
     * Use this to initialize plugin resources and store the context reference.
     *
     * @param context the plugin context providing access to platform services
     */
    void onInstall(PluginContext context);

    /**
     * Called when the plugin is enabled (started).
     * Use this to start plugin services, register extensions, and begin processing.
     */
    void onEnable();

    /**
     * Called when the plugin is disabled (stopped).
     * Use this to stop services, unregister extensions, and release resources.
     */
    void onDisable();

    /**
     * Called when the plugin is being uninstalled.
     * Use this to perform final cleanup, such as removing persistent data if needed.
     */
    default void onUninstall() {
        // Default implementation does nothing
    }

    /**
     * Called when the plugin is being upgraded from a previous version.
     *
     * @param previousVersion the version being upgraded from
     */
    default void onUpgrade(String previousVersion) {
        // Default implementation does nothing
    }

    /**
     * Returns the plugin descriptor containing metadata about this plugin.
     *
     * @return the plugin descriptor
     */
    PluginDescriptor getDescriptor();

    /**
     * Returns the current state of this plugin.
     *
     * @return the plugin state
     */
    PluginState getState();
}
