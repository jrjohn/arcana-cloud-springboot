package com.arcana.cloud.plugin.runtime;

import com.arcana.cloud.plugin.extension.*;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for plugin extensions.
 *
 * <p>The extension registry tracks all extension point implementations
 * registered by plugins and provides methods to query them.</p>
 */
public class ExtensionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ExtensionRegistry.class);

    private final BundleContext bundleContext;
    private final Map<Class<?>, List<ExtensionEntry<?>>> extensionsByType;
    private final Map<String, List<ExtensionEntry<?>>> extensionsByPlugin;

    public ExtensionRegistry(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
        this.extensionsByType = new ConcurrentHashMap<>();
        this.extensionsByPlugin = new ConcurrentHashMap<>();
    }

    /**
     * Registers an extension.
     *
     * @param pluginKey the plugin key
     * @param extensionType the extension interface
     * @param extension the extension instance
     * @param <T> the extension type
     */
    public <T> void registerExtension(String pluginKey, Class<T> extensionType, T extension) {
        ExtensionEntry<T> entry = new ExtensionEntry<>(pluginKey, extensionType, extension);

        extensionsByType.computeIfAbsent(extensionType, k -> new ArrayList<>()).add(entry);
        extensionsByPlugin.computeIfAbsent(pluginKey, k -> new ArrayList<>()).add(entry);

        log.debug("Registered extension {} from plugin {}", extensionType.getSimpleName(), pluginKey);
    }

    /**
     * Unregisters all extensions from a plugin.
     *
     * @param pluginKey the plugin key
     */
    public void unregisterExtensions(String pluginKey) {
        List<ExtensionEntry<?>> entries = extensionsByPlugin.remove(pluginKey);
        if (entries != null) {
            for (ExtensionEntry<?> entry : entries) {
                List<ExtensionEntry<?>> typeEntries = extensionsByType.get(entry.getExtensionType());
                if (typeEntries != null) {
                    typeEntries.removeIf(e -> e.getPluginKey().equals(pluginKey));
                }
            }
            log.debug("Unregistered {} extensions from plugin {}", entries.size(), pluginKey);
        }
    }

    /**
     * Returns all extensions of a type.
     *
     * @param extensionType the extension interface
     * @param <T> the extension type
     * @return list of extensions
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getExtensions(Class<T> extensionType) {
        // First check locally registered extensions
        List<ExtensionEntry<?>> entries = extensionsByType.get(extensionType);
        List<T> results = new ArrayList<>();

        if (entries != null) {
            for (ExtensionEntry<?> entry : entries) {
                results.add((T) entry.getExtension());
            }
        }

        // Also check OSGi service registry
        try {
            Collection<ServiceReference<T>> references =
                bundleContext.getServiceReferences(extensionType, null);
            for (ServiceReference<T> ref : references) {
                T service = bundleContext.getService(ref);
                if (service != null && !results.contains(service)) {
                    results.add(service);
                }
            }
        } catch (InvalidSyntaxException e) {
            log.error("Error querying OSGi services", e);
        }

        return results;
    }

    /**
     * Returns extensions of a type from a specific plugin.
     *
     * @param extensionType the extension interface
     * @param pluginKey the plugin key
     * @param <T> the extension type
     * @return list of extensions
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getExtensions(Class<T> extensionType, String pluginKey) {
        List<ExtensionEntry<?>> entries = extensionsByPlugin.get(pluginKey);
        if (entries == null) {
            return Collections.emptyList();
        }

        List<T> results = new ArrayList<>();
        for (ExtensionEntry<?> entry : entries) {
            if (entry.getExtensionType().equals(extensionType)) {
                results.add((T) entry.getExtension());
            }
        }
        return results;
    }

    /**
     * Returns all REST endpoint extensions.
     *
     * @return list of REST controllers
     */
    public List<Object> getRestEndpoints() {
        List<Object> endpoints = new ArrayList<>();

        try {
            // Query for services with rest-extension marker
            String filter = "(extension.type=rest-extension)";
            ServiceReference<?>[] references = bundleContext.getAllServiceReferences(null, filter);
            if (references != null) {
                for (ServiceReference<?> ref : references) {
                    Object service = bundleContext.getService(ref);
                    if (service != null) {
                        endpoints.add(service);
                    }
                }
            }
        } catch (InvalidSyntaxException e) {
            log.error("Error querying REST endpoints", e);
        }

        return endpoints;
    }

    /**
     * Returns all event listener extensions.
     *
     * @return map of event type to listeners
     */
    public Map<Class<?>, List<Object>> getEventListeners() {
        Map<Class<?>, List<Object>> listeners = new HashMap<>();

        try {
            String filter = "(extension.type=event-listener)";
            ServiceReference<?>[] references = bundleContext.getAllServiceReferences(null, filter);
            if (references != null) {
                for (ServiceReference<?> ref : references) {
                    Object listener = bundleContext.getService(ref);
                    Object events = ref.getProperty("events");
                    if (listener != null && events instanceof Class<?>[] eventTypes) {
                        for (Class<?> eventType : eventTypes) {
                            listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
                        }
                    }
                }
            }
        } catch (InvalidSyntaxException e) {
            log.error("Error querying event listeners", e);
        }

        return listeners;
    }

    /**
     * Returns all scheduled job extensions.
     *
     * @return list of scheduled jobs with their configurations
     */
    public List<ScheduledJobInfo> getScheduledJobs() {
        List<ScheduledJobInfo> jobs = new ArrayList<>();

        try {
            String filter = "(extension.type=scheduled-job)";
            ServiceReference<?>[] references = bundleContext.getAllServiceReferences(null, filter);
            if (references != null) {
                for (ServiceReference<?> ref : references) {
                    Object job = bundleContext.getService(ref);
                    if (job != null) {
                        jobs.add(new ScheduledJobInfo(
                            (String) ref.getProperty("job.key"),
                            (String) ref.getProperty("plugin.key"),
                            (String) ref.getProperty("cron"),
                            job
                        ));
                    }
                }
            }
        } catch (InvalidSyntaxException e) {
            log.error("Error querying scheduled jobs", e);
        }

        return jobs;
    }

    /**
     * Extension entry wrapper.
     */
    private static class ExtensionEntry<T> {
        private final String pluginKey;
        private final Class<T> extensionType;
        private final T extension;

        ExtensionEntry(String pluginKey, Class<T> extensionType, T extension) {
            this.pluginKey = pluginKey;
            this.extensionType = extensionType;
            this.extension = extension;
        }

        String getPluginKey() {
            return pluginKey;
        }

        Class<T> getExtensionType() {
            return extensionType;
        }

        T getExtension() {
            return extension;
        }
    }

    /**
     * Scheduled job information.
     */
    public static class ScheduledJobInfo {
        private final String jobKey;
        private final String pluginKey;
        private final String cron;
        private final Object job;

        ScheduledJobInfo(String jobKey, String pluginKey, String cron, Object job) {
            this.jobKey = jobKey;
            this.pluginKey = pluginKey;
            this.cron = cron;
            this.job = job;
        }

        public String getJobKey() {
            return jobKey;
        }

        public String getPluginKey() {
            return pluginKey;
        }

        public String getCron() {
            return cron;
        }

        public Object getJob() {
            return job;
        }
    }
}
