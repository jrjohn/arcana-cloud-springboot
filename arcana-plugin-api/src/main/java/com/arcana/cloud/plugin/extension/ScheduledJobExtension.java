package com.arcana.cloud.plugin.extension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Extension point for scheduled background jobs.
 *
 * <p>Scheduled jobs run periodically based on cron expressions or fixed intervals.
 * They're useful for cleanup tasks, report generation, synchronization, and
 * other background processing.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @ScheduledJobExtension(
 *     key = "audit-cleanup",
 *     cron = "0 0 2 * * ?",
 *     description = "Clean up old audit logs"
 * )
 * public class AuditCleanupJob implements ScheduledJob {
 *
 *     @Override
 *     public void execute(JobContext context) {
 *         int deleted = auditRepository.deleteOlderThan(30);
 *         context.log("Deleted " + deleted + " old audit entries");
 *     }
 * }
 * }</pre>
 *
 * <p>In arcana-plugin.xml:</p>
 * <pre>{@code
 * <scheduled-job key="audit-cleanup"
 *                class="com.example.audit.AuditCleanupJob"
 *                cron="0 0 2 * * ?">
 *     <description>Clean up old audit logs</description>
 *     <enabled>true</enabled>
 * </scheduled-job>
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtensionPoint(type = "scheduled-job", description = "Scheduled background job")
public @interface ScheduledJobExtension {

    /**
     * Unique key for this extension within the plugin.
     *
     * @return the extension key
     */
    String key();

    /**
     * Cron expression for scheduling.
     * Uses Spring cron format: second minute hour day-of-month month day-of-week
     *
     * @return the cron expression
     */
    String cron() default "";

    /**
     * Fixed delay in milliseconds between job completions.
     * Mutually exclusive with cron() and fixedRate().
     *
     * @return the fixed delay in ms
     */
    long fixedDelay() default -1;

    /**
     * Fixed rate in milliseconds between job starts.
     * Mutually exclusive with cron() and fixedDelay().
     *
     * @return the fixed rate in ms
     */
    long fixedRate() default -1;

    /**
     * Initial delay before first execution in milliseconds.
     *
     * @return the initial delay
     */
    long initialDelay() default 0;

    /**
     * Description of this scheduled job.
     *
     * @return the description
     */
    String description() default "";

    /**
     * Whether the job is enabled by default.
     *
     * @return true if enabled
     */
    boolean enabled() default true;

    /**
     * Whether to allow concurrent executions.
     *
     * @return true if concurrent executions allowed
     */
    boolean concurrent() default false;
}

/**
 * Interface for scheduled job implementations.
 */
interface ScheduledJob {

    /**
     * Executes the scheduled job.
     *
     * @param context the job execution context
     */
    void execute(JobContext context);

    /**
     * Job execution context.
     */
    interface JobContext {
        /**
         * Returns the job key.
         * @return the key
         */
        String getJobKey();

        /**
         * Returns the plugin key.
         * @return the plugin key
         */
        String getPluginKey();

        /**
         * Returns the scheduled execution time.
         * @return the execution time
         */
        java.time.Instant getScheduledTime();

        /**
         * Logs an info message.
         * @param message the message
         */
        void log(String message);

        /**
         * Logs an error.
         * @param message the message
         * @param throwable the exception
         */
        void logError(String message, Throwable throwable);

        /**
         * Checks if cancellation was requested.
         * @return true if cancelled
         */
        boolean isCancelled();
    }
}
