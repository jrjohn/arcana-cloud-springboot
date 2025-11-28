package com.arcana.cloud.plugin.runtime.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for plugin security settings.
 *
 * <p>Controls which Spring beans are accessible to plugins (whitelisting)
 * and enables/disables security features like signature verification.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "arcana.plugin.security")
public class PluginSecurityConfig {

    /**
     * Whether plugin security is enabled (default: true).
     */
    private boolean enabled = true;

    /**
     * Whether to require signed plugins (default: false for dev, true for prod).
     */
    private boolean requireSignedPlugins = false;

    /**
     * Path to the trusted certificates for plugin signature verification.
     */
    private String trustedCertificatesPath = "";

    /**
     * Whether to enable audit logging for plugin operations (default: true).
     */
    private boolean auditEnabled = true;

    /**
     * Whitelisted bean types that plugins can access.
     * By default, only safe types are allowed.
     */
    private Set<String> whitelistedBeanTypes = new HashSet<>(Set.of(
        // Data access (read-only patterns)
        "javax.sql.DataSource",
        // Password encoding (safe utility)
        "org.springframework.security.crypto.password.PasswordEncoder",
        // Cache operations
        "org.springframework.cache.CacheManager",
        // Event publishing
        "org.springframework.context.ApplicationEventPublisher",
        // Environment (read-only)
        "org.springframework.core.env.Environment"
    ));

    /**
     * Blacklisted bean types that plugins can NEVER access.
     * This takes precedence over whitelist.
     */
    private Set<String> blacklistedBeanTypes = new HashSet<>(Set.of(
        // Security-sensitive
        "com.arcana.cloud.security.JwtTokenProvider",
        "org.springframework.security.authentication.AuthenticationManager",
        "org.springframework.security.config.annotation.web.builders.HttpSecurity",
        // Repository access (use service layer instead)
        "com.arcana.cloud.repository.UserRepository",
        "com.arcana.cloud.repository.RefreshTokenRepository",
        // Internal services
        "com.arcana.cloud.service.impl.AuthServiceImpl",
        "com.arcana.cloud.service.impl.UserServiceImpl"
    ));

    /**
     * Maximum plugin JAR size in bytes (default: 50MB).
     */
    private long maxPluginSizeBytes = 50 * 1024 * 1024;

    /**
     * Maximum time allowed for plugin initialization in seconds.
     */
    private int initializationTimeoutSeconds = 30;

    /**
     * Maximum number of plugins that can be installed.
     */
    private int maxPluginCount = 100;

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRequireSignedPlugins() {
        return requireSignedPlugins;
    }

    public void setRequireSignedPlugins(boolean requireSignedPlugins) {
        this.requireSignedPlugins = requireSignedPlugins;
    }

    public String getTrustedCertificatesPath() {
        return trustedCertificatesPath;
    }

    public void setTrustedCertificatesPath(String trustedCertificatesPath) {
        this.trustedCertificatesPath = trustedCertificatesPath;
    }

    public boolean isAuditEnabled() {
        return auditEnabled;
    }

    public void setAuditEnabled(boolean auditEnabled) {
        this.auditEnabled = auditEnabled;
    }

    public Set<String> getWhitelistedBeanTypes() {
        return whitelistedBeanTypes;
    }

    public void setWhitelistedBeanTypes(Set<String> whitelistedBeanTypes) {
        this.whitelistedBeanTypes = whitelistedBeanTypes;
    }

    public Set<String> getBlacklistedBeanTypes() {
        return blacklistedBeanTypes;
    }

    public void setBlacklistedBeanTypes(Set<String> blacklistedBeanTypes) {
        this.blacklistedBeanTypes = blacklistedBeanTypes;
    }

    public long getMaxPluginSizeBytes() {
        return maxPluginSizeBytes;
    }

    public void setMaxPluginSizeBytes(long maxPluginSizeBytes) {
        this.maxPluginSizeBytes = maxPluginSizeBytes;
    }

    public int getInitializationTimeoutSeconds() {
        return initializationTimeoutSeconds;
    }

    public void setInitializationTimeoutSeconds(int initializationTimeoutSeconds) {
        this.initializationTimeoutSeconds = initializationTimeoutSeconds;
    }

    public int getMaxPluginCount() {
        return maxPluginCount;
    }

    public void setMaxPluginCount(int maxPluginCount) {
        this.maxPluginCount = maxPluginCount;
    }

    /**
     * Checks if a bean type is allowed for plugin access.
     *
     * @param beanType the fully qualified class name
     * @return true if allowed, false otherwise
     */
    public boolean isBeanTypeAllowed(String beanType) {
        if (!enabled) {
            return true; // Security disabled, allow all
        }

        // Blacklist takes precedence
        if (blacklistedBeanTypes.contains(beanType)) {
            return false;
        }

        // Check whitelist
        return whitelistedBeanTypes.contains(beanType);
    }

    /**
     * Checks if a bean type is allowed for plugin access.
     *
     * @param beanClass the class
     * @return true if allowed, false otherwise
     */
    public boolean isBeanTypeAllowed(Class<?> beanClass) {
        return isBeanTypeAllowed(beanClass.getName());
    }
}
