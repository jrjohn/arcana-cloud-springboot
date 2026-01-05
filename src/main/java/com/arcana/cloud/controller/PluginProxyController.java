package com.arcana.cloud.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Enumeration;

/**
 * Proxy controller for plugin REST endpoints in layered/K8s HTTP mode.
 *
 * <p>When running in layered mode with HTTP protocol, plugins are hosted
 * on the Service Layer. This controller proxies plugin requests from the
 * Controller Layer to the Service Layer.</p>
 *
 * <p>URL pattern: /api/v1/plugins/{pluginKey}/** → Service Layer</p>
 */
@RestController
@RequestMapping("/api/v1/proxy/plugins")
@Tag(name = "Plugin Proxy", description = "Proxy for plugin endpoints in layered mode")
@ConditionalOnProperty(name = "communication.protocol", havingValue = "http")
@ConditionalOnExpression("'${deployment.layer:}' == 'controller'")
public class PluginProxyController {

    private static final Logger log = LoggerFactory.getLogger(PluginProxyController.class);

    private final RestTemplate restTemplate;

    @Value("${service.http.url:http://localhost:8081}")
    private String serviceLayerUrl;

    public PluginProxyController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Proxies GET requests to plugin endpoints.
     */
    @GetMapping("/{pluginKey}/**")
    @Operation(summary = "Proxy GET request to plugin")
    public ResponseEntity<String> proxyGet(
            @PathVariable String pluginKey,
            HttpServletRequest request) {
        return proxyRequest(pluginKey, request, HttpMethod.GET, null);
    }

    /**
     * Proxies POST requests to plugin endpoints.
     */
    @PostMapping("/{pluginKey}/**")
    @Operation(summary = "Proxy POST request to plugin")
    public ResponseEntity<String> proxyPost(
            @PathVariable String pluginKey,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {
        return proxyRequest(pluginKey, request, HttpMethod.POST, body);
    }

    /**
     * Proxies PUT requests to plugin endpoints.
     */
    @PutMapping("/{pluginKey}/**")
    @Operation(summary = "Proxy PUT request to plugin")
    public ResponseEntity<String> proxyPut(
            @PathVariable String pluginKey,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {
        return proxyRequest(pluginKey, request, HttpMethod.PUT, body);
    }

    /**
     * Proxies PATCH requests to plugin endpoints.
     */
    @PatchMapping("/{pluginKey}/**")
    @Operation(summary = "Proxy PATCH request to plugin")
    public ResponseEntity<String> proxyPatch(
            @PathVariable String pluginKey,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {
        return proxyRequest(pluginKey, request, HttpMethod.PATCH, body);
    }

    /**
     * Proxies DELETE requests to plugin endpoints.
     */
    @DeleteMapping("/{pluginKey}/**")
    @Operation(summary = "Proxy DELETE request to plugin")
    public ResponseEntity<String> proxyDelete(
            @PathVariable String pluginKey,
            HttpServletRequest request) {
        return proxyRequest(pluginKey, request, HttpMethod.DELETE, null);
    }

    /**
     * Proxies the request to the service layer.
     */
    private ResponseEntity<String> proxyRequest(
            String pluginKey,
            HttpServletRequest request,
            HttpMethod method,
            String body) {

        // Build the target URL
        String requestUri = request.getRequestURI();
        String pluginPath = extractPluginPath(requestUri, pluginKey);
        String queryString = request.getQueryString();

        String targetUrl = buildTargetUrl(pluginKey, pluginPath, queryString);

        log.debug("Proxying {} {} to {}", method, requestUri, targetUrl);

        try {
            // Copy headers
            HttpHeaders headers = copyHeaders(request);

            // Create request entity
            HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);

            // Execute request
            ResponseEntity<String> response = restTemplate.exchange(
                targetUrl,
                method,
                requestEntity,
                String.class
            );

            // Return response with original status and headers
            return ResponseEntity
                .status(response.getStatusCode())
                .headers(filterResponseHeaders(response.getHeaders()))
                .body(response.getBody());

        } catch (RestClientException e) {
            log.error("Failed to proxy request to plugin {}: {}", pluginKey, e.getMessage());
            return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"success\":false,\"error\":\"Failed to reach plugin service: " +
                      e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    /**
     * Extracts the plugin-specific path from the request URI.
     */
    private String extractPluginPath(String requestUri, String pluginKey) {
        // Remove /api/v1/proxy/plugins/{pluginKey} prefix
        String prefix = "/api/v1/proxy/plugins/" + pluginKey;
        if (requestUri.startsWith(prefix)) {
            String path = requestUri.substring(prefix.length());
            return path.isEmpty() ? "" : path;
        }
        return "";
    }

    /**
     * Builds the target URL for the service layer.
     */
    private String buildTargetUrl(String pluginKey, String pluginPath, String queryString) {
        StringBuilder url = new StringBuilder(serviceLayerUrl);

        // Remove trailing slash from base URL
        if (url.charAt(url.length() - 1) == '/') {
            url.setLength(url.length() - 1);
        }

        // Standard plugin endpoint pattern on service layer
        url.append("/api/v1/plugins/").append(pluginKey);

        // Add plugin-specific path
        if (pluginPath != null && !pluginPath.isEmpty()) {
            if (!pluginPath.startsWith("/")) {
                url.append("/");
            }
            url.append(pluginPath);
        }

        // Add query string
        if (queryString != null && !queryString.isEmpty()) {
            url.append("?").append(queryString);
        }

        return url.toString();
    }

    /**
     * Copies relevant headers from the incoming request.
     */
    private HttpHeaders copyHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();

        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();

            // Skip hop-by-hop headers
            if (isHopByHopHeader(headerName)) {
                continue;
            }

            Enumeration<String> headerValues = request.getHeaders(headerName);
            while (headerValues.hasMoreElements()) {
                headers.add(headerName, headerValues.nextElement());
            }
        }

        // Add forwarding headers
        headers.set("X-Forwarded-For", request.getRemoteAddr());
        headers.set("X-Forwarded-Proto", request.getScheme());
        headers.set("X-Forwarded-Host", request.getServerName());

        return headers;
    }

    /**
     * Filters response headers to remove hop-by-hop headers.
     */
    private HttpHeaders filterResponseHeaders(HttpHeaders headers) {
        HttpHeaders filtered = new HttpHeaders();
        headers.forEach((name, values) -> {
            if (!isHopByHopHeader(name)) {
                filtered.addAll(name, values);
            }
        });
        return filtered;
    }

    /**
     * Checks if a header is a hop-by-hop header that should not be forwarded.
     */
    private boolean isHopByHopHeader(String headerName) {
        String lower = headerName.toLowerCase();
        return lower.equals("connection") ||
               lower.equals("keep-alive") ||
               lower.equals("proxy-authenticate") ||
               lower.equals("proxy-authorization") ||
               lower.equals("te") ||
               lower.equals("trailers") ||
               lower.equals("transfer-encoding") ||
               lower.equals("upgrade") ||
               lower.equals("host");
    }
}
