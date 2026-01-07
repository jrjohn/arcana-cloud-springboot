package com.arcana.cloud.scheduler.listener;

import com.arcana.cloud.entity.JobExecutionHistory;
import com.arcana.cloud.entity.JobExecutionHistory.JobExecutionStatus;
import com.arcana.cloud.scheduler.service.JobHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Quartz Job Listener that tracks job execution history.
 * Records start time, completion time, duration, and status for all jobs.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "quartz.enabled", havingValue = "true", matchIfMissing = false)
public class JobExecutionHistoryListener implements JobListener {

    private final JobHistoryService jobHistoryService;

    private static final String LISTENER_NAME = "JobExecutionHistoryListener";
    private static final String START_TIME_KEY = "_startTime";
    private static final String HISTORY_ID_KEY = "_historyId";

    @Override
    public String getName() {
        return LISTENER_NAME;
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        String jobName = context.getJobDetail().getKey().getName();
        String jobGroup = context.getJobDetail().getKey().getGroup();
        String triggerName = context.getTrigger().getKey().getName();
        String triggerGroup = context.getTrigger().getKey().getGroup();
        String instanceName = getInstanceName(context);

        log.debug("Job to be executed: {}.{}", jobGroup, jobName);

        // Record start time in context for duration calculation
        context.put(START_TIME_KEY, System.currentTimeMillis());

        // Create initial history record
        try {
            JobExecutionHistory history = JobExecutionHistory.builder()
                    .jobName(jobName)
                    .jobGroup(jobGroup)
                    .triggerName(triggerName)
                    .triggerGroup(triggerGroup)
                    .instanceName(instanceName)
                    .firedTime(LocalDateTime.now())
                    .status(JobExecutionStatus.STARTED)
                    .build();

            Long historyId = jobHistoryService.recordJobStart(history);
            context.put(HISTORY_ID_KEY, historyId);
        } catch (Exception e) {
            log.warn("Failed to record job start history: {}", e.getMessage());
        }
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        String jobName = context.getJobDetail().getKey().getName();
        String jobGroup = context.getJobDetail().getKey().getGroup();

        log.warn("Job execution vetoed: {}.{}", jobGroup, jobName);

        try {
            Long historyId = (Long) context.get(HISTORY_ID_KEY);
            if (historyId != null) {
                long startTime = (Long) context.get(START_TIME_KEY);
                long duration = System.currentTimeMillis() - startTime;

                jobHistoryService.recordJobCompletion(
                        historyId,
                        LocalDateTime.now(),
                        duration,
                        JobExecutionStatus.VETOED,
                        "Job execution was vetoed"
                );
            }
        } catch (Exception e) {
            log.warn("Failed to record job veto history: {}", e.getMessage());
        }
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        String jobName = context.getJobDetail().getKey().getName();
        String jobGroup = context.getJobDetail().getKey().getGroup();

        try {
            Long historyId = (Long) context.get(HISTORY_ID_KEY);
            Long startTime = (Long) context.get(START_TIME_KEY);

            if (historyId != null && startTime != null) {
                long duration = System.currentTimeMillis() - startTime;
                JobExecutionStatus status;
                String errorMessage = null;

                if (jobException != null) {
                    status = JobExecutionStatus.FAILED;
                    errorMessage = jobException.getMessage();
                    if (jobException.getCause() != null) {
                        errorMessage = errorMessage + " - Caused by: " + jobException.getCause().getMessage();
                    }
                    log.error("Job failed: {}.{} - {}", jobGroup, jobName, errorMessage);
                } else {
                    status = JobExecutionStatus.COMPLETED;
                    log.debug("Job completed: {}.{} in {}ms", jobGroup, jobName, duration);
                }

                jobHistoryService.recordJobCompletion(
                        historyId,
                        LocalDateTime.now(),
                        duration,
                        status,
                        errorMessage
                );
            }
        } catch (Exception e) {
            log.warn("Failed to record job completion history: {}", e.getMessage());
        }
    }

    private String getInstanceName(JobExecutionContext context) {
        try {
            return context.getScheduler().getSchedulerInstanceId();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
