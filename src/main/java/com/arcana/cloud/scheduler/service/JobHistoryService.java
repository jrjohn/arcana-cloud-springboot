package com.arcana.cloud.scheduler.service;

import com.arcana.cloud.entity.JobExecutionHistory;
import com.arcana.cloud.entity.JobExecutionHistory.JobExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing job execution history.
 */
public interface JobHistoryService {

    /**
     * Records the start of a job execution.
     *
     * @param history The history record with initial data
     * @return The ID of the created history record
     */
    Long recordJobStart(JobExecutionHistory history);

    /**
     * Records the completion of a job execution.
     *
     * @param historyId The ID of the history record to update
     * @param completedTime The time the job completed
     * @param executionTimeMs The duration of execution in milliseconds
     * @param status The final status of the job
     * @param errorMessage Optional error message if failed
     */
    void recordJobCompletion(Long historyId, LocalDateTime completedTime,
                            Long executionTimeMs, JobExecutionStatus status,
                            String errorMessage);

    /**
     * Gets execution history for a specific job.
     *
     * @param jobName The job name
     * @param jobGroup The job group
     * @param pageable Pagination parameters
     * @return Page of execution history records
     */
    Page<JobExecutionHistory> getJobHistory(String jobName, String jobGroup, Pageable pageable);

    /**
     * Gets all execution history with pagination.
     *
     * @param pageable Pagination parameters
     * @return Page of execution history records
     */
    Page<JobExecutionHistory> getAllHistory(Pageable pageable);

    /**
     * Gets recent execution history.
     *
     * @param limit Maximum number of records to return
     * @return List of recent execution history records
     */
    List<JobExecutionHistory> getRecentHistory(int limit);

    /**
     * Gets execution history by status.
     *
     * @param status The status to filter by
     * @param pageable Pagination parameters
     * @return Page of execution history records
     */
    Page<JobExecutionHistory> getHistoryByStatus(JobExecutionStatus status, Pageable pageable);

    /**
     * Gets execution history within a time range.
     *
     * @param startTime Start of the time range
     * @param endTime End of the time range
     * @param pageable Pagination parameters
     * @return Page of execution history records
     */
    Page<JobExecutionHistory> getHistoryByTimeRange(LocalDateTime startTime,
                                                     LocalDateTime endTime,
                                                     Pageable pageable);

    /**
     * Gets a specific history record by ID.
     *
     * @param id The history record ID
     * @return Optional containing the history record if found
     */
    Optional<JobExecutionHistory> getById(Long id);

    /**
     * Deletes old history records.
     *
     * @param olderThan Delete records older than this time
     * @return Number of records deleted
     */
    int deleteOldHistory(LocalDateTime olderThan);

    /**
     * Gets job execution statistics.
     *
     * @param jobName The job name (optional, null for all jobs)
     * @param jobGroup The job group (optional, null for all groups)
     * @param startTime Start of the time range
     * @param endTime End of the time range
     * @return Statistics summary
     */
    JobExecutionStats getStatistics(String jobName, String jobGroup,
                                    LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Statistics summary for job executions.
     */
    record JobExecutionStats(
            long totalExecutions,
            long completedCount,
            long failedCount,
            long vetoedCount,
            Double avgExecutionTimeMs,
            Long minExecutionTimeMs,
            Long maxExecutionTimeMs
    ) {}
}
