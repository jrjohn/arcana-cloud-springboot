package com.arcana.cloud.scheduler.service.impl;

import com.arcana.cloud.scheduler.dto.JobDetailDto;
import com.arcana.cloud.scheduler.dto.JobDetailDto.JobStatus;
import com.arcana.cloud.scheduler.dto.JobScheduleRequest;
import com.arcana.cloud.scheduler.dto.JobScheduleRequest.TriggerConfig;
import com.arcana.cloud.scheduler.dto.TriggerDetailDto;
import com.arcana.cloud.scheduler.dto.TriggerDetailDto.TriggerType;
import com.arcana.cloud.scheduler.service.JobManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of JobManagementService.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "quartz.enabled", havingValue = "true", matchIfMissing = false)
public class JobManagementServiceImpl implements JobManagementService {

    private final Scheduler scheduler;

    @Override
    public JobDetailDto scheduleJob(JobScheduleRequest request) {
        try {
            // Build job detail
            Class<? extends Job> jobClass = getJobClass(request.getJobClassName());

            JobDetail jobDetail = JobBuilder.newJob(jobClass)
                    .withIdentity(request.getJobName(), request.getJobGroup())
                    .withDescription(request.getDescription())
                    .storeDurably(request.isDurable())
                    .requestRecovery(request.isRequestsRecovery())
                    .build();

            // Add job data if provided
            if (request.getJobData() != null) {
                jobDetail.getJobDataMap().putAll(request.getJobData());
            }

            // Build trigger
            Trigger trigger = buildTrigger(request.getTrigger(), request.getJobName(), request.getJobGroup());

            // Schedule the job
            scheduler.scheduleJob(jobDetail, trigger);

            log.info("Scheduled job: {}.{} with trigger: {}",
                    request.getJobGroup(), request.getJobName(), trigger.getKey());

            return getJob(request.getJobName(), request.getJobGroup());

        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Job class not found: " + request.getJobClassName(), e);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to schedule job: " + e.getMessage(), e);
        }
    }

    @Override
    public JobDetailDto rescheduleJob(String jobName, String jobGroup, JobScheduleRequest request) {
        try {
            JobKey jobKey = JobKey.jobKey(jobName, jobGroup);

            if (!scheduler.checkExists(jobKey)) {
                throw new IllegalArgumentException("Job not found: " + jobGroup + "." + jobName);
            }

            // Get existing triggers
            List<? extends Trigger> existingTriggers = scheduler.getTriggersOfJob(jobKey);

            // Build new trigger
            Trigger newTrigger = buildTrigger(request.getTrigger(), jobName, jobGroup);

            // Reschedule with new trigger (replace first trigger)
            if (!existingTriggers.isEmpty()) {
                scheduler.rescheduleJob(existingTriggers.get(0).getKey(), newTrigger);
            } else {
                scheduler.scheduleJob(newTrigger);
            }

            log.info("Rescheduled job: {}.{}", jobGroup, jobName);

            return getJob(jobName, jobGroup);

        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to reschedule job: " + e.getMessage(), e);
        }
    }

    @Override
    public JobDetailDto getJob(String jobName, String jobGroup) {
        try {
            JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
            JobDetail jobDetail = scheduler.getJobDetail(jobKey);

            if (jobDetail == null) {
                throw new IllegalArgumentException("Job not found: " + jobGroup + "." + jobName);
            }

            return mapToJobDetailDto(jobDetail);

        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to get job: " + e.getMessage(), e);
        }
    }

    @Override
    public List<JobDetailDto> getAllJobs() {
        try {
            List<JobDetailDto> jobs = new ArrayList<>();

            for (String groupName : scheduler.getJobGroupNames()) {
                for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
                    JobDetail jobDetail = scheduler.getJobDetail(jobKey);
                    if (jobDetail != null) {
                        jobs.add(mapToJobDetailDto(jobDetail));
                    }
                }
            }

            return jobs;

        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to get all jobs: " + e.getMessage(), e);
        }
    }

    @Override
    public List<JobDetailDto> getJobsByGroup(String jobGroup) {
        try {
            List<JobDetailDto> jobs = new ArrayList<>();

            for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(jobGroup))) {
                JobDetail jobDetail = scheduler.getJobDetail(jobKey);
                if (jobDetail != null) {
                    jobs.add(mapToJobDetailDto(jobDetail));
                }
            }

            return jobs;

        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to get jobs by group: " + e.getMessage(), e);
        }
    }

    @Override
    public void pauseJob(String jobName, String jobGroup) {
        try {
            scheduler.pauseJob(JobKey.jobKey(jobName, jobGroup));
            log.info("Paused job: {}.{}", jobGroup, jobName);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to pause job: " + e.getMessage(), e);
        }
    }

    @Override
    public void resumeJob(String jobName, String jobGroup) {
        try {
            scheduler.resumeJob(JobKey.jobKey(jobName, jobGroup));
            log.info("Resumed job: {}.{}", jobGroup, jobName);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to resume job: " + e.getMessage(), e);
        }
    }

    @Override
    public void triggerJob(String jobName, String jobGroup, Map<String, Object> jobData) {
        try {
            JobDataMap dataMap = new JobDataMap();
            if (jobData != null) {
                dataMap.putAll(jobData);
            }

            scheduler.triggerJob(JobKey.jobKey(jobName, jobGroup), dataMap);
            log.info("Triggered job: {}.{}", jobGroup, jobName);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to trigger job: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteJob(String jobName, String jobGroup) {
        try {
            boolean deleted = scheduler.deleteJob(JobKey.jobKey(jobName, jobGroup));
            if (deleted) {
                log.info("Deleted job: {}.{}", jobGroup, jobName);
            }
            return deleted;
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to delete job: " + e.getMessage(), e);
        }
    }

    @Override
    public void pauseJobGroup(String jobGroup) {
        try {
            scheduler.pauseJobs(GroupMatcher.jobGroupEquals(jobGroup));
            log.info("Paused job group: {}", jobGroup);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to pause job group: " + e.getMessage(), e);
        }
    }

    @Override
    public void resumeJobGroup(String jobGroup) {
        try {
            scheduler.resumeJobs(GroupMatcher.jobGroupEquals(jobGroup));
            log.info("Resumed job group: {}", jobGroup);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to resume job group: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TriggerDetailDto> getJobTriggers(String jobName, String jobGroup) {
        try {
            List<? extends Trigger> triggers = scheduler.getTriggersOfJob(JobKey.jobKey(jobName, jobGroup));
            return triggers.stream()
                    .map(this::mapToTriggerDetailDto)
                    .collect(Collectors.toList());
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to get job triggers: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean jobExists(String jobName, String jobGroup) {
        try {
            return scheduler.checkExists(JobKey.jobKey(jobName, jobGroup));
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to check job existence: " + e.getMessage(), e);
        }
    }

    @Override
    public SchedulerStatus getSchedulerStatus() {
        try {
            SchedulerMetaData metaData = scheduler.getMetaData();
            return new SchedulerStatus(
                    metaData.getSchedulerName(),
                    metaData.getSchedulerInstanceId(),
                    metaData.isStarted(),
                    metaData.isInStandbyMode(),
                    metaData.isShutdown(),
                    metaData.getThreadPoolSize(),
                    metaData.getVersion()
            );
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to get scheduler status: " + e.getMessage(), e);
        }
    }

    @Override
    public void startScheduler() {
        try {
            scheduler.start();
            log.info("Scheduler started");
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to start scheduler: " + e.getMessage(), e);
        }
    }

    @Override
    public void pauseScheduler() {
        try {
            scheduler.standby();
            log.info("Scheduler paused (standby mode)");
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to pause scheduler: " + e.getMessage(), e);
        }
    }

    @Override
    public void shutdownScheduler(boolean waitForJobsToComplete) {
        try {
            scheduler.shutdown(waitForJobsToComplete);
            log.info("Scheduler shutdown (waitForJobs={})", waitForJobsToComplete);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to shutdown scheduler: " + e.getMessage(), e);
        }
    }

    @Override
    public List<JobDetailDto> getCurrentlyExecutingJobs() {
        try {
            return scheduler.getCurrentlyExecutingJobs().stream()
                    .map(context -> {
                        try {
                            return mapToJobDetailDto(context.getJobDetail());
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to get currently executing jobs: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Job> getJobClass(String className) throws ClassNotFoundException {
        return (Class<? extends Job>) Class.forName(className);
    }

    private Trigger buildTrigger(TriggerConfig config, String jobName, String jobGroup) {
        String triggerName = config.getTriggerName() != null ? config.getTriggerName() : jobName + "Trigger";
        String triggerGroup = config.getTriggerGroup() != null ? config.getTriggerGroup() : jobGroup;

        TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger()
                .withIdentity(triggerName, triggerGroup)
                .withDescription(config.getDescription())
                .withPriority(config.getPriority())
                .forJob(jobName, jobGroup);

        // Set start time
        if (config.getStartTime() != null) {
            triggerBuilder.startAt(Date.from(config.getStartTime()
                    .atZone(ZoneId.systemDefault()).toInstant()));
        } else {
            triggerBuilder.startNow();
        }

        // Set end time
        if (config.getEndTime() != null) {
            triggerBuilder.endAt(Date.from(config.getEndTime()
                    .atZone(ZoneId.systemDefault()).toInstant()));
        }

        // Build schedule based on trigger type
        if (config.getTriggerType() == TriggerConfig.TriggerType.CRON) {
            CronScheduleBuilder cronBuilder = CronScheduleBuilder.cronSchedule(config.getCronExpression());

            if (config.getTimeZone() != null) {
                cronBuilder.inTimeZone(TimeZone.getTimeZone(config.getTimeZone()));
            }

            // Set misfire instruction
            switch (config.getMisfireInstruction()) {
                case IGNORE_MISFIRE_POLICY -> cronBuilder.withMisfireHandlingInstructionIgnoreMisfires();
                case FIRE_NOW -> cronBuilder.withMisfireHandlingInstructionFireAndProceed();
                case DO_NOTHING -> cronBuilder.withMisfireHandlingInstructionDoNothing();
                default -> {} // SMART_POLICY - use default
            }

            triggerBuilder.withSchedule(cronBuilder);
        } else {
            SimpleScheduleBuilder simpleBuilder = SimpleScheduleBuilder.simpleSchedule();

            if (config.getRepeatIntervalMs() != null) {
                simpleBuilder.withIntervalInMilliseconds(config.getRepeatIntervalMs());
            }

            if (config.getRepeatCount() < 0) {
                simpleBuilder.repeatForever();
            } else {
                simpleBuilder.withRepeatCount(config.getRepeatCount());
            }

            // Set misfire instruction
            switch (config.getMisfireInstruction()) {
                case IGNORE_MISFIRE_POLICY -> simpleBuilder.withMisfireHandlingInstructionIgnoreMisfires();
                case FIRE_NOW -> simpleBuilder.withMisfireHandlingInstructionFireNow();
                case DO_NOTHING -> simpleBuilder.withMisfireHandlingInstructionNextWithRemainingCount();
                default -> {} // SMART_POLICY - use default
            }

            triggerBuilder.withSchedule(simpleBuilder);
        }

        return triggerBuilder.build();
    }

    private JobDetailDto mapToJobDetailDto(JobDetail jobDetail) throws SchedulerException {
        JobKey key = jobDetail.getKey();

        // Get triggers
        List<TriggerDetailDto> triggers = scheduler.getTriggersOfJob(key).stream()
                .map(this::mapToTriggerDetailDto)
                .collect(Collectors.toList());

        // Determine job status
        JobStatus status = determineJobStatus(key);

        return JobDetailDto.builder()
                .jobName(key.getName())
                .jobGroup(key.getGroup())
                .description(jobDetail.getDescription())
                .jobClassName(jobDetail.getJobClass().getName())
                .isDurable(jobDetail.isDurable())
                .requestsRecovery(jobDetail.requestsRecovery())
                .jobData(new HashMap<>(jobDetail.getJobDataMap()))
                .triggers(triggers)
                .status(status)
                .build();
    }

    private JobStatus determineJobStatus(JobKey jobKey) throws SchedulerException {
        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);

        if (triggers.isEmpty()) {
            return JobStatus.NONE;
        }

        boolean allPaused = true;
        boolean hasError = false;
        boolean hasBlocked = false;

        for (Trigger trigger : triggers) {
            Trigger.TriggerState state = scheduler.getTriggerState(trigger.getKey());
            switch (state) {
                case ERROR -> hasError = true;
                case BLOCKED -> hasBlocked = true;
                case PAUSED -> {}
                default -> allPaused = false;
            }
        }

        if (hasError) return JobStatus.ERROR;
        if (hasBlocked) return JobStatus.BLOCKED;
        if (allPaused) return JobStatus.PAUSED;
        return JobStatus.NORMAL;
    }

    private TriggerDetailDto mapToTriggerDetailDto(Trigger trigger) {
        TriggerDetailDto.TriggerDetailDtoBuilder builder = TriggerDetailDto.builder()
                .triggerName(trigger.getKey().getName())
                .triggerGroup(trigger.getKey().getGroup())
                .description(trigger.getDescription())
                .priority(trigger.getPriority())
                .calendarName(trigger.getCalendarName())
                .misfireInstruction(trigger.getMisfireInstruction());

        // Set times
        if (trigger.getStartTime() != null) {
            builder.startTime(LocalDateTime.ofInstant(
                    trigger.getStartTime().toInstant(), ZoneId.systemDefault()));
        }
        if (trigger.getEndTime() != null) {
            builder.endTime(LocalDateTime.ofInstant(
                    trigger.getEndTime().toInstant(), ZoneId.systemDefault()));
        }
        if (trigger.getPreviousFireTime() != null) {
            builder.previousFireTime(LocalDateTime.ofInstant(
                    trigger.getPreviousFireTime().toInstant(), ZoneId.systemDefault()));
        }
        if (trigger.getNextFireTime() != null) {
            builder.nextFireTime(LocalDateTime.ofInstant(
                    trigger.getNextFireTime().toInstant(), ZoneId.systemDefault()));
        }

        // Set trigger type specific fields
        if (trigger instanceof CronTrigger cronTrigger) {
            builder.triggerType(TriggerType.CRON)
                    .cronExpression(cronTrigger.getCronExpression())
                    .timeZone(cronTrigger.getTimeZone().getID());
        } else if (trigger instanceof SimpleTrigger simpleTrigger) {
            builder.triggerType(TriggerType.SIMPLE)
                    .repeatCount(simpleTrigger.getRepeatCount())
                    .repeatIntervalMs(simpleTrigger.getRepeatInterval())
                    .timesTriggered(simpleTrigger.getTimesTriggered());
        }

        // Get trigger state
        try {
            Trigger.TriggerState state = scheduler.getTriggerState(trigger.getKey());
            builder.state(mapTriggerState(state));
        } catch (SchedulerException e) {
            builder.state(TriggerDetailDto.TriggerState.ERROR);
        }

        return builder.build();
    }

    private TriggerDetailDto.TriggerState mapTriggerState(Trigger.TriggerState state) {
        return switch (state) {
            case NONE -> TriggerDetailDto.TriggerState.NONE;
            case NORMAL -> TriggerDetailDto.TriggerState.NORMAL;
            case PAUSED -> TriggerDetailDto.TriggerState.PAUSED;
            case COMPLETE -> TriggerDetailDto.TriggerState.COMPLETE;
            case ERROR -> TriggerDetailDto.TriggerState.ERROR;
            case BLOCKED -> TriggerDetailDto.TriggerState.BLOCKED;
        };
    }
}
