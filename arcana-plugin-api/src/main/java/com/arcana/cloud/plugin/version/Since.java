package com.arcana.cloud.plugin.version;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates the API version in which an element was first introduced.
 *
 * <p>This annotation helps plugin developers understand API compatibility
 * and plan for minimum required platform versions.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @Since("1.0.0")
 * public interface Plugin {
 *     // ...
 * }
 *
 * @Since("1.2.0")
 * default void onUpgrade(String previousVersion) {
 *     // Added in version 1.2.0
 * }
 * }</pre>
 *
 * @see Deprecated
 * @see ApiVersion
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.PACKAGE})
public @interface Since {

    /**
     * The version in which this element was first introduced.
     * Format should follow semantic versioning: MAJOR.MINOR.PATCH
     *
     * @return the version string (e.g., "1.0.0", "1.2.0")
     */
    String value();

    /**
     * Optional description of what was added or changed.
     *
     * @return description of the addition
     */
    String description() default "";
}
