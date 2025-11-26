package com.arcana.cloud.plugin.api;

import java.util.List;
import java.util.Optional;

/**
 * Provides access to platform services and other plugins.
 *
 * <p>The PluginAccessor is the main entry point for plugins to interact
 * with the Arcana Cloud platform. It provides access to:</p>
 * <ul>
 *   <li>Other installed plugins</li>
 *   <li>Extension points and their implementations</li>
 *   <li>Platform-wide services</li>
 * </ul>
 */
public interface PluginAccessor {

    /**
     * Returns all installed plugins.
     *
     * @return an unmodifiable list of all installed plugins
     */
    List<Plugin> getPlugins();

    /**
     * Returns all enabled plugins.
     *
     * @return an unmodifiable list of enabled plugins
     */
    List<Plugin> getEnabledPlugins();

    /**
     * Finds a plugin by its key.
     *
     * @param pluginKey the plugin key
     * @return an optional containing the plugin if found
     */
    Optional<Plugin> getPlugin(String pluginKey);

    /**
     * Checks if a plugin is installed.
     *
     * @param pluginKey the plugin key
     * @return true if the plugin is installed
     */
    boolean isPluginInstalled(String pluginKey);

    /**
     * Checks if a plugin is enabled.
     *
     * @param pluginKey the plugin key
     * @return true if the plugin is installed and enabled
     */
    boolean isPluginEnabled(String pluginKey);

    /**
     * Returns all extensions of a specific type across all enabled plugins.
     *
     * @param extensionType the extension interface class
     * @param <T> the extension type
     * @return a list of extension implementations
     */
    <T> List<T> getExtensions(Class<T> extensionType);

    /**
     * Returns extensions of a specific type from a specific plugin.
     *
     * @param extensionType the extension interface class
     * @param pluginKey the plugin key
     * @param <T> the extension type
     * @return a list of extension implementations from the specified plugin
     */
    <T> List<T> getExtensions(Class<T> extensionType, String pluginKey);

    /**
     * Returns the platform version.
     *
     * @return the platform version string
     */
    String getPlatformVersion();

    /**
     * Returns the plugins directory path.
     *
     * @return the absolute path to the plugins directory
     */
    String getPluginsDirectory();
}
