package com.arcana.cloud.plugin.runtime.osgi;

import org.apache.felix.framework.Felix;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Factory for creating and configuring the Apache Felix OSGi framework.
 *
 * <p>This class handles the initialization and lifecycle of the embedded
 * OSGi framework that hosts plugin bundles.</p>
 */
public class FelixFrameworkFactory {

    private static final Logger log = LoggerFactory.getLogger(FelixFrameworkFactory.class);

    private final Path felixCacheDir;
    private final Path pluginsDir;
    private final String platformVersion;

    private Framework framework;
    private BundleContext systemBundleContext;

    /**
     * Creates a new Felix framework factory.
     *
     * @param felixCacheDir directory for Felix cache
     * @param pluginsDir directory containing plugin bundles
     * @param platformVersion the platform version string
     */
    public FelixFrameworkFactory(Path felixCacheDir, Path pluginsDir, String platformVersion) {
        this.felixCacheDir = felixCacheDir;
        this.pluginsDir = pluginsDir;
        this.platformVersion = platformVersion;
    }

    /**
     * Creates and starts the Felix framework.
     *
     * @return the started framework
     * @throws BundleException if framework fails to start
     */
    public Framework createAndStart() throws BundleException {
        log.info("Starting Apache Felix OSGi framework...");

        Map<String, String> config = createFrameworkConfig();

        // Use Felix factory
        org.apache.felix.framework.FrameworkFactory factory =
            new org.apache.felix.framework.FrameworkFactory();

        framework = factory.newFramework(config);
        framework.init();
        framework.start();

        systemBundleContext = framework.getBundleContext();

        log.info("Apache Felix OSGi framework started successfully");
        log.info("  Framework version: {}", systemBundleContext.getProperty("org.osgi.framework.version"));
        log.info("  Cache directory: {}", felixCacheDir);
        log.info("  Plugins directory: {}", pluginsDir);

        return framework;
    }

    /**
     * Creates the Felix framework configuration.
     *
     * @return configuration map
     */
    private Map<String, String> createFrameworkConfig() {
        Map<String, String> config = new HashMap<>();

        // Framework storage
        config.put("org.osgi.framework.storage", felixCacheDir.toString());
        config.put("org.osgi.framework.storage.clean", "onFirstInit");

        // Export platform packages to plugins
        config.put("org.osgi.framework.system.packages.extra", buildExtraPackages());

        // Boot delegation for common packages
        config.put("org.osgi.framework.bootdelegation",
            "sun.*,com.sun.*,javax.crypto.*,javax.net.*,javax.security.*");

        // Felix-specific configuration
        config.put("felix.auto.deploy.dir", pluginsDir.toString());
        config.put("felix.auto.deploy.action", "install,start");
        config.put("felix.log.level", "2"); // 1=error, 2=warning, 3=info, 4=debug

        // File install configuration (for hot deployment)
        config.put("felix.fileinstall.dir", pluginsDir.toString());
        config.put("felix.fileinstall.poll", "2000");
        config.put("felix.fileinstall.start.level", "4");
        config.put("felix.fileinstall.noInitialDelay", "true");

        // Platform version property
        config.put("arcana.platform.version", platformVersion);

        return config;
    }

    /**
     * Builds the list of packages to export from the system bundle.
     * These packages are available to all plugin bundles.
     *
     * @return comma-separated package list
     */
    private String buildExtraPackages() {
        return String.join(",",
            // Plugin API packages
            "com.arcana.cloud.plugin.api;version=\"1.0.0\"",
            "com.arcana.cloud.plugin.extension;version=\"1.0.0\"",
            "com.arcana.cloud.plugin.lifecycle;version=\"1.0.0\"",
            "com.arcana.cloud.plugin.event;version=\"1.0.0\"",
            "com.arcana.cloud.plugin.web;version=\"1.0.0\"",

            // Platform entity packages (read-only access)
            "com.arcana.cloud.entity;version=\"1.0.0\"",
            "com.arcana.cloud.dto.request;version=\"1.0.0\"",
            "com.arcana.cloud.dto.response;version=\"1.0.0\"",

            // Jakarta APIs
            "jakarta.persistence;version=\"3.1.0\"",
            "jakarta.validation;version=\"3.0.2\"",
            "jakarta.validation.constraints;version=\"3.0.2\"",
            "jakarta.servlet;version=\"6.0.0\"",
            "jakarta.servlet.http;version=\"6.0.0\"",
            "jakarta.annotation;version=\"2.1.1\"",

            // Spring packages (limited access)
            "org.springframework.stereotype;version=\"6.1.0\"",
            "org.springframework.beans.factory.annotation;version=\"6.1.0\"",
            "org.springframework.web.bind.annotation;version=\"6.1.0\"",
            "org.springframework.http;version=\"6.1.0\"",
            "org.springframework.scheduling.annotation;version=\"6.1.0\"",

            // Logging
            "org.slf4j;version=\"2.0.0\"",

            // Common utilities
            "lombok;version=\"1.18.30\""
        );
    }

    /**
     * Stops the framework.
     */
    public void stop() {
        if (framework != null) {
            try {
                log.info("Stopping Apache Felix OSGi framework...");
                framework.stop();
                framework.waitForStop(30000); // Wait up to 30 seconds
                log.info("Apache Felix OSGi framework stopped");
            } catch (BundleException | InterruptedException e) {
                log.error("Error stopping Felix framework", e);
            }
        }
    }

    /**
     * Returns the system bundle context.
     *
     * @return the bundle context
     */
    public BundleContext getSystemBundleContext() {
        return systemBundleContext;
    }

    /**
     * Returns the framework instance.
     *
     * @return the framework
     */
    public Framework getFramework() {
        return framework;
    }
}
