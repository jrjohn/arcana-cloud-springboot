package com.arcana.cloud.scheduler.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;

/**
 * Base class for all scheduled jobs.
 * Provides common functionality like logging, error handling, and job data access.
 *
 * Features:
 * - DisallowConcurrentExecution: Prevents same job from running concurrently on same instance
 * - PersistJobDataAfterExecution: Persists job data changes after execution
 */
@Slf4j
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public abstract class BaseJob implements Job {

    /**
     * Template method for job execution.
     * Handles common concerns like logging and error handling.
     */
    @Override
    public final void execute(JobExecutionContext context) throws JobExecutionException {
        String jobName = context.getJobDetail().getKey().getName();
        String jobGroup = context.getJobDetail().getKey().getGroup();
        String instanceId = getInstanceId(context);

        log.info("Job started: {}.{} on instance: {}", jobGroup, jobName, instanceId);
        long startTime = System.currentTimeMillis();

        try {
            // Execute the actual job logic
            doExecute(context);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Job completed: {}.{} in {}ms", jobGroup, jobName, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Job failed: {}.{} after {}ms - {}", jobGroup, jobName, duration, e.getMessage(), e);

            // Wrap in JobExecutionException for Quartz to handle
            JobExecutionException jee = new JobExecutionException(e);

            // Configure retry behavior based on job settings
            if (shouldRetryOnFailure(context)) {
                jee.setRefireImmediately(false); // Don't refire immediately
                jee.setUnscheduleAllTriggers(false);
            }

            throw jee;
        }
    }

    /**
     * Implement this method with the actual job logic.
     *
     * @param context The job execution context
     * @throws Exception if job execution fails
     */
    protected abstract void doExecute(JobExecutionContext context) throws Exception;

    /**
     * Override to customize retry behavior on failure.
     * Default is true - jobs will be retried according to trigger configuration.
     */
    protected boolean shouldRetryOnFailure(JobExecutionContext context) {
        return true;
    }

    /**
     * Helper method to get a required string parameter from job data.
     */
    protected String getRequiredString(JobDataMap dataMap, String key) throws JobExecutionException {
        String value = dataMap.getString(key);
        if (value == null || value.isEmpty()) {
            throw new JobExecutionException("Required job parameter missing: " + key);
        }
        return value;
    }

    /**
     * Helper method to get an optional string parameter from job data.
     */
    protected String getOptionalString(JobDataMap dataMap, String key, String defaultValue) {
        String value = dataMap.getString(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    /**
     * Helper method to get a long parameter from job data.
     */
    protected long getLong(JobDataMap dataMap, String key, long defaultValue) {
        if (dataMap.containsKey(key)) {
            return dataMap.getLong(key);
        }
        return defaultValue;
    }

    /**
     * Helper method to get an int parameter from job data.
     */
    protected int getInt(JobDataMap dataMap, String key, int defaultValue) {
        if (dataMap.containsKey(key)) {
            return dataMap.getInt(key);
        }
        return defaultValue;
    }

    /**
     * Helper method to get a boolean parameter from job data.
     */
    protected boolean getBoolean(JobDataMap dataMap, String key, boolean defaultValue) {
        if (dataMap.containsKey(key)) {
            return dataMap.getBoolean(key);
        }
        return defaultValue;
    }

    /**
     * Helper method to increment a counter in job data.
     * Useful for tracking execution counts.
     */
    protected void incrementCounter(JobDataMap dataMap, String key) {
        int count = getInt(dataMap, key, 0);
        dataMap.put(key, count + 1);
    }

    /**
     * Helper method to store the last execution time in job data.
     */
    protected void recordLastExecution(JobDataMap dataMap) {
        dataMap.put("lastExecutionTime", System.currentTimeMillis());
    }

    /**
     * Helper method to get the scheduler instance ID safely.
     */
    private String getInstanceId(JobExecutionContext context) {
        try {
            return context.getScheduler().getSchedulerInstanceId();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
