package com.arcana.cloud.scheduler.controller;

import com.arcana.cloud.scheduler.dto.JobDetailDto;
import com.arcana.cloud.scheduler.dto.JobScheduleRequest;
import com.arcana.cloud.scheduler.dto.TriggerDetailDto;
import com.arcana.cloud.scheduler.service.JobHistoryService;
import com.arcana.cloud.scheduler.service.JobHistoryService.JobExecutionStats;
import com.arcana.cloud.scheduler.service.JobManagementService;
import com.arcana.cloud.scheduler.service.JobManagementService.SchedulerStatus;
import com.arcana.cloud.entity.JobExecutionHistory;
import com.arcana.cloud.entity.JobExecutionHistory.JobExecutionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST controller for managing scheduled jobs.
 */
@RestController
@RequestMapping("/api/v1/scheduler")
@RequiredArgsConstructor
@Tag(name = "Scheduler", description = "Distributed job scheduling management")
@ConditionalOnProperty(name = "quartz.enabled", havingValue = "true", matchIfMissing = false)
public class JobController {

    private final JobManagementService jobManagementService;
    private final JobHistoryService jobHistoryService;

    // ==================== Scheduler Status ====================

    @GetMapping("/status")
    @Operation(summary = "Get scheduler status")
    public ResponseEntity<SchedulerStatus> getSchedulerStatus() {
        return ResponseEntity.ok(jobManagementService.getSchedulerStatus());
    }

    @PostMapping("/start")
    @Operation(summary = "Start the scheduler")
    public ResponseEntity<Void> startScheduler() {
        jobManagementService.startScheduler();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/pause")
    @Operation(summary = "Pause the scheduler (standby mode)")
    public ResponseEntity<Void> pauseScheduler() {
        jobManagementService.pauseScheduler();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/shutdown")
    @Operation(summary = "Shutdown the scheduler")
    public ResponseEntity<Void> shutdownScheduler(
            @RequestParam(defaultValue = "true") boolean waitForJobsToComplete) {
        jobManagementService.shutdownScheduler(waitForJobsToComplete);
        return ResponseEntity.ok().build();
    }

    // ==================== Job Management ====================

    @PostMapping("/jobs")
    @Operation(summary = "Schedule a new job")
    public ResponseEntity<JobDetailDto> scheduleJob(@Valid @RequestBody JobScheduleRequest request) {
        JobDetailDto job = jobManagementService.scheduleJob(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @PutMapping("/jobs/{jobGroup}/{jobName}")
    @Operation(summary = "Reschedule an existing job")
    public ResponseEntity<JobDetailDto> rescheduleJob(
            @PathVariable String jobGroup,
            @PathVariable String jobName,
            @Valid @RequestBody JobScheduleRequest request) {
        JobDetailDto job = jobManagementService.rescheduleJob(jobName, jobGroup, request);
        return ResponseEntity.ok(job);
    }

    @GetMapping("/jobs")
    @Operation(summary = "Get all scheduled jobs")
    public ResponseEntity<List<JobDetailDto>> getAllJobs() {
        return ResponseEntity.ok(jobManagementService.getAllJobs());
    }

    @GetMapping("/jobs/group/{jobGroup}")
    @Operation(summary = "Get jobs by group")
    public ResponseEntity<List<JobDetailDto>> getJobsByGroup(@PathVariable String jobGroup) {
        return ResponseEntity.ok(jobManagementService.getJobsByGroup(jobGroup));
    }

    @GetMapping("/jobs/{jobGroup}/{jobName}")
    @Operation(summary = "Get job details")
    public ResponseEntity<JobDetailDto> getJob(
            @PathVariable String jobGroup,
            @PathVariable String jobName) {
        return ResponseEntity.ok(jobManagementService.getJob(jobName, jobGroup));
    }

    @DeleteMapping("/jobs/{jobGroup}/{jobName}")
    @Operation(summary = "Delete a job")
    public ResponseEntity<Void> deleteJob(
            @PathVariable String jobGroup,
            @PathVariable String jobName) {
        boolean deleted = jobManagementService.deleteJob(jobName, jobGroup);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/jobs/{jobGroup}/{jobName}/pause")
    @Operation(summary = "Pause a job")
    public ResponseEntity<Void> pauseJob(
            @PathVariable String jobGroup,
            @PathVariable String jobName) {
        jobManagementService.pauseJob(jobName, jobGroup);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/jobs/{jobGroup}/{jobName}/resume")
    @Operation(summary = "Resume a paused job")
    public ResponseEntity<Void> resumeJob(
            @PathVariable String jobGroup,
            @PathVariable String jobName) {
        jobManagementService.resumeJob(jobName, jobGroup);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/jobs/{jobGroup}/{jobName}/trigger")
    @Operation(summary = "Trigger immediate job execution")
    public ResponseEntity<Void> triggerJob(
            @PathVariable String jobGroup,
            @PathVariable String jobName,
            @RequestBody(required = false) Map<String, Object> jobData) {
        jobManagementService.triggerJob(jobName, jobGroup, jobData);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/jobs/{jobGroup}/{jobName}/triggers")
    @Operation(summary = "Get triggers for a job")
    public ResponseEntity<List<TriggerDetailDto>> getJobTriggers(
            @PathVariable String jobGroup,
            @PathVariable String jobName) {
        return ResponseEntity.ok(jobManagementService.getJobTriggers(jobName, jobGroup));
    }

    @GetMapping("/jobs/{jobGroup}/{jobName}/exists")
    @Operation(summary = "Check if job exists")
    public ResponseEntity<Boolean> jobExists(
            @PathVariable String jobGroup,
            @PathVariable String jobName) {
        return ResponseEntity.ok(jobManagementService.jobExists(jobName, jobGroup));
    }

    // ==================== Job Group Management ====================

    @PostMapping("/groups/{jobGroup}/pause")
    @Operation(summary = "Pause all jobs in a group")
    public ResponseEntity<Void> pauseJobGroup(@PathVariable String jobGroup) {
        jobManagementService.pauseJobGroup(jobGroup);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/groups/{jobGroup}/resume")
    @Operation(summary = "Resume all jobs in a group")
    public ResponseEntity<Void> resumeJobGroup(@PathVariable String jobGroup) {
        jobManagementService.resumeJobGroup(jobGroup);
        return ResponseEntity.ok().build();
    }

    // ==================== Currently Executing Jobs ====================

    @GetMapping("/executing")
    @Operation(summary = "Get currently executing jobs")
    public ResponseEntity<List<JobDetailDto>> getCurrentlyExecutingJobs() {
        return ResponseEntity.ok(jobManagementService.getCurrentlyExecutingJobs());
    }

    // ==================== Job History ====================

    @GetMapping("/history")
    @Operation(summary = "Get job execution history")
    public ResponseEntity<Page<JobExecutionHistory>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(jobHistoryService.getAllHistory(pageable));
    }

    @GetMapping("/history/job/{jobGroup}/{jobName}")
    @Operation(summary = "Get history for a specific job")
    public ResponseEntity<Page<JobExecutionHistory>> getJobHistory(
            @PathVariable String jobGroup,
            @PathVariable String jobName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(jobHistoryService.getJobHistory(jobName, jobGroup, pageable));
    }

    @GetMapping("/history/recent")
    @Operation(summary = "Get recent job executions")
    public ResponseEntity<List<JobExecutionHistory>> getRecentHistory(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(jobHistoryService.getRecentHistory(limit));
    }

    @GetMapping("/history/status/{status}")
    @Operation(summary = "Get history by status")
    public ResponseEntity<Page<JobExecutionHistory>> getHistoryByStatus(
            @PathVariable JobExecutionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(jobHistoryService.getHistoryByStatus(status, pageable));
    }

    @GetMapping("/history/range")
    @Operation(summary = "Get history within time range")
    public ResponseEntity<Page<JobExecutionHistory>> getHistoryByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(jobHistoryService.getHistoryByTimeRange(startTime, endTime, pageable));
    }

    @GetMapping("/history/statistics")
    @Operation(summary = "Get job execution statistics")
    public ResponseEntity<JobExecutionStats> getStatistics(
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String jobGroup,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ResponseEntity.ok(jobHistoryService.getStatistics(jobName, jobGroup, startTime, endTime));
    }

    @DeleteMapping("/history/cleanup")
    @Operation(summary = "Delete old history records")
    public ResponseEntity<Integer> cleanupHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime olderThan) {
        int deleted = jobHistoryService.deleteOldHistory(olderThan);
        return ResponseEntity.ok(deleted);
    }
}
