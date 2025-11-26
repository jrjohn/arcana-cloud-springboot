package com.arcana.cloud.ssr;

import com.arcana.cloud.ssr.cache.SSRCache;
import com.arcana.cloud.ssr.context.SSRContext;
import com.arcana.cloud.ssr.context.SSRRequestContext;
import com.arcana.cloud.ssr.renderer.AngularSSRRenderer;
import com.arcana.cloud.ssr.renderer.ReactSSRRenderer;
import com.arcana.cloud.ssr.renderer.SSRRenderer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-Side Rendering Engine for Arcana Cloud.
 *
 * <p>Provides SSR capabilities for both React (Next.js) and Angular Universal
 * applications. Uses GraalJS for JavaScript execution when direct Node.js
 * integration is not available.</p>
 */
@Component
public class SSREngine {

    private static final Logger log = LoggerFactory.getLogger(SSREngine.class);

    private final SSRConfiguration config;
    private final SSRCache cache;
    private final Map<SSRFramework, SSRRenderer> renderers;

    private ReactSSRRenderer reactRenderer;
    private AngularSSRRenderer angularRenderer;

    public SSREngine(SSRConfiguration config, SSRCache cache) {
        this.config = config;
        this.cache = cache;
        this.renderers = new ConcurrentHashMap<>();
    }

    @PostConstruct
    public void initialize() {
        log.info("Initializing SSR Engine");

        if (config.isReactEnabled()) {
            reactRenderer = new ReactSSRRenderer(config);
            renderers.put(SSRFramework.REACT, reactRenderer);
            log.info("React SSR renderer initialized");
        }

        if (config.isAngularEnabled()) {
            angularRenderer = new AngularSSRRenderer(config);
            renderers.put(SSRFramework.ANGULAR, angularRenderer);
            log.info("Angular SSR renderer initialized");
        }

        log.info("SSR Engine initialized with {} renderer(s)", renderers.size());
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down SSR Engine");

        for (SSRRenderer renderer : renderers.values()) {
            try {
                renderer.close();
            } catch (Exception e) {
                log.error("Error closing renderer", e);
            }
        }

        renderers.clear();
        log.info("SSR Engine shutdown complete");
    }

    /**
     * Renders a page using server-side rendering.
     *
     * @param framework the SSR framework to use
     * @param componentPath path to the component to render
     * @param context the SSR context with request data
     * @return the rendered HTML
     */
    public String render(SSRFramework framework, String componentPath, SSRContext context) {
        SSRRenderer renderer = renderers.get(framework);
        if (renderer == null) {
            throw new IllegalArgumentException("SSR framework not available: " + framework);
        }

        // Check cache first
        if (config.isCacheEnabled()) {
            String cacheKey = buildCacheKey(framework, componentPath, context);
            Optional<String> cached = cache.get(cacheKey);
            if (cached.isPresent()) {
                log.debug("SSR cache hit: {}", componentPath);
                return cached.get();
            }
        }

        // Render the component
        String html = renderer.render(componentPath, context);

        // Cache the result
        if (config.isCacheEnabled() && context.getCacheDuration() > 0) {
            String cacheKey = buildCacheKey(framework, componentPath, context);
            cache.put(cacheKey, html, context.getCacheDuration());
        }

        return html;
    }

    /**
     * Renders a page with initial props.
     *
     * @param framework the SSR framework
     * @param componentPath path to the component
     * @param props initial props for the component
     * @param request the HTTP request context
     * @return rendered HTML with hydration script
     */
    public String renderWithProps(SSRFramework framework, String componentPath,
                                   Map<String, Object> props, SSRRequestContext request) {
        SSRContext context = SSRContext.builder()
            .path(request.getPath())
            .queryParams(request.getQueryParams())
            .headers(request.getHeaders())
            .props(props)
            .currentUserId(request.getCurrentUserId())
            .locale(request.getLocale())
            .build();

        String html = render(framework, componentPath, context);

        // Add hydration script for client-side
        return addHydrationScript(html, props);
    }

    /**
     * Renders a plugin SSR view.
     *
     * @param pluginKey the plugin key
     * @param viewKey the view key
     * @param framework the SSR framework
     * @param props initial props
     * @param request the HTTP request context
     * @return rendered HTML
     */
    public String renderPluginView(String pluginKey, String viewKey,
                                    SSRFramework framework, Map<String, Object> props,
                                    SSRRequestContext request) {
        String componentPath = String.format("plugins/%s/%s", pluginKey, viewKey);
        return renderWithProps(framework, componentPath, props, request);
    }

    /**
     * Checks if a framework is available.
     *
     * @param framework the framework
     * @return true if available
     */
    public boolean isFrameworkAvailable(SSRFramework framework) {
        return renderers.containsKey(framework);
    }

    /**
     * Returns the React renderer.
     *
     * @return the React renderer or null if not enabled
     */
    public ReactSSRRenderer getReactRenderer() {
        return reactRenderer;
    }

    /**
     * Returns the Angular renderer.
     *
     * @return the Angular renderer or null if not enabled
     */
    public AngularSSRRenderer getAngularRenderer() {
        return angularRenderer;
    }

    /**
     * Clears the SSR cache.
     */
    public void clearCache() {
        cache.clear();
        log.info("SSR cache cleared");
    }

    /**
     * Clears cache entries for a specific path pattern.
     *
     * @param pathPattern the path pattern
     */
    public void clearCache(String pathPattern) {
        cache.clearByPattern(pathPattern);
        log.info("SSR cache cleared for pattern: {}", pathPattern);
    }

    private String buildCacheKey(SSRFramework framework, String componentPath, SSRContext context) {
        return String.format("%s:%s:%s:%d",
            framework.name(),
            componentPath,
            context.getPath(),
            context.hashCode()
        );
    }

    private String addHydrationScript(String html, Map<String, Object> props) {
        // Serialize props for client hydration
        String serializedProps = serializeProps(props);

        String hydrationScript = String.format("""
            <script id="__ARCANA_SSR_DATA__" type="application/json">%s</script>
            <script>
                window.__ARCANA_SSR_DATA__ = JSON.parse(
                    document.getElementById('__ARCANA_SSR_DATA__').textContent
                );
            </script>
            """, serializedProps);

        // Insert before </body>
        return html.replace("</body>", hydrationScript + "</body>");
    }

    private String serializeProps(Map<String, Object> props) {
        try {
            return new tools.jackson.databind.json.JsonMapper()
                .writeValueAsString(props);
        } catch (Exception e) {
            log.error("Failed to serialize SSR props", e);
            return "{}";
        }
    }

    /**
     * Supported SSR frameworks.
     */
    public enum SSRFramework {
        REACT,
        ANGULAR
    }
}
