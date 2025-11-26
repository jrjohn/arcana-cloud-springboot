package com.arcana.cloud.plugin.extension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Extension point for server-side rendered views.
 *
 * <p>SSR views allow plugins to contribute complex UI pages that are
 * rendered on the server using React (Next.js) or Angular Universal.
 * This provides better SEO, faster initial page loads, and improved
 * accessibility.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @SSRViewExtension(
 *     key = "audit-dashboard",
 *     path = "/plugins/audit/dashboard",
 *     framework = SSRFramework.REACT,
 *     entry = "AuditDashboard.tsx"
 * )
 * public class AuditDashboardView implements SSRView {
 *
 *     @Override
 *     public Map<String, Object> getInitialProps(SSRContext context) {
 *         return Map.of(
 *             "auditLogs", auditService.getRecentLogs(100),
 *             "stats", auditService.getStatistics()
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p>In arcana-plugin.xml:</p>
 * <pre>{@code
 * <ssr-view key="audit-dashboard"
 *           path="/plugins/audit/dashboard"
 *           framework="react"
 *           entry="AuditDashboard.tsx">
 *     <requires-permission>ADMIN</requires-permission>
 *     <title>Audit Dashboard</title>
 * </ssr-view>
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtensionPoint(type = "ssr-view", description = "Server-side rendered view")
public @interface SSRViewExtension {

    /**
     * Unique key for this extension within the plugin.
     *
     * @return the extension key
     */
    String key();

    /**
     * The URL path for this view.
     * Should start with /plugins/
     *
     * @return the path
     */
    String path();

    /**
     * The SSR framework to use.
     *
     * @return the framework
     */
    SSRFramework framework() default SSRFramework.REACT;

    /**
     * The entry point file for the view (relative to plugin's webapp folder).
     *
     * @return the entry point
     */
    String entry();

    /**
     * The page title.
     *
     * @return the title
     */
    String title() default "";

    /**
     * Description of this view.
     *
     * @return the description
     */
    String description() default "";

    /**
     * Required permission to access this view.
     *
     * @return the required permission
     */
    String requiresPermission() default "";

    /**
     * Cache duration in seconds. 0 means no caching.
     *
     * @return the cache duration
     */
    int cacheDuration() default 0;

    /**
     * Supported SSR frameworks.
     */
    enum SSRFramework {
        /**
         * React with Next.js
         */
        REACT,

        /**
         * Angular with Universal
         */
        ANGULAR
    }
}

/**
 * Interface for SSR view data providers.
 */
interface SSRView {

    /**
     * Returns initial props for server-side rendering.
     *
     * @param context the SSR context with request information
     * @return a map of props to pass to the view component
     */
    java.util.Map<String, Object> getInitialProps(SSRContext context);

    /**
     * SSR rendering context.
     */
    interface SSRContext {
        /**
         * Returns the request path.
         * @return the path
         */
        String getPath();

        /**
         * Returns query parameters.
         * @return the parameters
         */
        java.util.Map<String, String> getQueryParams();

        /**
         * Returns the authenticated user ID, if any.
         * @return the user ID or null
         */
        Long getCurrentUserId();

        /**
         * Returns a request header value.
         * @param name the header name
         * @return the header value or null
         */
        String getHeader(String name);
    }
}
