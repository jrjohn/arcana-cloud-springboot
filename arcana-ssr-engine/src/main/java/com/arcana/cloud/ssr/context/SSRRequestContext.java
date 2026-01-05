package com.arcana.cloud.ssr.context;

import jakarta.servlet.http.HttpServletRequest;

import java.util.*;

/**
 * HTTP request context for SSR rendering.
 */
public class SSRRequestContext {

    private final String path;
    private final Map<String, String> queryParams;
    private final Map<String, String> headers;
    private final Long currentUserId;
    private final Locale locale;
    private final String ipAddress;
    private final String userAgent;

    private SSRRequestContext(Builder builder) {
        this.path = builder.path;
        this.queryParams = Collections.unmodifiableMap(builder.queryParams);
        this.headers = Collections.unmodifiableMap(builder.headers);
        this.currentUserId = builder.currentUserId;
        this.locale = builder.locale;
        this.ipAddress = builder.ipAddress;
        this.userAgent = builder.userAgent;
    }

    /**
     * Creates an SSRRequestContext from an HttpServletRequest.
     *
     * @param request the HTTP request
     * @param currentUserId the authenticated user ID (if any)
     * @return the request context
     */
    public static SSRRequestContext fromHttpRequest(HttpServletRequest request, Long currentUserId) {
        Builder builder = builder()
            .path(request.getRequestURI())
            .currentUserId(currentUserId)
            .ipAddress(getClientIp(request))
            .userAgent(request.getHeader("User-Agent"))
            .locale(request.getLocale());

        // Extract query parameters
        request.getParameterMap().forEach((name, values) -> {
            if (values.length > 0) {
                builder.queryParam(name, values[0]);
            }
        });

        // Extract headers
        Collections.list(request.getHeaderNames()).forEach(name ->
            builder.header(name.toLowerCase(), request.getHeader(name))
        );

        return builder.build();
    }

    private static String getClientIp(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public String getQueryParam(String name) {
        return queryParams.get(name);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getHeader(String name) {
        return headers.get(name.toLowerCase());
    }

    public Long getCurrentUserId() {
        return currentUserId;
    }

    public boolean isAuthenticated() {
        return currentUserId != null;
    }

    public Locale getLocale() {
        return locale;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for SSRRequestContext.
     */
    public static class Builder {
        private String path = "/";
        private final Map<String, String> queryParams = new HashMap<>();
        private final Map<String, String> headers = new HashMap<>();
        private Long currentUserId;
        private Locale locale = Locale.ENGLISH;
        private String ipAddress;
        private String userAgent;

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder queryParams(Map<String, String> params) {
            this.queryParams.putAll(params);
            return this;
        }

        public Builder queryParam(String name, String value) {
            this.queryParams.put(name, value);
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers.putAll(headers);
            return this;
        }

        public Builder header(String name, String value) {
            this.headers.put(name.toLowerCase(), value);
            return this;
        }

        public Builder currentUserId(Long userId) {
            this.currentUserId = userId;
            return this;
        }

        public Builder locale(Locale locale) {
            this.locale = locale;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public SSRRequestContext build() {
            return new SSRRequestContext(this);
        }
    }
}
