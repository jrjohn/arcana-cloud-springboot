package com.arcana.cloud.ssr;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration properties for the SSR Engine.
 */
@Component
@ConfigurationProperties(prefix = "arcana.ssr")
public class SSRConfiguration {

    /**
     * Whether SSR is enabled.
     */
    private boolean enabled = true;

    /**
     * Whether React SSR is enabled.
     */
    private boolean reactEnabled = true;

    /**
     * Whether Angular SSR is enabled.
     */
    private boolean angularEnabled = true;

    /**
     * Directory containing React application.
     */
    private String reactAppDir = "arcana-web/react-app";

    /**
     * Directory containing Angular application.
     */
    private String angularAppDir = "arcana-web/angular-app";

    /**
     * Whether to cache rendered pages.
     */
    private boolean cacheEnabled = true;

    /**
     * Default cache duration in seconds.
     */
    private int defaultCacheDuration = 60;

    /**
     * Maximum cache size in MB.
     */
    private int maxCacheSize = 100;

    /**
     * GraalJS pool size for rendering.
     */
    private int graalPoolSize = 4;

    /**
     * Render timeout in milliseconds.
     */
    private long renderTimeout = 5000;

    /**
     * Whether to use external Node.js process for SSR.
     */
    private boolean useExternalNode = false;

    /**
     * External Node.js SSR server URL.
     */
    private String nodeServerUrl = "http://localhost:3001";

    // Getters and setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isReactEnabled() {
        return enabled && reactEnabled;
    }

    public void setReactEnabled(boolean reactEnabled) {
        this.reactEnabled = reactEnabled;
    }

    public boolean isAngularEnabled() {
        return enabled && angularEnabled;
    }

    public void setAngularEnabled(boolean angularEnabled) {
        this.angularEnabled = angularEnabled;
    }

    public String getReactAppDir() {
        return reactAppDir;
    }

    public void setReactAppDir(String reactAppDir) {
        this.reactAppDir = reactAppDir;
    }

    public Path getReactAppPath() {
        return Paths.get(reactAppDir).toAbsolutePath();
    }

    public String getAngularAppDir() {
        return angularAppDir;
    }

    public void setAngularAppDir(String angularAppDir) {
        this.angularAppDir = angularAppDir;
    }

    public Path getAngularAppPath() {
        return Paths.get(angularAppDir).toAbsolutePath();
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public int getDefaultCacheDuration() {
        return defaultCacheDuration;
    }

    public void setDefaultCacheDuration(int defaultCacheDuration) {
        this.defaultCacheDuration = defaultCacheDuration;
    }

    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }

    public int getGraalPoolSize() {
        return graalPoolSize;
    }

    public void setGraalPoolSize(int graalPoolSize) {
        this.graalPoolSize = graalPoolSize;
    }

    public long getRenderTimeout() {
        return renderTimeout;
    }

    public void setRenderTimeout(long renderTimeout) {
        this.renderTimeout = renderTimeout;
    }

    public boolean isUseExternalNode() {
        return useExternalNode;
    }

    public void setUseExternalNode(boolean useExternalNode) {
        this.useExternalNode = useExternalNode;
    }

    public String getNodeServerUrl() {
        return nodeServerUrl;
    }

    public void setNodeServerUrl(String nodeServerUrl) {
        this.nodeServerUrl = nodeServerUrl;
    }
}
