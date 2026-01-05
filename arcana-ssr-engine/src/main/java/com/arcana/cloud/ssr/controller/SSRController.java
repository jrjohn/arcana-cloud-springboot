package com.arcana.cloud.ssr.controller;

import com.arcana.cloud.ssr.SSREngine;
import com.arcana.cloud.ssr.SSREngine.SSRFramework;
import com.arcana.cloud.ssr.cache.SSRCache;
import com.arcana.cloud.ssr.context.SSRRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for SSR rendering endpoints.
 *
 * <p>Provides endpoints for server-side rendering of React and Angular
 * components, as well as cache management.</p>
 */
@RestController
@RequestMapping("/api/v1/ssr")
public class SSRController {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(SSRController.class);

    private final SSREngine ssrEngine;
    private final SSRCache ssrCache;

    public SSRController(SSREngine ssrEngine, SSRCache ssrCache) {
        this.ssrEngine = ssrEngine;
        this.ssrCache = ssrCache;
    }

    /**
     * Renders a React component.
     *
     * @param componentPath path to the component
     * @param props initial props
     * @param request the HTTP request
     * @return rendered HTML
     */
    @PostMapping(value = "/react/{*componentPath}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> renderReact(
            @PathVariable String componentPath,
            @RequestBody(required = false) Map<String, Object> props,
            HttpServletRequest request) {

        if (!ssrEngine.isFrameworkAvailable(SSRFramework.REACT)) {
            return ResponseEntity.status(503)
                .body("<html><body><h1>React SSR not available</h1></body></html>");
        }

        Long userId = getUserIdFromRequest(request);
        SSRRequestContext context = SSRRequestContext.fromHttpRequest(request, userId);

        String html = ssrEngine.renderWithProps(
            SSRFramework.REACT,
            componentPath,
            props != null ? props : Map.of(),
            context
        );

        return ResponseEntity.ok(html);
    }

    /**
     * Renders an Angular component.
     *
     * @param componentPath path to the component
     * @param props initial props
     * @param request the HTTP request
     * @return rendered HTML
     */
    @PostMapping(value = "/angular/{*componentPath}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> renderAngular(
            @PathVariable String componentPath,
            @RequestBody(required = false) Map<String, Object> props,
            HttpServletRequest request) {

        if (!ssrEngine.isFrameworkAvailable(SSRFramework.ANGULAR)) {
            return ResponseEntity.status(503)
                .body("<html><body><h1>Angular SSR not available</h1></body></html>");
        }

        Long userId = getUserIdFromRequest(request);
        SSRRequestContext context = SSRRequestContext.fromHttpRequest(request, userId);

        String html = ssrEngine.renderWithProps(
            SSRFramework.ANGULAR,
            componentPath,
            props != null ? props : Map.of(),
            context
        );

        return ResponseEntity.ok(html);
    }

    /**
     * Renders a plugin SSR view.
     *
     * @param pluginKey the plugin key
     * @param viewKey the view key
     * @param framework the framework (react or angular)
     * @param props initial props
     * @param request the HTTP request
     * @return rendered HTML
     */
    @PostMapping(value = "/plugin/{pluginKey}/{viewKey}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> renderPluginView(
            @PathVariable String pluginKey,
            @PathVariable String viewKey,
            @RequestParam(defaultValue = "react") String framework,
            @RequestBody(required = false) Map<String, Object> props,
            HttpServletRequest request) {

        SSRFramework ssrFramework = "angular".equalsIgnoreCase(framework)
            ? SSRFramework.ANGULAR
            : SSRFramework.REACT;

        if (!ssrEngine.isFrameworkAvailable(ssrFramework)) {
            return ResponseEntity.status(503)
                .body("<html><body><h1>" + framework + " SSR not available</h1></body></html>");
        }

        Long userId = getUserIdFromRequest(request);
        SSRRequestContext context = SSRRequestContext.fromHttpRequest(request, userId);

        String html = ssrEngine.renderPluginView(
            pluginKey,
            viewKey,
            ssrFramework,
            props != null ? props : Map.of(),
            context
        );

        return ResponseEntity.ok(html);
    }

    /**
     * Gets SSR cache statistics.
     *
     * @return cache statistics
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<SSRCache.CacheStatistics> getCacheStats() {
        return ResponseEntity.ok(ssrCache.getStatistics());
    }

    /**
     * Clears the SSR cache.
     *
     * @param pattern optional pattern to match
     * @return success message
     */
    @DeleteMapping("/cache")
    public ResponseEntity<Map<String, String>> clearCache(
            @RequestParam(required = false) String pattern) {

        if (pattern != null && !pattern.isEmpty()) {
            ssrEngine.clearCache(pattern);
            return ResponseEntity.ok(Map.of(
                "message", "Cache cleared for pattern: " + pattern
            ));
        } else {
            ssrEngine.clearCache();
            return ResponseEntity.ok(Map.of(
                "message", "Cache cleared"
            ));
        }
    }

    /**
     * Gets SSR engine status.
     *
     * @return engine status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
            "reactAvailable", ssrEngine.isFrameworkAvailable(SSRFramework.REACT),
            "angularAvailable", ssrEngine.isFrameworkAvailable(SSRFramework.ANGULAR),
            "reactReady", ssrEngine.getReactRenderer() != null && ssrEngine.getReactRenderer().isReady(),
            "angularReady", ssrEngine.getAngularRenderer() != null && ssrEngine.getAngularRenderer().isReady(),
            "cacheStats", ssrCache.getStatistics()
        ));
    }

    private Long getUserIdFromRequest(HttpServletRequest request) {
        // Extract user ID from authentication context
        // This would typically come from Spring Security
        Object principal = request.getAttribute("userId");
        if (principal instanceof Long) {
            return (Long) principal;
        }
        return null;
    }
}
