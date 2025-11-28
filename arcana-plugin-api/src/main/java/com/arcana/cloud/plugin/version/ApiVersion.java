package com.arcana.cloud.plugin.version;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an API element with its API version metadata.
 *
 * <p>This annotation can be applied to packages, classes, or methods to indicate
 * the current API version and stability level. It helps plugin developers
 * understand which parts of the API are stable for production use.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @ApiVersion(
 *     version = "1.0.0",
 *     stability = ApiVersion.Stability.STABLE
 * )
 * public interface Plugin {
 *     // Stable API
 * }
 *
 * @ApiVersion(
 *     version = "1.0.0",
 *     stability = ApiVersion.Stability.EXPERIMENTAL
 * )
 * public interface SSRViewExtension {
 *     // Experimental API - may change
 * }
 * }</pre>
 *
 * @see Since
 * @see DeprecatedSince
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE, ElementType.METHOD})
public @interface ApiVersion {

    /**
     * The current API version.
     *
     * @return the version string (e.g., "1.0.0")
     */
    String version();

    /**
     * The stability level of this API.
     *
     * @return the stability level
     */
    Stability stability() default Stability.STABLE;

    /**
     * API stability levels.
     */
    enum Stability {
        /**
         * API is stable and backwards compatible.
         * Breaking changes only occur in major versions.
         */
        STABLE,

        /**
         * API is mostly stable but may have minor changes in minor versions.
         * Suitable for production but monitor release notes.
         */
        EVOLVING,

        /**
         * API is experimental and may change significantly.
         * Not recommended for production use.
         */
        EXPERIMENTAL,

        /**
         * API is internal and should not be used by plugins.
         * May change or be removed without notice.
         */
        INTERNAL,

        /**
         * API is deprecated and will be removed in a future version.
         */
        DEPRECATED
    }
}
