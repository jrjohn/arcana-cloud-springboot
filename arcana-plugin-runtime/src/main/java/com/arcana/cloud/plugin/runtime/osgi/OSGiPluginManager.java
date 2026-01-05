package com.arcana.cloud.plugin.runtime.osgi;

import com.arcana.cloud.plugin.lifecycle.PluginState;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages OSGi bundles representing plugins.
 *
 * <p>This class provides high-level operations for installing, starting,
 * stopping, and uninstalling plugin bundles.</p>
 */
public class OSGiPluginManager implements BundleListener {

    private static final Logger log = LoggerFactory.getLogger(OSGiPluginManager.class);

    private static final String ARCANA_PLUGIN_KEY = "Arcana-Plugin-Key";
    private static final String ARCANA_PLUGIN_NAME = "Arcana-Plugin-Name";

    private final BundleContext bundleContext;
    private final Map<String, Bundle> pluginBundles;
    private final Map<Long, String> bundleIdToPluginKey;
    private final List<PluginBundleListener> listeners;

    /**
     * Creates a new OSGi plugin manager.
     *
     * @param bundleContext the OSGi bundle context
     */
    public OSGiPluginManager(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
        this.pluginBundles = new ConcurrentHashMap<>();
        this.bundleIdToPluginKey = new ConcurrentHashMap<>();
        this.listeners = new ArrayList<>();

        // Register as bundle listener
        bundleContext.addBundleListener(this);

        // Scan for already installed bundles
        scanInstalledBundles();
    }

    /**
     * Scans for bundles already installed in the framework.
     */
    private void scanInstalledBundles() {
        for (Bundle bundle : bundleContext.getBundles()) {
            String pluginKey = getPluginKey(bundle);
            if (pluginKey != null) {
                registerPluginBundle(pluginKey, bundle);
            }
        }
    }

    /**
     * Installs a plugin bundle from a file path.
     *
     * @param bundlePath path to the bundle JAR
     * @return the installed bundle
     * @throws BundleException if installation fails
     */
    public Bundle installPlugin(Path bundlePath) throws BundleException {
        log.info("Installing plugin bundle: {}", bundlePath);

        String location = bundlePath.toUri().toString();
        Bundle bundle = bundleContext.installBundle(location);

        String pluginKey = getPluginKey(bundle);
        if (pluginKey != null) {
            registerPluginBundle(pluginKey, bundle);
            log.info("Plugin installed: {} (bundle id: {})", pluginKey, bundle.getBundleId());
        } else {
            log.warn("Bundle {} does not have Arcana-Plugin-Key header", bundle.getSymbolicName());
        }

        return bundle;
    }

    /**
     * Installs a plugin bundle from an input stream.
     *
     * @param location unique location identifier
     * @param inputStream the bundle content
     * @return the installed bundle
     * @throws BundleException if installation fails
     */
    public Bundle installPlugin(String location, InputStream inputStream) throws BundleException {
        Bundle bundle = bundleContext.installBundle(location, inputStream);

        String pluginKey = getPluginKey(bundle);
        if (pluginKey != null) {
            registerPluginBundle(pluginKey, bundle);
        }

        return bundle;
    }

    /**
     * Starts (enables) a plugin.
     *
     * @param pluginKey the plugin key
     * @throws BundleException if start fails
     */
    public void startPlugin(String pluginKey) throws BundleException {
        Bundle bundle = pluginBundles.get(pluginKey);
        if (bundle == null) {
            throw new IllegalArgumentException("Plugin not found: " + pluginKey);
        }

        log.info("Starting plugin: {}", pluginKey);
        bundle.start();
    }

    /**
     * Stops (disables) a plugin.
     *
     * @param pluginKey the plugin key
     * @throws BundleException if stop fails
     */
    public void stopPlugin(String pluginKey) throws BundleException {
        Bundle bundle = pluginBundles.get(pluginKey);
        if (bundle == null) {
            throw new IllegalArgumentException("Plugin not found: " + pluginKey);
        }

        log.info("Stopping plugin: {}", pluginKey);
        bundle.stop();
    }

    /**
     * Uninstalls a plugin.
     *
     * @param pluginKey the plugin key
     * @throws BundleException if uninstall fails
     */
    public void uninstallPlugin(String pluginKey) throws BundleException {
        Bundle bundle = pluginBundles.get(pluginKey);
        if (bundle == null) {
            throw new IllegalArgumentException("Plugin not found: " + pluginKey);
        }

        log.info("Uninstalling plugin: {}", pluginKey);
        bundle.uninstall();
        unregisterPluginBundle(pluginKey);
    }

    /**
     * Updates a plugin bundle.
     *
     * @param pluginKey the plugin key
     * @param bundlePath path to the new bundle JAR
     * @throws BundleException if update fails
     */
    public void updatePlugin(String pluginKey, Path bundlePath) throws BundleException {
        Bundle bundle = pluginBundles.get(pluginKey);
        if (bundle == null) {
            throw new IllegalArgumentException("Plugin not found: " + pluginKey);
        }

        log.info("Updating plugin: {} from {}", pluginKey, bundlePath);
        try (InputStream is = Files.newInputStream(bundlePath)) {
            bundle.update(is);
        } catch (Exception e) {
            throw new BundleException("Failed to update plugin: " + pluginKey, e);
        }
    }

    /**
     * Returns the state of a plugin.
     *
     * @param pluginKey the plugin key
     * @return the plugin state
     */
    public PluginState getPluginState(String pluginKey) {
        Bundle bundle = pluginBundles.get(pluginKey);
        if (bundle == null) {
            return PluginState.UNINSTALLED;
        }
        return PluginState.fromOsgiState(bundle.getState());
    }

    /**
     * Returns all registered plugin keys.
     *
     * @return set of plugin keys
     */
    public Set<String> getPluginKeys() {
        return Collections.unmodifiableSet(pluginBundles.keySet());
    }

    /**
     * Returns the bundle for a plugin.
     *
     * @param pluginKey the plugin key
     * @return the bundle or null
     */
    public Bundle getPluginBundle(String pluginKey) {
        return pluginBundles.get(pluginKey);
    }

    /**
     * Returns the plugin key from bundle headers.
     */
    private String getPluginKey(Bundle bundle) {
        Dictionary<String, String> headers = bundle.getHeaders();
        return headers.get(ARCANA_PLUGIN_KEY);
    }

    /**
     * Registers a plugin bundle.
     */
    private void registerPluginBundle(String pluginKey, Bundle bundle) {
        pluginBundles.put(pluginKey, bundle);
        bundleIdToPluginKey.put(bundle.getBundleId(), pluginKey);
    }

    /**
     * Unregisters a plugin bundle.
     */
    private void unregisterPluginBundle(String pluginKey) {
        Bundle bundle = pluginBundles.remove(pluginKey);
        if (bundle != null) {
            bundleIdToPluginKey.remove(bundle.getBundleId());
        }
    }

    /**
     * Adds a plugin bundle listener.
     *
     * @param listener the listener
     */
    public void addListener(PluginBundleListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a plugin bundle listener.
     *
     * @param listener the listener
     */
    public void removeListener(PluginBundleListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        Bundle bundle = event.getBundle();
        String pluginKey = bundleIdToPluginKey.get(bundle.getBundleId());

        if (pluginKey == null) {
            // Check if this is a new plugin bundle
            pluginKey = getPluginKey(bundle);
            if (pluginKey != null && event.getType() == BundleEvent.INSTALLED) {
                registerPluginBundle(pluginKey, bundle);
            }
        }

        if (pluginKey != null) {
            PluginState state = PluginState.fromOsgiState(bundle.getState());
            notifyListeners(pluginKey, event.getType(), state);

            if (event.getType() == BundleEvent.UNINSTALLED) {
                unregisterPluginBundle(pluginKey);
            }
        }
    }

    private void notifyListeners(String pluginKey, int eventType, PluginState state) {
        for (PluginBundleListener listener : listeners) {
            try {
                listener.onPluginStateChanged(pluginKey, eventType, state);
            } catch (Exception e) {
                log.error("Error notifying plugin listener", e);
            }
        }
    }

    /**
     * Shuts down the plugin manager.
     */
    public void shutdown() {
        bundleContext.removeBundleListener(this);
        pluginBundles.clear();
        bundleIdToPluginKey.clear();
    }

    /**
     * Listener for plugin bundle events.
     */
    public interface PluginBundleListener {
        /**
         * Called when a plugin's state changes.
         *
         * @param pluginKey the plugin key
         * @param eventType the OSGi bundle event type
         * @param newState the new plugin state
         */
        void onPluginStateChanged(String pluginKey, int eventType, PluginState newState);
    }
}
