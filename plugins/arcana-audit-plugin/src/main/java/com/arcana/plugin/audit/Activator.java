package com.arcana.plugin.audit;

import com.arcana.plugin.audit.api.AuditService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Hashtable;

/**
 * OSGi Bundle Activator for the Audit Plugin.
 *
 * <p>This class manages the plugin's lifecycle within the OSGi framework,
 * registering and unregistering services as the bundle starts and stops.</p>
 */
public class Activator implements BundleActivator {

    private static final Logger log = LoggerFactory.getLogger(Activator.class);

    private AuditPlugin plugin;
    private ServiceRegistration<AuditService> auditServiceRegistration;
    private ServiceRegistration<?> restControllerRegistration;
    private ServiceRegistration<?> eventListenerRegistration;

    @Override
    public void start(BundleContext context) throws Exception {
        log.info("Starting Audit Plugin...");

        // Create and initialize the main plugin
        plugin = new AuditPlugin();
        plugin.initialize(context);

        // Register the audit service
        AuditServiceImpl auditService = new AuditServiceImpl(plugin.getAuditRepository());

        Dictionary<String, Object> serviceProps = new Hashtable<>();
        serviceProps.put("service.name", "audit-service");
        serviceProps.put("plugin.key", "com.arcana.plugin.audit");

        auditServiceRegistration = context.registerService(
            AuditService.class, auditService, serviceProps);

        // Register REST controller
        Dictionary<String, Object> restProps = new Hashtable<>();
        restProps.put("extension.type", "rest-extension");
        restProps.put("extension.key", "audit-rest");
        restProps.put("path", "/api/v1/plugins/audit");
        restProps.put("plugin.key", "com.arcana.plugin.audit");

        AuditRestController restController = new AuditRestController(auditService);
        restControllerRegistration = context.registerService(
            Object.class, restController, restProps);

        // Register event listener
        Dictionary<String, Object> listenerProps = new Hashtable<>();
        listenerProps.put("extension.type", "event-listener");
        listenerProps.put("extension.key", "user-event-listener");
        listenerProps.put("plugin.key", "com.arcana.plugin.audit");

        UserEventListener eventListener = new UserEventListener(auditService);
        eventListenerRegistration = context.registerService(
            Object.class, eventListener, listenerProps);

        log.info("Audit Plugin started successfully");
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        log.info("Stopping Audit Plugin...");

        // Unregister services
        if (eventListenerRegistration != null) {
            eventListenerRegistration.unregister();
        }
        if (restControllerRegistration != null) {
            restControllerRegistration.unregister();
        }
        if (auditServiceRegistration != null) {
            auditServiceRegistration.unregister();
        }

        // Cleanup plugin
        if (plugin != null) {
            plugin.shutdown();
        }

        log.info("Audit Plugin stopped");
    }
}
