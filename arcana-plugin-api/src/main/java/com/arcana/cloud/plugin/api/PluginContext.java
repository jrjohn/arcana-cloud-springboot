package com.arcana.cloud.plugin.api;

import org.osgi.framework.BundleContext;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Provides plugins with access to platform services and resources.
 *
 * <p>The PluginContext is passed to plugins during installation and provides:</p>
 * <ul>
 *   <li>Access to platform services (database, cache, security, etc.)</li>
 *   <li>Plugin configuration and properties</li>
 *   <li>Resource loading from the plugin bundle</li>
 *   <li>Event publishing capabilities</li>
 *   <li>Logging facilities</li>
 * </ul>
 *
 * @see Plugin
 */
public interface PluginContext {

    /**
     * Returns the plugin descriptor for this context.
     *
     * @return the plugin descriptor
     */
    PluginDescriptor getPluginDescriptor();

    /**
     * Returns the OSGi bundle context for advanced OSGi operations.
     *
     * @return the OSGi bundle context
     */
    BundleContext getBundleContext();

    /**
     * Retrieves a platform service by its type.
     *
     * @param serviceClass the service interface class
     * @param <T> the service type
     * @return an optional containing the service if available
     */
    <T> Optional<T> getService(Class<T> serviceClass);

    /**
     * Retrieves a platform service by its type, throwing if not available.
     *
     * @param serviceClass the service interface class
     * @param <T> the service type
     * @return the service instance
     * @throws IllegalStateException if the service is not available
     */
    <T> T requireService(Class<T> serviceClass);

    /**
     * Returns the plugin's data directory for storing persistent files.
     *
     * @return the path to the plugin's data directory
     */
    Path getDataDirectory();

    /**
     * Returns a configuration property value.
     *
     * @param key the property key
     * @return an optional containing the value if present
     */
    Optional<String> getProperty(String key);

    /**
     * Returns a configuration property value with a default.
     *
     * @param key the property key
     * @param defaultValue the default value if not present
     * @return the property value or the default
     */
    String getProperty(String key, String defaultValue);

    /**
     * Returns all configuration properties for this plugin.
     *
     * @return an unmodifiable map of all properties
     */
    Map<String, String> getProperties();

    /**
     * Loads a resource from the plugin bundle.
     *
     * @param path the resource path relative to the bundle root
     * @return an optional containing the input stream if found
     */
    Optional<InputStream> getResource(String path);

    /**
     * Publishes an event to the platform event bus.
     *
     * @param event the event to publish
     */
    void publishEvent(Object event);

    /**
     * Returns the platform accessor for accessing core platform functionality.
     *
     * @return the plugin accessor
     */
    PluginAccessor getPluginAccessor();

    /**
     * Logs a message at INFO level.
     *
     * @param message the message to log
     */
    void log(String message);

    /**
     * Logs a message at INFO level with arguments.
     *
     * @param message the message format
     * @param args the message arguments
     */
    void log(String message, Object... args);

    /**
     * Logs a warning message.
     *
     * @param message the message to log
     */
    void logWarning(String message);

    /**
     * Logs an error message with an exception.
     *
     * @param message the message to log
     * @param throwable the exception
     */
    void logError(String message, Throwable throwable);
}
