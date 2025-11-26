package com.arcana.cloud.ssr.renderer;

import com.arcana.cloud.ssr.context.SSRContext;

/**
 * Interface for SSR renderers.
 */
public interface SSRRenderer extends AutoCloseable {

    /**
     * Renders a component to HTML.
     *
     * @param componentPath path to the component
     * @param context the SSR context
     * @return rendered HTML string
     */
    String render(String componentPath, SSRContext context);

    /**
     * Checks if this renderer is ready.
     *
     * @return true if ready to render
     */
    boolean isReady();

    /**
     * Gets the renderer name.
     *
     * @return the name
     */
    String getName();

    /**
     * Warms up the renderer by pre-loading resources.
     */
    default void warmUp() {
        // Optional warm-up
    }
}
