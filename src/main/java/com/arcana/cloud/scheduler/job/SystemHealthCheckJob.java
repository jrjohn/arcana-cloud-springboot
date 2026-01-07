package com.arcana.cloud.scheduler.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Scheduled job to perform system health checks.
 * Useful for:
 * - Periodic health monitoring in clustered deployments
 * - Generating alerts based on health status
 * - Recording health metrics over time
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "quartz.enabled", havingValue = "true", matchIfMissing = false)
public class SystemHealthCheckJob extends BaseJob {

    @Value("${deployment.mode:monolithic}")
    private String deploymentMode;

    @Override
    protected void doExecute(JobExecutionContext context) throws Exception {
        String instanceId = context.getScheduler().getSchedulerInstanceId();
        log.info("Running system health check on instance: {} (mode: {})", instanceId, deploymentMode);

        // Check JVM metrics
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        double memoryUsagePercent = (double) usedMemory / totalMemory * 100;

        log.info("JVM Memory - Used: {}MB, Free: {}MB, Total: {}MB, Max: {}MB, Usage: {:.1f}%",
                usedMemory / (1024 * 1024),
                freeMemory / (1024 * 1024),
                totalMemory / (1024 * 1024),
                maxMemory / (1024 * 1024),
                memoryUsagePercent);

        // Check available processors
        int availableProcessors = runtime.availableProcessors();
        log.info("Available processors: {}", availableProcessors);

        // Store metrics in job data for tracking
        context.getJobDetail().getJobDataMap().put("memoryUsagePercent", memoryUsagePercent);
        context.getJobDetail().getJobDataMap().put("usedMemoryMB", usedMemory / (1024 * 1024));
        context.getJobDetail().getJobDataMap().put("freeMemoryMB", freeMemory / (1024 * 1024));
        context.getJobDetail().getJobDataMap().put("maxMemoryMB", maxMemory / (1024 * 1024));
        context.getJobDetail().getJobDataMap().put("availableProcessors", availableProcessors);
        context.getJobDetail().getJobDataMap().put("lastCheckTime", System.currentTimeMillis());

        // Memory warning threshold (e.g., 85%)
        if (memoryUsagePercent > 85.0) {
            log.warn("High memory usage detected: {:.1f}%", memoryUsagePercent);
        }

        incrementCounter(context.getJobDetail().getJobDataMap(), "executionCount");
        recordLastExecution(context.getJobDetail().getJobDataMap());
    }
}
