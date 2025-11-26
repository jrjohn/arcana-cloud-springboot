package com.arcana.cloud.plugin.runtime.bridge;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridge between Spring ApplicationContext and OSGi service registry.
 *
 * <p>This bridge allows:</p>
 * <ul>
 *   <li>Spring beans to be exposed as OSGi services</li>
 *   <li>OSGi services to be injected into Spring beans</li>
 *   <li>Dynamic discovery of services from both environments</li>
 * </ul>
 *
 * <p>This is essential for plugins (OSGi bundles) to access platform
 * services (Spring beans) and vice versa.</p>
 */
public class SpringOSGiBridge implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SpringOSGiBridge.class);

    private final ApplicationContext applicationContext;
    private final BundleContext bundleContext;
    private final Map<String, ServiceRegistration<?>> exportedServices;
    private final Map<Class<?>, ServiceTracker<?, ?>> serviceTrackers;

    /**
     * Creates a new Spring-OSGi bridge.
     *
     * @param applicationContext the Spring application context
     * @param bundleContext the OSGi bundle context
     */
    public SpringOSGiBridge(ApplicationContext applicationContext, BundleContext bundleContext) {
        this.applicationContext = applicationContext;
        this.bundleContext = bundleContext;
        this.exportedServices = new ConcurrentHashMap<>();
        this.serviceTrackers = new ConcurrentHashMap<>();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("Initializing Spring-OSGi bridge");
        exportPlatformServices();
    }

    @Override
    public void destroy() throws Exception {
        log.info("Shutting down Spring-OSGi bridge");

        // Unregister exported services
        for (ServiceRegistration<?> registration : exportedServices.values()) {
            try {
                registration.unregister();
            } catch (Exception e) {
                log.warn("Error unregistering service", e);
            }
        }
        exportedServices.clear();

        // Close service trackers
        for (ServiceTracker<?, ?> tracker : serviceTrackers.values()) {
            tracker.close();
        }
        serviceTrackers.clear();
    }

    /**
     * Exports platform services to OSGi.
     */
    private void exportPlatformServices() {
        // Export beans annotated with @ExportToOSGi or specific service interfaces
        // This list can be configured externally
        exportBeansByType(javax.sql.DataSource.class);

        // Try to export Spring Security PasswordEncoder if available
        try {
            Class<?> passwordEncoderClass = Class.forName(
                "org.springframework.security.crypto.password.PasswordEncoder");
            exportBeansByTypeByName(passwordEncoderClass);
        } catch (ClassNotFoundException e) {
            log.debug("Spring Security not available, skipping PasswordEncoder export");
        }

        // Export any bean annotated with @ExportToOSGi
        Map<String, Object> exportedBeans = applicationContext.getBeansWithAnnotation(ExportToOSGi.class);
        for (Map.Entry<String, Object> entry : exportedBeans.entrySet()) {
            exportBean(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Exports beans by type using class name lookup (for optional dependencies).
     */
    private void exportBeansByTypeByName(Class<?> serviceType) {
        try {
            Map<String, ?> beans = applicationContext.getBeansOfType(serviceType);
            for (Map.Entry<String, ?> entry : beans.entrySet()) {
                exportService(entry.getKey(), new String[]{serviceType.getName()}, entry.getValue());
            }
        } catch (Exception e) {
            log.debug("No beans of type {} found to export", serviceType.getName());
        }
    }

    /**
     * Exports all beans of a specific type to OSGi.
     *
     * @param serviceType the service interface
     * @param <T> the service type
     */
    public <T> void exportBeansByType(Class<T> serviceType) {
        try {
            Map<String, T> beans = applicationContext.getBeansOfType(serviceType);
            for (Map.Entry<String, T> entry : beans.entrySet()) {
                exportService(entry.getKey(), serviceType, entry.getValue());
            }
        } catch (Exception e) {
            log.debug("No beans of type {} found to export", serviceType.getName());
        }
    }

    /**
     * Exports a specific Spring bean to OSGi.
     *
     * @param beanName the bean name
     * @param bean the bean instance
     */
    public void exportBean(String beanName, Object bean) {
        Class<?>[] interfaces = bean.getClass().getInterfaces();
        if (interfaces.length > 0) {
            String[] interfaceNames = new String[interfaces.length];
            for (int i = 0; i < interfaces.length; i++) {
                interfaceNames[i] = interfaces[i].getName();
            }
            exportService(beanName, interfaceNames, bean);
        } else {
            exportService(beanName, new String[]{bean.getClass().getName()}, bean);
        }
    }

    /**
     * Exports a service to OSGi with a specific interface.
     *
     * @param name the service name
     * @param serviceClass the service interface
     * @param service the service instance
     * @param <T> the service type
     */
    public <T> void exportService(String name, Class<T> serviceClass, T service) {
        exportService(name, new String[]{serviceClass.getName()}, service);
    }

    /**
     * Exports a service to OSGi with multiple interfaces.
     *
     * @param name the service name
     * @param interfaces the service interfaces
     * @param service the service instance
     */
    public void exportService(String name, String[] interfaces, Object service) {
        if (exportedServices.containsKey(name)) {
            log.debug("Service {} already exported, skipping", name);
            return;
        }

        Dictionary<String, Object> properties = new Hashtable<>();
        properties.put("service.name", name);
        properties.put("service.origin", "spring");

        ServiceRegistration<?> registration = bundleContext.registerService(
            interfaces, service, properties);

        exportedServices.put(name, registration);
        log.info("Exported Spring bean '{}' to OSGi as {}", name, Arrays.toString(interfaces));
    }

    /**
     * Imports an OSGi service into Spring.
     *
     * @param serviceClass the service interface
     * @param <T> the service type
     * @return the service or null if not available
     */
    public <T> T importService(Class<T> serviceClass) {
        ServiceReference<T> reference = bundleContext.getServiceReference(serviceClass);
        if (reference != null) {
            return bundleContext.getService(reference);
        }
        return null;
    }

    /**
     * Imports all OSGi services of a type.
     *
     * @param serviceClass the service interface
     * @param <T> the service type
     * @return list of services
     */
    public <T> List<T> importServices(Class<T> serviceClass) {
        try {
            Collection<ServiceReference<T>> references =
                bundleContext.getServiceReferences(serviceClass, null);

            List<T> services = new ArrayList<>();
            for (ServiceReference<T> reference : references) {
                T service = bundleContext.getService(reference);
                if (service != null) {
                    services.add(service);
                }
            }
            return services;
        } catch (Exception e) {
            log.error("Error importing services of type {}", serviceClass.getName(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Creates a service tracker for dynamic service discovery.
     *
     * @param serviceClass the service interface
     * @param listener optional listener for service events
     * @param <T> the service type
     * @return the service tracker
     */
    @SuppressWarnings("unchecked")
    public <T> ServiceTracker<T, T> trackService(Class<T> serviceClass,
                                                   ServiceTrackerCustomizer<T, T> listener) {
        ServiceTracker<T, T> tracker = new ServiceTracker<>(
            bundleContext, serviceClass, listener);
        tracker.open();
        serviceTrackers.put(serviceClass, tracker);
        return tracker;
    }

    /**
     * Returns the bundle context.
     *
     * @return the bundle context
     */
    public BundleContext getBundleContext() {
        return bundleContext;
    }

    /**
     * Returns the application context.
     *
     * @return the application context
     */
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
