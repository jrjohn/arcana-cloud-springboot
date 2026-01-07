package com.arcana.cloud.scheduler;

import com.arcana.cloud.scheduler.job.SystemHealthCheckJob;
import com.arcana.cloud.scheduler.job.TokenCleanupJob;
import com.arcana.cloud.scheduler.listener.JobExecutionHistoryListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Initializes the Quartz scheduler with listeners and default jobs.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "quartz.enabled", havingValue = "true", matchIfMissing = false)
public class JobInitializer {

    private final Scheduler scheduler;
    private final JobExecutionHistoryListener historyListener;

    @Value("${quartz.jobs.auto-register:true}")
    private boolean autoRegisterJobs;

    @Value("${deployment.mode:monolithic}")
    private String deploymentMode;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            // Register the job execution history listener
            scheduler.getListenerManager().addJobListener(historyListener);
            log.info("Registered JobExecutionHistoryListener");

            // Optionally register default jobs
            if (autoRegisterJobs) {
                registerDefaultJobs();
            }

            log.info("Quartz scheduler initialized successfully in {} mode", deploymentMode);

        } catch (SchedulerException e) {
            log.error("Failed to initialize Quartz scheduler", e);
        }
    }

    private void registerDefaultJobs() throws SchedulerException {
        // Register Token Cleanup Job (runs daily at 2 AM)
        registerJobIfNotExists(
                TokenCleanupJob.class,
                "tokenCleanup",
                "maintenance",
                "Cleans up expired OAuth tokens",
                "0 0 2 * * ?", // Daily at 2 AM
                builder -> builder.usingJobData("retentionDays", 30)
        );

        // Register System Health Check Job (runs every 5 minutes)
        registerJobIfNotExists(
                SystemHealthCheckJob.class,
                "systemHealthCheck",
                "monitoring",
                "Performs system health checks",
                "0 0/5 * * * ?", // Every 5 minutes
                null
        );

        log.info("Default jobs registered");
    }

    private void registerJobIfNotExists(
            Class<? extends Job> jobClass,
            String jobName,
            String jobGroup,
            String description,
            String cronExpression,
            JobDataCustomizer dataCustomizer) throws SchedulerException {

        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);

        if (scheduler.checkExists(jobKey)) {
            log.debug("Job already exists: {}.{}", jobGroup, jobName);
            return;
        }

        // Build job detail
        JobBuilder jobBuilder = JobBuilder.newJob(jobClass)
                .withIdentity(jobKey)
                .withDescription(description)
                .storeDurably(true)
                .requestRecovery(true);

        if (dataCustomizer != null) {
            dataCustomizer.customize(jobBuilder);
        }

        JobDetail jobDetail = jobBuilder.build();

        // Build trigger
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobName + "Trigger", jobGroup)
                .withDescription("Trigger for " + description)
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression)
                        .withMisfireHandlingInstructionFireAndProceed())
                .forJob(jobKey)
                .build();

        // Schedule the job
        scheduler.scheduleJob(jobDetail, trigger);
        log.info("Registered job: {}.{} with cron: {}", jobGroup, jobName, cronExpression);
    }

    @FunctionalInterface
    interface JobDataCustomizer {
        void customize(JobBuilder builder);
    }
}
