package com.arcana.cloud.plugin.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration properties for the plugin system.
 */
@Component
@ConfigurationProperties(prefix = "arcana.plugin")
public class PluginConfiguration {

    /**
     * Whether the plugin system is enabled.
     */
    private boolean enabled = true;

    /**
     * Directory containing plugin bundles.
     */
    private String pluginsDir = "plugins";

    /**
     * Directory for Felix framework cache.
     */
    private String cacheDir = "plugins/.cache";

    /**
     * Platform version string.
     */
    private String platformVersion = "1.0.0";

    /**
     * Whether to auto-install plugins on startup.
     */
    private boolean autoInstall = true;

    /**
     * Whether to auto-start plugins after installation.
     */
    private boolean autoStart = true;

    /**
     * Whether to watch for new plugins.
     */
    private boolean hotDeploy = true;

    /**
     * Hot deploy poll interval in milliseconds.
     */
    private long hotDeployPollInterval = 2000;

    /**
     * Maximum time to wait for plugin operations.
     */
    private long operationTimeout = 30000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPluginsDir() {
        return pluginsDir;
    }

    public void setPluginsDir(String pluginsDir) {
        this.pluginsDir = pluginsDir;
    }

    public Path getPluginsDirectory() {
        return Paths.get(pluginsDir).toAbsolutePath();
    }

    public String getCacheDir() {
        return cacheDir;
    }

    public void setCacheDir(String cacheDir) {
        this.cacheDir = cacheDir;
    }

    public Path getCacheDirectory() {
        return Paths.get(cacheDir).toAbsolutePath();
    }

    public String getPlatformVersion() {
        return platformVersion;
    }

    public void setPlatformVersion(String platformVersion) {
        this.platformVersion = platformVersion;
    }

    public boolean isAutoInstall() {
        return autoInstall;
    }

    public void setAutoInstall(boolean autoInstall) {
        this.autoInstall = autoInstall;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public boolean isHotDeploy() {
        return hotDeploy;
    }

    public void setHotDeploy(boolean hotDeploy) {
        this.hotDeploy = hotDeploy;
    }

    public long getHotDeployPollInterval() {
        return hotDeployPollInterval;
    }

    public void setHotDeployPollInterval(long hotDeployPollInterval) {
        this.hotDeployPollInterval = hotDeployPollInterval;
    }

    public long getOperationTimeout() {
        return operationTimeout;
    }

    public void setOperationTimeout(long operationTimeout) {
        this.operationTimeout = operationTimeout;
    }

    /**
     * Ensures required directories exist.
     */
    public void ensureDirectoriesExist() throws IOException {
        Files.createDirectories(getPluginsDirectory());
        Files.createDirectories(getCacheDirectory());
    }
}
