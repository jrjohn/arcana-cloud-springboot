package com.arcana.cloud.plugin.extension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Extension point for adding web UI fragments.
 *
 * <p>Web fragments are reusable UI components that can be injected into
 * specific locations within the platform's web interface. They're useful
 * for adding dashboard widgets, sidebar panels, or custom UI sections.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @WebFragmentExtension(
 *     key = "audit-summary-widget",
 *     location = "dashboard.widgets",
 *     weight = 100
 * )
 * public class AuditSummaryWidget implements WebFragment {
 *
 *     @Override
 *     public String render(RenderContext context) {
 *         int todayCount = auditService.getTodayCount();
 *         return "<div class='widget'><h3>Audit Summary</h3><p>" +
 *                todayCount + " events today</p></div>";
 *     }
 * }
 * }</pre>
 *
 * <p>In arcana-plugin.xml:</p>
 * <pre>{@code
 * <web-fragment key="audit-summary-widget"
 *               location="dashboard.widgets"
 *               class="com.example.audit.AuditSummaryWidget">
 *     <weight>100</weight>
 *     <requires-permission>USER</requires-permission>
 * </web-fragment>
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtensionPoint(type = "web-fragment", description = "Web UI fragment extension")
public @interface WebFragmentExtension {

    /**
     * Unique key for this extension within the plugin.
     *
     * @return the extension key
     */
    String key();

    /**
     * The location where this fragment should be rendered.
     * Standard locations include:
     * - dashboard.widgets
     * - sidebar.top
     * - sidebar.bottom
     * - header.right
     * - footer
     *
     * @return the location
     */
    String location();

    /**
     * Weight for ordering fragments in the same location.
     * Lower weights appear first.
     *
     * @return the weight
     */
    int weight() default 100;

    /**
     * Description of this fragment.
     *
     * @return the description
     */
    String description() default "";

    /**
     * Required permission to view this fragment.
     *
     * @return the required permission
     */
    String requiresPermission() default "";

    /**
     * CSS resources required by this fragment.
     *
     * @return array of CSS resource paths
     */
    String[] css() default {};

    /**
     * JavaScript resources required by this fragment.
     *
     * @return array of JS resource paths
     */
    String[] js() default {};
}

/**
 * Interface for web fragment implementations.
 */
interface WebFragment {

    /**
     * Renders the fragment HTML.
     *
     * @param context the render context
     * @return the rendered HTML
     */
    String render(RenderContext context);

    /**
     * Render context providing request information.
     */
    interface RenderContext {
        /**
         * Returns the current user ID.
         * @return the user ID or null if anonymous
         */
        Long getCurrentUserId();

        /**
         * Returns a request parameter.
         * @param name the parameter name
         * @return the parameter value or null
         */
        String getParameter(String name);

        /**
         * Returns the current locale.
         * @return the locale
         */
        java.util.Locale getLocale();

        /**
         * Returns the context path.
         * @return the context path
         */
        String getContextPath();
    }
}
