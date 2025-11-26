package com.arcana.cloud.ssr.renderer;

import com.arcana.cloud.ssr.SSRConfiguration;
import com.arcana.cloud.ssr.context.SSRContext;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Angular Universal Server-Side Renderer.
 *
 * <p>Renders Angular applications using GraalJS or external Node.js process.
 * Supports Angular 17+ with standalone components and SSR.</p>
 */
public class AngularSSRRenderer implements SSRRenderer {

    private static final Logger log = LoggerFactory.getLogger(AngularSSRRenderer.class);

    private final SSRConfiguration config;
    private final GraalJSRuntime runtime;
    private final boolean useExternalNode;
    private boolean ready = false;

    public AngularSSRRenderer(SSRConfiguration config) {
        this.config = config;
        this.useExternalNode = config.isUseExternalNode();

        if (!useExternalNode) {
            this.runtime = new GraalJSRuntime(config.getGraalPoolSize(), config.getRenderTimeout());
            initialize();
        } else {
            this.runtime = null;
            log.info("Angular SSR configured to use external Node.js: {}", config.getNodeServerUrl());
        }
    }

    private void initialize() {
        try {
            Path bundlePath = config.getAngularAppPath().resolve("dist/server/main.js");
            if (Files.exists(bundlePath)) {
                runtime.setupSSREnvironment(bundlePath);
                ready = true;
                log.info("Angular SSR renderer initialized with bundle: {}", bundlePath);
            } else {
                log.warn("Angular server bundle not found: {}. SSR will use fallback.", bundlePath);
                setupFallbackRenderer();
            }
        } catch (IOException e) {
            log.error("Failed to initialize Angular SSR renderer", e);
            setupFallbackRenderer();
        }
    }

    private void setupFallbackRenderer() {
        // Set up minimal Angular SSR without bundle
        String fallbackScript = """
            var ngPlatformServer = {
                renderModule: function(module, options) {
                    return Promise.resolve(
                        '<app-root data-ssr="fallback">' +
                        '<div>Loading Angular application...</div>' +
                        '</app-root>'
                    );
                }
            };

            function renderAngularComponent(componentPath, props, context) {
                return '<app-root data-ssr="fallback">' +
                       '<div>Loading ' + componentPath + '...</div>' +
                       '</app-root>';
            }

            true;
            """;

        try {
            runtime.execute(fallbackScript);
            ready = true;
            log.info("Angular SSR fallback renderer initialized");
        } catch (Exception e) {
            log.error("Failed to initialize fallback renderer", e);
        }
    }

    @Override
    public String render(String componentPath, SSRContext context) {
        if (useExternalNode) {
            return renderViaExternalNode(componentPath, context);
        }
        return renderViaGraalJS(componentPath, context);
    }

    private String renderViaGraalJS(String componentPath, SSRContext context) {
        if (!ready) {
            return getFallbackHTML(componentPath, context);
        }

        try {
            String renderScript = buildRenderScript(componentPath, context);
            Value result = runtime.execute(renderScript);
            return result.asString();
        } catch (Exception e) {
            log.error("Angular SSR failed for: {}", componentPath, e);
            return getErrorHTML(componentPath, e);
        }
    }

    private String renderViaExternalNode(String componentPath, SSRContext context) {
        // Call external Node.js SSR server
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(config.getRenderTimeout()))
                .build();

            String requestBody = buildNodeRequestBody(componentPath, context);

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(config.getNodeServerUrl() + "/angular/render"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(java.time.Duration.ofMillis(config.getRenderTimeout()))
                .build();

            java.net.http.HttpResponse<String> response = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                log.error("External Node SSR failed with status: {}", response.statusCode());
                return getErrorHTML(componentPath, new RuntimeException("SSR failed: " + response.statusCode()));
            }
        } catch (Exception e) {
            log.error("Failed to call external Node SSR server", e);
            return getErrorHTML(componentPath, e);
        }
    }

    private String buildRenderScript(String componentPath, SSRContext context) {
        StringBuilder script = new StringBuilder();
        script.append("(function() {\n");
        script.append("  var props = ").append(serializeToJson(context.getProps())).append(";\n");
        script.append("  var contextData = {\n");
        script.append("    url: '").append(escapeJS(context.getPath())).append("',\n");
        script.append("    locale: '").append(context.getLocale().toLanguageTag()).append("',\n");
        script.append("    authenticated: ").append(context.isAuthenticated()).append(",\n");
        script.append("    queryParams: ").append(serializeToJson(context.getQueryParams())).append("\n");
        script.append("  };\n");
        script.append("  if (typeof renderAngularComponent === 'function') {\n");
        script.append("    return renderAngularComponent('").append(escapeJS(componentPath)).append("', props, contextData);\n");
        script.append("  }\n");
        script.append("  return '<app-root>Component not found: ").append(escapeJS(componentPath)).append("</app-root>';\n");
        script.append("})();\n");
        return script.toString();
    }

    private String buildNodeRequestBody(String componentPath, SSRContext context) {
        return String.format("""
            {
                "component": "%s",
                "props": %s,
                "context": {
                    "url": "%s",
                    "locale": "%s",
                    "authenticated": %s,
                    "userId": %s,
                    "queryParams": %s
                }
            }
            """,
            escapeJS(componentPath),
            serializeToJson(context.getProps()),
            escapeJS(context.getPath()),
            context.getLocale().toLanguageTag(),
            context.isAuthenticated(),
            context.getCurrentUserId() != null ? context.getCurrentUserId() : "null",
            serializeToJson(context.getQueryParams())
        );
    }

    private String getFallbackHTML(String componentPath, SSRContext context) {
        return String.format("""
            <!DOCTYPE html>
            <html lang="%s">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Arcana Cloud</title>
                <base href="/">
            </head>
            <body>
                <app-root data-ssr-fallback="true">
                    <div class="loading">
                        <div class="spinner"></div>
                        <p>Loading application...</p>
                    </div>
                </app-root>
                <script>window.__SSR_FALLBACK__ = true;</script>
            </body>
            </html>
            """, context.getLocale().toLanguageTag());
    }

    private String getErrorHTML(String componentPath, Exception e) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>SSR Error</title>
                <base href="/">
                <style>
                    .ssr-error { padding: 20px; background: #fee; border: 1px solid #fcc; margin: 20px; border-radius: 4px; }
                    .ssr-error h2 { color: #c00; margin: 0 0 10px; }
                    .ssr-error pre { background: #fff; padding: 10px; overflow: auto; border-radius: 4px; }
                </style>
            </head>
            <body>
                <app-root>
                    <div class="ssr-error">
                        <h2>Server-Side Rendering Error</h2>
                        <p>Component: %s</p>
                        <pre>%s</pre>
                    </div>
                </app-root>
                <script>window.__SSR_ERROR__ = true;</script>
            </body>
            </html>
            """, escapeHTML(componentPath), escapeHTML(e.getMessage()));
    }

    private String serializeToJson(Map<String, ?> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        try {
            return new tools.jackson.databind.json.JsonMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String escapeJS(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String escapeHTML(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    @Override
    public boolean isReady() {
        return ready || useExternalNode;
    }

    @Override
    public String getName() {
        return "Angular Universal SSR Renderer";
    }

    @Override
    public void warmUp() {
        if (!useExternalNode && runtime != null) {
            // Pre-execute JavaScript to warm up JIT
            runtime.execute("(function() { return 'warmed up'; })()");
            log.info("Angular SSR renderer warmed up");
        }
    }

    @Override
    public void close() throws Exception {
        if (runtime != null) {
            runtime.close();
        }
    }
}
