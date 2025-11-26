package com.arcana.cloud.ssr.context;

import java.util.*;

/**
 * Context for server-side rendering containing request data and props.
 */
public class SSRContext {

    private final String path;
    private final Map<String, String> queryParams;
    private final Map<String, String> headers;
    private final Map<String, Object> props;
    private final Long currentUserId;
    private final Locale locale;
    private final int cacheDuration;

    private SSRContext(Builder builder) {
        this.path = builder.path;
        this.queryParams = Collections.unmodifiableMap(builder.queryParams);
        this.headers = Collections.unmodifiableMap(builder.headers);
        this.props = Collections.unmodifiableMap(builder.props);
        this.currentUserId = builder.currentUserId;
        this.locale = builder.locale;
        this.cacheDuration = builder.cacheDuration;
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
        return headers.get(name);
    }

    public Map<String, Object> getProps() {
        return props;
    }

    @SuppressWarnings("unchecked")
    public <T> T getProp(String name) {
        return (T) props.get(name);
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

    public int getCacheDuration() {
        return cacheDuration;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, queryParams, currentUserId, locale);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SSRContext that = (SSRContext) obj;
        return Objects.equals(path, that.path) &&
               Objects.equals(queryParams, that.queryParams) &&
               Objects.equals(currentUserId, that.currentUserId) &&
               Objects.equals(locale, that.locale);
    }

    /**
     * Builder for SSRContext.
     */
    public static class Builder {
        private String path = "/";
        private final Map<String, String> queryParams = new HashMap<>();
        private final Map<String, String> headers = new HashMap<>();
        private final Map<String, Object> props = new HashMap<>();
        private Long currentUserId;
        private Locale locale = Locale.ENGLISH;
        private int cacheDuration = 0;

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
            this.headers.put(name, value);
            return this;
        }

        public Builder props(Map<String, Object> props) {
            this.props.putAll(props);
            return this;
        }

        public Builder prop(String name, Object value) {
            this.props.put(name, value);
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

        public Builder cacheDuration(int seconds) {
            this.cacheDuration = seconds;
            return this;
        }

        public SSRContext build() {
            return new SSRContext(this);
        }
    }
}
