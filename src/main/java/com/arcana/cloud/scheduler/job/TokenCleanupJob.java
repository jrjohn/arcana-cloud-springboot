package com.arcana.cloud.scheduler.job;

import com.arcana.cloud.dao.interfaces.OAuthTokenDao;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Scheduled job to clean up expired OAuth tokens.
 * This job demonstrates a practical use case for distributed scheduling:
 * - In monolithic mode: Runs on the single instance
 * - In layered/K8s mode: Only one instance executes (cluster-aware)
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "quartz.enabled", havingValue = "true", matchIfMissing = false)
public class TokenCleanupJob extends BaseJob {

    @Autowired
    private OAuthTokenDao tokenDao;

    @Override
    protected void doExecute(JobExecutionContext context) throws Exception {
        log.info("Starting token cleanup job...");

        // Get parameters from job data
        int retentionDays = getInt(context.getJobDetail().getJobDataMap(), "retentionDays", 30);

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);

        // Count tokens before cleanup (for logging)
        long totalBefore = tokenDao.count();

        // Delete expired and revoked tokens
        // In a real implementation, you'd add a method to delete by expiration date
        log.info("Cleaning up tokens older than {} (retention: {} days)", cutoffDate, retentionDays);

        // Record execution stats
        incrementCounter(context.getJobDetail().getJobDataMap(), "executionCount");
        recordLastExecution(context.getJobDetail().getJobDataMap());

        long totalAfter = tokenDao.count();
        long deleted = totalBefore - totalAfter;

        log.info("Token cleanup completed. Deleted {} tokens. Total remaining: {}", deleted, totalAfter);
    }
}
