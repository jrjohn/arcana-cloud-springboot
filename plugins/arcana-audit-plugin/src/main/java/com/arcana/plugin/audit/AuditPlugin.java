package com.arcana.plugin.audit;

import com.arcana.plugin.audit.repository.AuditRepository;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Main plugin class for the Audit Plugin.
 *
 * <p>Manages plugin initialization, configuration, and provides
 * access to plugin-level resources.</p>
 */
public class AuditPlugin {

    private static final Logger log = LoggerFactory.getLogger(AuditPlugin.class);

    private BundleContext bundleContext;
    private AuditRepository auditRepository;
    private DataSource dataSource;

    /**
     * Initializes the plugin.
     *
     * @param context the OSGi bundle context
     */
    public void initialize(BundleContext context) {
        this.bundleContext = context;

        log.info("Initializing Audit Plugin");

        // Get DataSource from OSGi service registry (provided by Spring-OSGi bridge)
        ServiceReference<DataSource> dsRef = context.getServiceReference(DataSource.class);
        if (dsRef != null) {
            dataSource = context.getService(dsRef);
            log.info("Obtained DataSource from OSGi registry");
        } else {
            log.warn("DataSource not available in OSGi registry, using in-memory storage");
        }

        // Initialize repository
        auditRepository = new AuditRepository(dataSource);

        // Run migrations if DataSource is available
        if (dataSource != null) {
            runMigrations();
        }

        log.info("Audit Plugin initialized");
    }

    /**
     * Shuts down the plugin.
     */
    public void shutdown() {
        log.info("Shutting down Audit Plugin");

        if (auditRepository != null) {
            auditRepository.close();
        }

        log.info("Audit Plugin shutdown complete");
    }

    /**
     * Runs database migrations for the plugin.
     */
    private void runMigrations() {
        log.info("Running Audit Plugin database migrations");
        // In a real implementation, this would use Flyway
        // For now, we'll rely on the platform's migration support
        try {
            auditRepository.ensureSchemaExists();
            log.info("Audit Plugin migrations completed");
        } catch (Exception e) {
            log.error("Failed to run migrations", e);
        }
    }

    /**
     * Returns the audit repository.
     *
     * @return the audit repository
     */
    public AuditRepository getAuditRepository() {
        return auditRepository;
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
     * Returns a configuration property.
     *
     * @param key the property key
     * @param defaultValue the default value
     * @return the property value
     */
    public String getConfig(String key, String defaultValue) {
        String value = bundleContext.getProperty("arcana.plugin.audit." + key);
        return value != null ? value : defaultValue;
    }

    /**
     * Returns the retention days for audit logs.
     *
     * @return retention days
     */
    public int getRetentionDays() {
        return Integer.parseInt(getConfig("retention.days", "90"));
    }
}
