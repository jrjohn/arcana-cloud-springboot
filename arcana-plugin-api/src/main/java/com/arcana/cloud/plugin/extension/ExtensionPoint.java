package com.arcana.cloud.plugin.extension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for extension point interfaces.
 *
 * <p>Extension points define contracts that plugins can implement to extend
 * platform functionality. The platform discovers and manages extensions
 * automatically based on OSGi service registrations.</p>
 *
 * <p>Example extension point definition:</p>
 * <pre>{@code
 * @ExtensionPoint(type = "authentication-provider")
 * public interface AuthenticationProviderExtension {
 *     Authentication authenticate(AuthenticationRequest request);
 * }
 * }</pre>
 *
 * @see RestEndpointExtension
 * @see ServiceExtension
 * @see EventListenerExtension
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExtensionPoint {

    /**
     * The extension point type identifier.
     * Used in arcana-plugin.xml to declare extensions.
     *
     * @return the extension type
     */
    String type();

    /**
     * Description of the extension point.
     *
     * @return the description
     */
    String description() default "";

    /**
     * Whether multiple extensions of this type are allowed per plugin.
     *
     * @return true if multiple extensions are allowed
     */
    boolean multiple() default true;
}
