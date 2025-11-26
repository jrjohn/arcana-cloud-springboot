package com.arcana.plugin.audit;

import com.arcana.plugin.audit.api.AuditService;
import com.arcana.cloud.plugin.extension.ScheduledJobExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduled job for cleaning up old audit entries.
 *
 * <p>Runs daily at 2 AM to delete audit entries older than the configured
 * retention period (default 90 days).</p>
 */
@ScheduledJobExtension(
    key = "audit-cleanup",
    cron = "0 0 2 * * ?",
    description = "Clean up old audit logs (runs daily at 2 AM)"
)
public class AuditCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AuditCleanupJob.class);

    private final AuditService auditService;
    private final int retentionDays;

    /**
     * Creates a new audit cleanup job.
     *
     * @param auditService the audit service
     * @param retentionDays number of days to retain audit logs
     */
    public AuditCleanupJob(AuditService auditService, int retentionDays) {
        this.auditService = auditService;
        this.retentionDays = retentionDays;
    }

    /**
     * Creates a new audit cleanup job with default retention.
     *
     * @param auditService the audit service
     */
    public AuditCleanupJob(AuditService auditService) {
        this(auditService, 90);
    }

    /**
     * Executes the cleanup job.
     */
    public void execute() {
        log.info("Starting audit cleanup job. Retention period: {} days", retentionDays);

        try {
            long startTime = System.currentTimeMillis();
            int deletedCount = auditService.deleteOlderThan(retentionDays);
            long duration = System.currentTimeMillis() - startTime;

            log.info("Audit cleanup completed. Deleted {} entries in {} ms",
                deletedCount, duration);

        } catch (Exception e) {
            log.error("Audit cleanup job failed", e);
        }
    }

    /**
     * Returns the retention period in days.
     *
     * @return retention days
     */
    public int getRetentionDays() {
        return retentionDays;
    }
}
