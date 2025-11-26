package com.arcana.cloud.plugin.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Describes a plugin's metadata and configuration.
 *
 * <p>Plugin descriptors are typically loaded from arcana-plugin.xml files
 * within plugin bundles. They provide essential metadata for the plugin
 * framework to manage plugin lifecycle and dependencies.</p>
 *
 * <p>Example arcana-plugin.xml:</p>
 * <pre>{@code
 * <arcana-plugin key="com.example.my-plugin"
 *                name="My Plugin"
 *                version="1.0.0">
 *     <plugin-info>
 *         <description>A sample plugin</description>
 *         <vendor name="Example Inc" url="https://example.com"/>
 *         <min-platform-version>1.0.0</min-platform-version>
 *     </plugin-info>
 *     <!-- extensions... -->
 * </arcana-plugin>
 * }</pre>
 */
public interface PluginDescriptor {

    /**
     * Returns the unique plugin key (e.g., "com.example.my-plugin").
     *
     * @return the plugin key
     */
    String getKey();

    /**
     * Returns the human-readable plugin name.
     *
     * @return the plugin name
     */
    String getName();

    /**
     * Returns the plugin version string.
     *
     * @return the version string
     */
    String getVersion();

    /**
     * Returns the plugin description.
     *
     * @return an optional containing the description if present
     */
    Optional<String> getDescription();

    /**
     * Returns the vendor name.
     *
     * @return an optional containing the vendor name if present
     */
    Optional<String> getVendorName();

    /**
     * Returns the vendor URL.
     *
     * @return an optional containing the vendor URL if present
     */
    Optional<String> getVendorUrl();

    /**
     * Returns the minimum platform version required by this plugin.
     *
     * @return an optional containing the minimum version if specified
     */
    Optional<String> getMinPlatformVersion();

    /**
     * Returns the fully qualified class name of the main plugin class.
     *
     * @return an optional containing the plugin class name if specified
     */
    Optional<String> getPluginClassName();

    /**
     * Returns the list of extension descriptors defined by this plugin.
     *
     * @return an unmodifiable list of extension descriptors
     */
    List<ExtensionDescriptor> getExtensions();

    /**
     * Returns extension descriptors of a specific type.
     *
     * @param extensionType the extension type (e.g., "rest-extension", "event-listener")
     * @return a list of matching extension descriptors
     */
    List<ExtensionDescriptor> getExtensions(String extensionType);

    /**
     * Returns the plugin's dependencies.
     *
     * @return a list of plugin keys this plugin depends on
     */
    List<String> getDependencies();

    /**
     * Returns custom parameters defined in the plugin descriptor.
     *
     * @return an unmodifiable map of custom parameters
     */
    Map<String, String> getParameters();

    /**
     * Describes an extension point contribution.
     */
    interface ExtensionDescriptor {

        /**
         * Returns the extension type (e.g., "rest-extension", "event-listener").
         *
         * @return the extension type
         */
        String getType();

        /**
         * Returns the extension key unique within this plugin.
         *
         * @return the extension key
         */
        String getKey();

        /**
         * Returns the fully qualified implementation class name.
         *
         * @return the implementation class name
         */
        String getClassName();

        /**
         * Returns extension-specific parameters.
         *
         * @return an unmodifiable map of parameters
         */
        Map<String, String> getParameters();

        /**
         * Returns a specific parameter value.
         *
         * @param name the parameter name
         * @return an optional containing the value if present
         */
        Optional<String> getParameter(String name);
    }
}
