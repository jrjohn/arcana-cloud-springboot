package com.arcana.cloud.plugin.runtime;

import com.arcana.cloud.plugin.api.Plugin;
import com.arcana.cloud.plugin.api.PluginAccessor;
import com.arcana.cloud.plugin.lifecycle.PluginLifecycleListener;
import com.arcana.cloud.plugin.lifecycle.PluginState;
import com.arcana.cloud.plugin.runtime.bridge.SpringOSGiBridge;
import com.arcana.cloud.plugin.runtime.config.PluginConfiguration;
import com.arcana.cloud.plugin.runtime.osgi.FelixFrameworkFactory;
import com.arcana.cloud.plugin.runtime.osgi.OSGiPluginManager;
import com.arcana.cloud.plugin.runtime.scanner.PluginScanner;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * High-level plugin manager that coordinates the OSGi framework
 * and provides a simplified API for plugin operations.
 *
 * <p>This is the main entry point for plugin management in the Arcana Cloud platform.</p>
 */
@Component
public class PluginManager implements PluginAccessor {

    private static final Logger log = LoggerFactory.getLogger(PluginManager.class);

    private final PluginConfiguration config;
    private final ApplicationContext applicationContext;

    private FelixFrameworkFactory frameworkFactory;
    private OSGiPluginManager osgiPluginManager;
    private SpringOSGiBridge springOSGiBridge;
    private ExtensionRegistry extensionRegistry;
    private PluginScanner pluginScanner;

    private final List<PluginLifecycleListener> lifecycleListeners;
    private final Map<String, PluginWrapper> pluginWrappers;
    private final List<Consumer<Boolean>> readinessListeners;

    private volatile boolean initialized = false;
    private final CountDownLatch initializationLatch = new CountDownLatch(1);
    private static final long INITIALIZATION_TIMEOUT_SECONDS = 120;

    public PluginManager(PluginConfiguration config, ApplicationContext applicationContext) {
        this.config = config;
        this.applicationContext = applicationContext;
        this.lifecycleListeners = new CopyOnWriteArrayList<>();
        this.pluginWrappers = new HashMap<>();
        this.readinessListeners = new CopyOnWriteArrayList<>();
    }

    @PostConstruct
    public void initialize() {
        if (!config.isEnabled()) {
            log.info("Plugin system is disabled");
            return;
        }

        log.info("Initializing Arcana Cloud Plugin System");
        log.info("  Plugins directory: {}", config.getPluginsDirectory());
        log.info("  Platform version: {}", config.getPlatformVersion());

        try {
            // Ensure directories exist
            config.ensureDirectoriesExist();

            // Initialize OSGi framework
            frameworkFactory = new FelixFrameworkFactory(
                config.getCacheDirectory(),
                config.getPluginsDirectory(),
                config.getPlatformVersion()
            );
            frameworkFactory.createAndStart();
            BundleContext bundleContext = frameworkFactory.getSystemBundleContext();

            // Initialize OSGi plugin manager
            osgiPluginManager = new OSGiPluginManager(bundleContext);
            osgiPluginManager.addListener(this::onPluginStateChanged);

            // Initialize Spring-OSGi bridge
            springOSGiBridge = new SpringOSGiBridge(applicationContext, bundleContext);
            springOSGiBridge.afterPropertiesSet();

            // Initialize extension registry
            extensionRegistry = new ExtensionRegistry(bundleContext);

            // Initialize plugin scanner
            pluginScanner = new PluginScanner(osgiPluginManager, config.getPluginsDirectory());

            // Scan and install plugins
            if (config.isAutoInstall()) {
                pluginScanner.scanAndInstall();
            }

            initialized = true;

            // Signal that initialization is complete
            initializationLatch.countDown();

            // Notify readiness listeners (e.g., gRPC health service)
            notifyReadinessListeners(true);

            log.info("Plugin system initialized successfully. {} plugins installed",
                osgiPluginManager.getPluginKeys().size());

        } catch (Exception e) {
            log.error("Failed to initialize plugin system", e);
            initializationLatch.countDown(); // Release latch even on failure
            notifyReadinessListeners(false);
            throw new RuntimeException("Plugin system initialization failed", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (!initialized) {
            return;
        }

        log.info("Shutting down plugin system");

        try {
            if (osgiPluginManager != null) {
                osgiPluginManager.shutdown();
            }
            if (springOSGiBridge != null) {
                springOSGiBridge.destroy();
            }
            if (frameworkFactory != null) {
                frameworkFactory.stop();
            }
        } catch (Exception e) {
            log.error("Error during plugin system shutdown", e);
        }

        initialized = false;
        log.info("Plugin system shutdown complete");
    }

    /**
     * Installs a plugin from a file path.
     *
     * @param bundlePath path to the plugin JAR
     * @return the plugin key
     * @throws BundleException if installation fails
     */
    public String installPlugin(Path bundlePath) throws BundleException {
        ensureInitialized();
        Bundle bundle = osgiPluginManager.installPlugin(bundlePath);
        return getPluginKeyFromBundle(bundle);
    }

    /**
     * Enables (starts) a plugin.
     *
     * @param pluginKey the plugin key
     * @throws BundleException if start fails
     */
    public void enablePlugin(String pluginKey) throws BundleException {
        ensureInitialized();
        osgiPluginManager.startPlugin(pluginKey);
    }

    /**
     * Disables (stops) a plugin.
     *
     * @param pluginKey the plugin key
     * @throws BundleException if stop fails
     */
    public void disablePlugin(String pluginKey) throws BundleException {
        ensureInitialized();
        osgiPluginManager.stopPlugin(pluginKey);
    }

    /**
     * Uninstalls a plugin.
     *
     * @param pluginKey the plugin key
     * @throws BundleException if uninstall fails
     */
    public void uninstallPlugin(String pluginKey) throws BundleException {
        ensureInitialized();
        osgiPluginManager.uninstallPlugin(pluginKey);
        pluginWrappers.remove(pluginKey);
    }

    /**
     * Updates a plugin with a new version.
     *
     * @param pluginKey the plugin key
     * @param bundlePath path to the new bundle
     * @throws BundleException if update fails
     */
    public void updatePlugin(String pluginKey, Path bundlePath) throws BundleException {
        ensureInitialized();
        osgiPluginManager.updatePlugin(pluginKey, bundlePath);
    }

    /**
     * Returns the state of a plugin.
     *
     * @param pluginKey the plugin key
     * @return the plugin state
     */
    public PluginState getPluginState(String pluginKey) {
        if (!initialized) {
            return PluginState.UNINSTALLED;
        }
        return osgiPluginManager.getPluginState(pluginKey);
    }

    /**
     * Adds a lifecycle listener.
     *
     * @param listener the listener
     */
    public void addLifecycleListener(PluginLifecycleListener listener) {
        lifecycleListeners.add(listener);
    }

    /**
     * Removes a lifecycle listener.
     *
     * @param listener the listener
     */
    public void removeLifecycleListener(PluginLifecycleListener listener) {
        lifecycleListeners.remove(listener);
    }

    // PluginAccessor implementation

    @Override
    public List<Plugin> getPlugins() {
        return new ArrayList<>(pluginWrappers.values().stream()
            .map(PluginWrapper::getPlugin)
            .toList());
    }

    @Override
    public List<Plugin> getEnabledPlugins() {
        return pluginWrappers.values().stream()
            .filter(w -> w.getState() == PluginState.ACTIVE)
            .map(PluginWrapper::getPlugin)
            .toList();
    }

    @Override
    public Optional<Plugin> getPlugin(String pluginKey) {
        PluginWrapper wrapper = pluginWrappers.get(pluginKey);
        return wrapper != null ? Optional.of(wrapper.getPlugin()) : Optional.empty();
    }

    @Override
    public boolean isPluginInstalled(String pluginKey) {
        return pluginWrappers.containsKey(pluginKey);
    }

    @Override
    public boolean isPluginEnabled(String pluginKey) {
        PluginWrapper wrapper = pluginWrappers.get(pluginKey);
        return wrapper != null && wrapper.getState() == PluginState.ACTIVE;
    }

    @Override
    public <T> List<T> getExtensions(Class<T> extensionType) {
        if (!initialized) {
            return Collections.emptyList();
        }
        return extensionRegistry.getExtensions(extensionType);
    }

    @Override
    public <T> List<T> getExtensions(Class<T> extensionType, String pluginKey) {
        if (!initialized) {
            return Collections.emptyList();
        }
        return extensionRegistry.getExtensions(extensionType, pluginKey);
    }

    @Override
    public String getPlatformVersion() {
        return config.getPlatformVersion();
    }

    @Override
    public String getPluginsDirectory() {
        return config.getPluginsDirectory().toString();
    }

    /**
     * Returns the extension registry.
     *
     * @return the extension registry
     */
    public ExtensionRegistry getExtensionRegistry() {
        return extensionRegistry;
    }

    /**
     * Returns the Spring-OSGi bridge.
     *
     * @return the bridge
     */
    public SpringOSGiBridge getSpringOSGiBridge() {
        return springOSGiBridge;
    }

    /**
     * Waits for plugin system initialization to complete.
     *
     * <p>This method blocks until the plugin system is fully initialized,
     * or until the timeout is reached. Use this to ensure plugins are
     * ready before starting services that depend on them (e.g., gRPC server).</p>
     *
     * @return true if initialization completed successfully, false if timeout
     * @throws InterruptedException if the wait is interrupted
     */
    public boolean waitForInitialization() throws InterruptedException {
        log.debug("Waiting for plugin system initialization...");
        boolean completed = initializationLatch.await(INITIALIZATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (completed) {
            log.debug("Plugin system initialization complete");
        } else {
            log.warn("Plugin system initialization timed out after {} seconds", INITIALIZATION_TIMEOUT_SECONDS);
        }
        return completed && initialized;
    }

    /**
     * Waits for plugin system initialization with a custom timeout.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit
     * @return true if initialization completed successfully
     * @throws InterruptedException if the wait is interrupted
     */
    public boolean waitForInitialization(long timeout, TimeUnit unit) throws InterruptedException {
        boolean completed = initializationLatch.await(timeout, unit);
        return completed && initialized;
    }

    /**
     * Checks if the plugin system is ready.
     *
     * @return true if initialized and ready
     */
    public boolean isReady() {
        return initialized && initializationLatch.getCount() == 0;
    }

    /**
     * Adds a readiness listener that will be notified when initialization completes.
     *
     * @param listener the listener (receives true on success, false on failure)
     */
    public void addReadinessListener(Consumer<Boolean> listener) {
        readinessListeners.add(listener);
        // If already initialized, notify immediately
        if (initializationLatch.getCount() == 0) {
            listener.accept(initialized);
        }
    }

    /**
     * Removes a readiness listener.
     *
     * @param listener the listener
     */
    public void removeReadinessListener(Consumer<Boolean> listener) {
        readinessListeners.remove(listener);
    }

    private void notifyReadinessListeners(boolean ready) {
        for (Consumer<Boolean> listener : readinessListeners) {
            try {
                listener.accept(ready);
            } catch (Exception e) {
                log.error("Error notifying readiness listener", e);
            }
        }
    }

    private void onPluginStateChanged(String pluginKey, int eventType, PluginState newState) {
        log.debug("Plugin {} state changed to {}", pluginKey, newState);

        // Notify lifecycle listeners
        for (PluginLifecycleListener listener : lifecycleListeners) {
            try {
                Plugin plugin = getPlugin(pluginKey).orElse(null);
                if (plugin != null) {
                    listener.onPluginStateChanged(plugin, null, newState);
                }
            } catch (Exception e) {
                log.error("Error notifying lifecycle listener", e);
            }
        }
    }

    private String getPluginKeyFromBundle(Bundle bundle) {
        return bundle.getHeaders().get("Arcana-Plugin-Key");
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("Plugin system is not initialized");
        }
    }

    /**
     * Internal wrapper for plugin instances.
     */
    @SuppressWarnings("unused")
    private static class PluginWrapper {
        private final Plugin plugin;
        private PluginState state;

        PluginWrapper(Plugin plugin) {
            this.plugin = plugin;
            this.state = PluginState.INSTALLED;
        }

        Plugin getPlugin() {
            return plugin;
        }

        PluginState getState() {
            return state;
        }

        void setState(PluginState state) {
            this.state = state;
        }
    }
}
