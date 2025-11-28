package com.arcana.cloud.plugin.version;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that an API element has been deprecated as of a specific version.
 *
 * <p>This annotation provides more context than {@link Deprecated} by specifying
 * when deprecation occurred and when removal is planned.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @DeprecatedSince(
 *     version = "1.5.0",
 *     replacement = "newMethod()",
 *     removalVersion = "2.0.0"
 * )
 * @Deprecated
 * void oldMethod();
 * }</pre>
 *
 * @see Since
 * @see java.lang.Deprecated
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface DeprecatedSince {

    /**
     * The version in which this element was deprecated.
     *
     * @return the deprecation version (e.g., "1.5.0")
     */
    String version();

    /**
     * The recommended replacement for this deprecated element.
     *
     * @return the replacement element name or description
     */
    String replacement() default "";

    /**
     * The planned version for removal of this element.
     * An empty string indicates no planned removal date.
     *
     * @return the removal version (e.g., "2.0.0") or empty string
     */
    String removalVersion() default "";

    /**
     * Additional information about why this element was deprecated.
     *
     * @return deprecation reason
     */
    String reason() default "";
}
