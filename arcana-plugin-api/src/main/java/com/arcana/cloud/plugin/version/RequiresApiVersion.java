package com.arcana.cloud.plugin.version;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the minimum Plugin API version required by a plugin.
 *
 * <p>Plugins should use this annotation on their main plugin class to declare
 * their API requirements. The platform will verify compatibility before loading.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @RequiresApiVersion(
 *     minimum = "1.0.0",
 *     tested = "1.2.0"
 * )
 * public class MyPlugin implements Plugin {
 *     // Plugin implementation
 * }
 * }</pre>
 *
 * @see PluginApiVersion
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RequiresApiVersion {

    /**
     * The minimum API version required to run this plugin.
     * The plugin will not load if the platform version is lower.
     *
     * @return minimum required version (e.g., "1.0.0")
     */
    String minimum();

    /**
     * The API version against which this plugin was tested.
     * This is informational and helps with troubleshooting.
     *
     * @return tested version (e.g., "1.2.0")
     */
    String tested() default "";

    /**
     * Optional maximum API version supported.
     * An empty string indicates no maximum (plugin works with any higher version).
     *
     * @return maximum supported version or empty string
     */
    String maximum() default "";
}
