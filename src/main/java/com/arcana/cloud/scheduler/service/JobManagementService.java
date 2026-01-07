package com.arcana.cloud.scheduler.service;

import com.arcana.cloud.scheduler.dto.JobDetailDto;
import com.arcana.cloud.scheduler.dto.JobScheduleRequest;
import com.arcana.cloud.scheduler.dto.TriggerDetailDto;

import java.util.List;
import java.util.Map;

/**
 * Service interface for managing Quartz scheduled jobs.
 */
public interface JobManagementService {

    /**
     * Schedules a new job with the given configuration.
     *
     * @param request The job schedule request
     * @return The created job details
     */
    JobDetailDto scheduleJob(JobScheduleRequest request);

    /**
     * Reschedules an existing job with new trigger configuration.
     *
     * @param jobName The job name
     * @param jobGroup The job group
     * @param request The new schedule request
     * @return The updated job details
     */
    JobDetailDto rescheduleJob(String jobName, String jobGroup, JobScheduleRequest request);

    /**
     * Gets details of a specific job.
     *
     * @param jobName The job name
     * @param jobGroup The job group
     * @return The job details
     */
    JobDetailDto getJob(String jobName, String jobGroup);

    /**
     * Gets all jobs.
     *
     * @return List of all job details
     */
    List<JobDetailDto> getAllJobs();

    /**
     * Gets all jobs in a specific group.
     *
     * @param jobGroup The job group
     * @return List of job details in the group
     */
    List<JobDetailDto> getJobsByGroup(String jobGroup);

    /**
     * Pauses a job.
     *
     * @param jobName The job name
     * @param jobGroup The job group
     */
    void pauseJob(String jobName, String jobGroup);

    /**
     * Resumes a paused job.
     *
     * @param jobName The job name
     * @param jobGroup The job group
     */
    void resumeJob(String jobName, String jobGroup);

    /**
     * Triggers immediate execution of a job.
     *
     * @param jobName The job name
     * @param jobGroup The job group
     * @param jobData Optional data to pass to the job
     */
    void triggerJob(String jobName, String jobGroup, Map<String, Object> jobData);

    /**
     * Deletes a job and all its triggers.
     *
     * @param jobName The job name
     * @param jobGroup The job group
     * @return true if job was deleted
     */
    boolean deleteJob(String jobName, String jobGroup);

    /**
     * Pauses all jobs in a group.
     *
     * @param jobGroup The job group
     */
    void pauseJobGroup(String jobGroup);

    /**
     * Resumes all jobs in a group.
     *
     * @param jobGroup The job group
     */
    void resumeJobGroup(String jobGroup);

    /**
     * Gets all triggers for a job.
     *
     * @param jobName The job name
     * @param jobGroup The job group
     * @return List of trigger details
     */
    List<TriggerDetailDto> getJobTriggers(String jobName, String jobGroup);

    /**
     * Checks if a job exists.
     *
     * @param jobName The job name
     * @param jobGroup The job group
     * @return true if job exists
     */
    boolean jobExists(String jobName, String jobGroup);

    /**
     * Gets the scheduler status.
     *
     * @return Scheduler status information
     */
    SchedulerStatus getSchedulerStatus();

    /**
     * Starts the scheduler.
     */
    void startScheduler();

    /**
     * Pauses the scheduler (standby mode).
     */
    void pauseScheduler();

    /**
     * Shuts down the scheduler.
     *
     * @param waitForJobsToComplete Whether to wait for running jobs to complete
     */
    void shutdownScheduler(boolean waitForJobsToComplete);

    /**
     * Gets currently executing jobs.
     *
     * @return List of currently executing job details
     */
    List<JobDetailDto> getCurrentlyExecutingJobs();

    /**
     * Scheduler status information.
     */
    record SchedulerStatus(
            String schedulerName,
            String instanceId,
            boolean isStarted,
            boolean isInStandbyMode,
            boolean isShutdown,
            int threadPoolSize,
            String version
    ) {}
}
