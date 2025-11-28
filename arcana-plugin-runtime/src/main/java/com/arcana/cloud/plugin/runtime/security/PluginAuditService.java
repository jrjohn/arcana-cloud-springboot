package com.arcana.cloud.plugin.runtime.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Audit logging service for plugin operations.
 *
 * <p>Provides comprehensive audit trail for all plugin lifecycle events
 * to support security monitoring and compliance requirements.</p>
 */
@Service
public class PluginAuditService {

    private static final Logger log = LoggerFactory.getLogger(PluginAuditService.class);
    private static final Logger auditLog = LoggerFactory.getLogger("PLUGIN_AUDIT");

    private final PluginSecurityConfig securityConfig;
    private final List<AuditEntry> auditEntries;
    private final Map<String, List<AuditEntry>> entriesByPlugin;

    // Maximum audit entries to keep in memory
    private static final int MAX_ENTRIES = 10000;

    public PluginAuditService(PluginSecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
        this.auditEntries = new CopyOnWriteArrayList<>();
        this.entriesByPlugin = new ConcurrentHashMap<>();
    }

    /**
     * Audit entry representing a plugin operation.
     */
    public record AuditEntry(
        String id,
        Instant timestamp,
        AuditAction action,
        String pluginKey,
        String pluginVersion,
        String userId,
        String ipAddress,
        String podName,
        AuditStatus status,
        String details,
        Map<String, String> metadata
    ) {
        public static AuditEntry create(
            AuditAction action,
            String pluginKey,
            String pluginVersion,
            String userId,
            AuditStatus status,
            String details
        ) {
            return new AuditEntry(
                java.util.UUID.randomUUID().toString(),
                Instant.now(),
                action,
                pluginKey,
                pluginVersion,
                userId,
                getCurrentIpAddress(),
                getPodName(),
                status,
                details,
                Map.of()
            );
        }

        private static String getCurrentIpAddress() {
            try {
                return java.net.InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                return "unknown";
            }
        }

        private static String getPodName() {
            String podName = System.getenv("HOSTNAME");
            return podName != null ? podName : "local";
        }
    }

    /**
     * Audit actions for plugin operations.
     */
    public enum AuditAction {
        PLUGIN_INSTALL,
        PLUGIN_UNINSTALL,
        PLUGIN_ENABLE,
        PLUGIN_DISABLE,
        PLUGIN_UPDATE,
        PLUGIN_SCAN,
        SIGNATURE_VERIFY,
        BEAN_ACCESS_DENIED,
        BEAN_ACCESS_ALLOWED,
        SECURITY_VIOLATION
    }

    /**
     * Status of the audited operation.
     */
    public enum AuditStatus {
        SUCCESS,
        FAILURE,
        WARNING,
        DENIED
    }

    /**
     * Logs a plugin installation event.
     */
    public void logInstall(String pluginKey, String version, String userId, boolean success, String details) {
        if (!securityConfig.isAuditEnabled()) {
            return;
        }

        AuditEntry entry = AuditEntry.create(
            AuditAction.PLUGIN_INSTALL,
            pluginKey,
            version,
            userId,
            success ? AuditStatus.SUCCESS : AuditStatus.FAILURE,
            details
        );

        recordEntry(entry);

        auditLog.info("[PLUGIN_INSTALL] plugin={} version={} user={} status={} details={}",
            pluginKey, version, userId, success ? "SUCCESS" : "FAILURE", details);
    }

    /**
     * Logs a plugin uninstallation event.
     */
    public void logUninstall(String pluginKey, String userId, boolean success, String details) {
        if (!securityConfig.isAuditEnabled()) {
            return;
        }

        AuditEntry entry = AuditEntry.create(
            AuditAction.PLUGIN_UNINSTALL,
            pluginKey,
            null,
            userId,
            success ? AuditStatus.SUCCESS : AuditStatus.FAILURE,
            details
        );

        recordEntry(entry);

        auditLog.info("[PLUGIN_UNINSTALL] plugin={} user={} status={} details={}",
            pluginKey, userId, success ? "SUCCESS" : "FAILURE", details);
    }

    /**
     * Logs a plugin enable event.
     */
    public void logEnable(String pluginKey, String userId, boolean success, String details) {
        if (!securityConfig.isAuditEnabled()) {
            return;
        }

        AuditEntry entry = AuditEntry.create(
            AuditAction.PLUGIN_ENABLE,
            pluginKey,
            null,
            userId,
            success ? AuditStatus.SUCCESS : AuditStatus.FAILURE,
            details
        );

        recordEntry(entry);

        auditLog.info("[PLUGIN_ENABLE] plugin={} user={} status={} details={}",
            pluginKey, userId, success ? "SUCCESS" : "FAILURE", details);
    }

    /**
     * Logs a plugin disable event.
     */
    public void logDisable(String pluginKey, String userId, boolean success, String details) {
        if (!securityConfig.isAuditEnabled()) {
            return;
        }

        AuditEntry entry = AuditEntry.create(
            AuditAction.PLUGIN_DISABLE,
            pluginKey,
            null,
            userId,
            success ? AuditStatus.SUCCESS : AuditStatus.FAILURE,
            details
        );

        recordEntry(entry);

        auditLog.info("[PLUGIN_DISABLE] plugin={} user={} status={} details={}",
            pluginKey, userId, success ? "SUCCESS" : "FAILURE", details);
    }

    /**
     * Logs a plugin update event.
     */
    public void logUpdate(String pluginKey, String oldVersion, String newVersion,
                          String userId, boolean success, String details) {
        if (!securityConfig.isAuditEnabled()) {
            return;
        }

        AuditEntry entry = AuditEntry.create(
            AuditAction.PLUGIN_UPDATE,
            pluginKey,
            newVersion,
            userId,
            success ? AuditStatus.SUCCESS : AuditStatus.FAILURE,
            details + " (from " + oldVersion + " to " + newVersion + ")"
        );

        recordEntry(entry);

        auditLog.info("[PLUGIN_UPDATE] plugin={} oldVersion={} newVersion={} user={} status={} details={}",
            pluginKey, oldVersion, newVersion, userId, success ? "SUCCESS" : "FAILURE", details);
    }

    /**
     * Logs a signature verification event.
     */
    public void logSignatureVerification(String pluginKey, boolean verified, String signerInfo) {
        if (!securityConfig.isAuditEnabled()) {
            return;
        }

        AuditEntry entry = AuditEntry.create(
            AuditAction.SIGNATURE_VERIFY,
            pluginKey,
            null,
            "system",
            verified ? AuditStatus.SUCCESS : AuditStatus.FAILURE,
            signerInfo
        );

        recordEntry(entry);

        if (verified) {
            auditLog.info("[SIGNATURE_VERIFY] plugin={} status=VERIFIED signer={}",
                pluginKey, signerInfo);
        } else {
            auditLog.warn("[SIGNATURE_VERIFY] plugin={} status=FAILED reason={}",
                pluginKey, signerInfo);
        }
    }

    /**
     * Logs a bean access attempt (allowed or denied).
     */
    public void logBeanAccess(String pluginKey, String beanType, boolean allowed) {
        if (!securityConfig.isAuditEnabled()) {
            return;
        }

        AuditEntry entry = AuditEntry.create(
            allowed ? AuditAction.BEAN_ACCESS_ALLOWED : AuditAction.BEAN_ACCESS_DENIED,
            pluginKey,
            null,
            "plugin",
            allowed ? AuditStatus.SUCCESS : AuditStatus.DENIED,
            "Bean type: " + beanType
        );

        recordEntry(entry);

        if (allowed) {
            log.debug("[BEAN_ACCESS] plugin={} bean={} status=ALLOWED", pluginKey, beanType);
        } else {
            auditLog.warn("[BEAN_ACCESS_DENIED] plugin={} bean={} status=DENIED",
                pluginKey, beanType);
        }
    }

    /**
     * Logs a security violation.
     */
    public void logSecurityViolation(String pluginKey, String violation, String details) {
        if (!securityConfig.isAuditEnabled()) {
            return;
        }

        AuditEntry entry = AuditEntry.create(
            AuditAction.SECURITY_VIOLATION,
            pluginKey,
            null,
            "system",
            AuditStatus.DENIED,
            violation + ": " + details
        );

        recordEntry(entry);

        auditLog.error("[SECURITY_VIOLATION] plugin={} violation={} details={}",
            pluginKey, violation, details);
    }

    /**
     * Records an audit entry.
     */
    private void recordEntry(AuditEntry entry) {
        // Add to main list with size limit
        if (auditEntries.size() >= MAX_ENTRIES) {
            // Remove oldest 10%
            int toRemove = MAX_ENTRIES / 10;
            for (int i = 0; i < toRemove && !auditEntries.isEmpty(); i++) {
                auditEntries.remove(0);
            }
        }
        auditEntries.add(entry);

        // Add to per-plugin index
        entriesByPlugin.computeIfAbsent(entry.pluginKey(), k -> new CopyOnWriteArrayList<>())
            .add(entry);
    }

    /**
     * Gets recent audit entries.
     *
     * @param limit maximum entries to return
     * @return list of recent audit entries
     */
    public List<AuditEntry> getRecentEntries(int limit) {
        int size = auditEntries.size();
        int start = Math.max(0, size - limit);
        return new ArrayList<>(auditEntries.subList(start, size));
    }

    /**
     * Gets audit entries for a specific plugin.
     *
     * @param pluginKey the plugin key
     * @return list of audit entries for the plugin
     */
    public List<AuditEntry> getEntriesForPlugin(String pluginKey) {
        return entriesByPlugin.getOrDefault(pluginKey, List.of());
    }

    /**
     * Gets audit entries filtered by action.
     *
     * @param action the action to filter by
     * @param limit maximum entries to return
     * @return filtered list of audit entries
     */
    public List<AuditEntry> getEntriesByAction(AuditAction action, int limit) {
        return auditEntries.stream()
            .filter(e -> e.action() == action)
            .limit(limit)
            .toList();
    }

    /**
     * Gets audit entries filtered by status.
     *
     * @param status the status to filter by
     * @param limit maximum entries to return
     * @return filtered list of audit entries
     */
    public List<AuditEntry> getEntriesByStatus(AuditStatus status, int limit) {
        return auditEntries.stream()
            .filter(e -> e.status() == status)
            .limit(limit)
            .toList();
    }

    /**
     * Gets count of entries by action.
     */
    public Map<AuditAction, Long> getCountsByAction() {
        return auditEntries.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                AuditEntry::action,
                java.util.stream.Collectors.counting()
            ));
    }

    /**
     * Clears all audit entries (for testing).
     */
    public void clearEntries() {
        auditEntries.clear();
        entriesByPlugin.clear();
    }

    /**
     * Gets total number of audit entries.
     */
    public int getTotalEntries() {
        return auditEntries.size();
    }
}
