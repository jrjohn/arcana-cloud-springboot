package com.arcana.cloud.scheduler;

import com.arcana.cloud.entity.JobExecutionHistory;
import com.arcana.cloud.entity.JobExecutionHistory.JobExecutionStatus;
import com.arcana.cloud.scheduler.controller.JobController;
import com.arcana.cloud.scheduler.dto.JobDetailDto;
import com.arcana.cloud.scheduler.dto.JobDetailDto.JobStatus;
import com.arcana.cloud.scheduler.dto.JobScheduleRequest;
import com.arcana.cloud.scheduler.dto.JobScheduleRequest.TriggerConfig;
import com.arcana.cloud.scheduler.dto.TriggerDetailDto;
import com.arcana.cloud.scheduler.service.JobHistoryService;
import com.arcana.cloud.scheduler.service.JobHistoryService.JobExecutionStats;
import com.arcana.cloud.scheduler.service.JobManagementService;
import com.arcana.cloud.scheduler.service.JobManagementService.SchedulerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobController Unit Tests")
class JobControllerTest {

    @Mock
    private JobManagementService jobManagementService;

    @Mock
    private JobHistoryService jobHistoryService;

    @InjectMocks
    private JobController jobController;

    private JobDetailDto testJobDetail;
    private TriggerDetailDto testTriggerDetail;

    @BeforeEach
    void setUp() {
        testJobDetail = JobDetailDto.builder()
            .jobName("testJob")
            .jobGroup("testGroup")
            .description("Test job")
            .jobClassName("com.example.TestJob")
            .status(JobStatus.NORMAL)
            .build();

        testTriggerDetail = TriggerDetailDto.builder()
            .triggerName("testTrigger")
            .triggerGroup("testGroup")
            .triggerType(TriggerDetailDto.TriggerType.CRON)
            .state(TriggerDetailDto.TriggerState.NORMAL)
            .build();
    }

    // ==================== Scheduler Status ====================

    @Nested
    @DisplayName("Scheduler Status Endpoints")
    class SchedulerStatusTests {

        @Test
        void getSchedulerStatus_returnsStatus() {
            SchedulerStatus status = mock(SchedulerStatus.class);
            when(jobManagementService.getSchedulerStatus()).thenReturn(status);

            ResponseEntity<SchedulerStatus> response = jobController.getSchedulerStatus();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(status);
        }

        @Test
        void startScheduler_returns200() {
            doNothing().when(jobManagementService).startScheduler();

            ResponseEntity<Void> response = jobController.startScheduler();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(jobManagementService).startScheduler();
        }

        @Test
        void pauseScheduler_returns200() {
            doNothing().when(jobManagementService).pauseScheduler();

            ResponseEntity<Void> response = jobController.pauseScheduler();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(jobManagementService).pauseScheduler();
        }

        @Test
        void shutdownScheduler_withWait_returns200() {
            doNothing().when(jobManagementService).shutdownScheduler(true);

            ResponseEntity<Void> response = jobController.shutdownScheduler(true);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(jobManagementService).shutdownScheduler(true);
        }

        @Test
        void shutdownScheduler_withoutWait_returns200() {
            doNothing().when(jobManagementService).shutdownScheduler(false);

            ResponseEntity<Void> response = jobController.shutdownScheduler(false);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(jobManagementService).shutdownScheduler(false);
        }
    }

    // ==================== Job Management ====================

    @Nested
    @DisplayName("Job Management Endpoints")
    class JobManagementTests {

        @Test
        void scheduleJob_returns201WithJobDetail() {
            JobScheduleRequest request = JobScheduleRequest.builder()
                .jobName("testJob").jobGroup("testGroup")
                .jobClassName("com.example.TestJob")
                .trigger(TriggerConfig.builder().cronExpression("0 0 * * * ?").build())
                .build();

            when(jobManagementService.scheduleJob(request)).thenReturn(testJobDetail);

            ResponseEntity<JobDetailDto> response = jobController.scheduleJob(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isEqualTo(testJobDetail);
        }

        @Test
        void rescheduleJob_returns200WithJobDetail() {
            JobScheduleRequest request = JobScheduleRequest.builder()
                .jobName("testJob").jobGroup("testGroup")
                .jobClassName("com.example.TestJob")
                .build();

            when(jobManagementService.rescheduleJob("testJob", "testGroup", request))
                .thenReturn(testJobDetail);

            ResponseEntity<JobDetailDto> response =
                jobController.rescheduleJob("testGroup", "testJob", request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(testJobDetail);
        }

        @Test
        void getAllJobs_returnsListOfJobs() {
            when(jobManagementService.getAllJobs()).thenReturn(List.of(testJobDetail));

            ResponseEntity<List<JobDetailDto>> response = jobController.getAllJobs();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        void getJobsByGroup_returnsJobs() {
            when(jobManagementService.getJobsByGroup("testGroup")).thenReturn(List.of(testJobDetail));

            ResponseEntity<List<JobDetailDto>> response = jobController.getJobsByGroup("testGroup");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        void getJob_returnsJobDetail() {
            when(jobManagementService.getJob("testJob", "testGroup")).thenReturn(testJobDetail);

            ResponseEntity<JobDetailDto> response = jobController.getJob("testGroup", "testJob");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(testJobDetail);
        }

        @Test
        void deleteJob_whenExists_returns204() {
            when(jobManagementService.deleteJob("testJob", "testGroup")).thenReturn(true);

            ResponseEntity<Void> response = jobController.deleteJob("testGroup", "testJob");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        void deleteJob_whenNotExists_returns404() {
            when(jobManagementService.deleteJob("testJob", "testGroup")).thenReturn(false);

            ResponseEntity<Void> response = jobController.deleteJob("testGroup", "testJob");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void pauseJob_returns200() {
            doNothing().when(jobManagementService).pauseJob("testJob", "testGroup");

            ResponseEntity<Void> response = jobController.pauseJob("testGroup", "testJob");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(jobManagementService).pauseJob("testJob", "testGroup");
        }

        @Test
        void resumeJob_returns200() {
            doNothing().when(jobManagementService).resumeJob("testJob", "testGroup");

            ResponseEntity<Void> response = jobController.resumeJob("testGroup", "testJob");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(jobManagementService).resumeJob("testJob", "testGroup");
        }

        @Test
        void triggerJob_returns202() {
            Map<String, Object> jobData = Map.of("key", "value");
            doNothing().when(jobManagementService).triggerJob("testJob", "testGroup", jobData);

            ResponseEntity<Void> response = jobController.triggerJob("testGroup", "testJob", jobData);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            verify(jobManagementService).triggerJob("testJob", "testGroup", jobData);
        }

        @Test
        void triggerJob_withNullData_returns202() {
            doNothing().when(jobManagementService).triggerJob("testJob", "testGroup", null);

            ResponseEntity<Void> response = jobController.triggerJob("testGroup", "testJob", null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }

        @Test
        void getJobTriggers_returnsTriggers() {
            when(jobManagementService.getJobTriggers("testJob", "testGroup"))
                .thenReturn(List.of(testTriggerDetail));

            ResponseEntity<List<TriggerDetailDto>> response =
                jobController.getJobTriggers("testGroup", "testJob");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        void jobExists_returnsTrue() {
            when(jobManagementService.jobExists("testJob", "testGroup")).thenReturn(true);

            ResponseEntity<Boolean> response = jobController.jobExists("testGroup", "testJob");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isTrue();
        }

        @Test
        void jobExists_returnsFalse() {
            when(jobManagementService.jobExists("unknown", "group")).thenReturn(false);

            ResponseEntity<Boolean> response = jobController.jobExists("group", "unknown");

            assertThat(response.getBody()).isFalse();
        }
    }

    // ==================== Job Group Management ====================

    @Nested
    @DisplayName("Job Group Management Endpoints")
    class JobGroupTests {

        @Test
        void pauseJobGroup_returns200() {
            doNothing().when(jobManagementService).pauseJobGroup("testGroup");

            ResponseEntity<Void> response = jobController.pauseJobGroup("testGroup");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(jobManagementService).pauseJobGroup("testGroup");
        }

        @Test
        void resumeJobGroup_returns200() {
            doNothing().when(jobManagementService).resumeJobGroup("testGroup");

            ResponseEntity<Void> response = jobController.resumeJobGroup("testGroup");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(jobManagementService).resumeJobGroup("testGroup");
        }
    }

    // ==================== Currently Executing Jobs ====================

    @Test
    void getCurrentlyExecutingJobs_returnsJobs() {
        when(jobManagementService.getCurrentlyExecutingJobs()).thenReturn(List.of(testJobDetail));

        ResponseEntity<List<JobDetailDto>> response = jobController.getCurrentlyExecutingJobs();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    // ==================== Job History ====================

    @Nested
    @DisplayName("Job History Endpoints")
    class JobHistoryTests {

        @Test
        void getHistory_returnsPage() {
            JobExecutionHistory history = JobExecutionHistory.builder()
                .id(1L).jobName("testJob").jobGroup("testGroup")
                .status(JobExecutionStatus.COMPLETED).build();

            Page<JobExecutionHistory> page = new PageImpl<>(List.of(history), PageRequest.of(0, 20), 1L);
            when(jobHistoryService.getAllHistory(any())).thenReturn(page);

            ResponseEntity<Page<JobExecutionHistory>> response = jobController.getHistory(0, 20);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getContent()).hasSize(1);
        }

        @Test
        void getJobHistory_returnsPage() {
            Page<JobExecutionHistory> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L);
            when(jobHistoryService.getJobHistory("testJob", "testGroup", PageRequest.of(0, 20)))
                .thenReturn(page);

            ResponseEntity<Page<JobExecutionHistory>> response =
                jobController.getJobHistory("testGroup", "testJob", 0, 20);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void getRecentHistory_returnsHistory() {
            JobExecutionHistory h = JobExecutionHistory.builder().id(1L).build();
            when(jobHistoryService.getRecentHistory(10)).thenReturn(List.of(h));

            ResponseEntity<List<JobExecutionHistory>> response = jobController.getRecentHistory(10);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        void getHistoryByStatus_returnsFilteredPage() {
            Page<JobExecutionHistory> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L);
            when(jobHistoryService.getHistoryByStatus(eq(JobExecutionStatus.FAILED), any()))
                .thenReturn(page);

            ResponseEntity<Page<JobExecutionHistory>> response =
                jobController.getHistoryByStatus(JobExecutionStatus.FAILED, 0, 20);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void getHistoryByTimeRange_returnsPage() {
            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now();
            Page<JobExecutionHistory> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L);
            when(jobHistoryService.getHistoryByTimeRange(eq(start), eq(end), any())).thenReturn(page);

            ResponseEntity<Page<JobExecutionHistory>> response =
                jobController.getHistoryByTimeRange(start, end, 0, 20);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void getStatistics_returnsStats() {
            JobExecutionStats stats = mock(JobExecutionStats.class);
            LocalDateTime start = LocalDateTime.now().minusDays(1);
            LocalDateTime end = LocalDateTime.now();
            when(jobHistoryService.getStatistics(null, null, start, end)).thenReturn(stats);

            ResponseEntity<JobExecutionStats> response =
                jobController.getStatistics(null, null, start, end);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(stats);
        }

        @Test
        void cleanupHistory_returnsDeletedCount() {
            LocalDateTime olderThan = LocalDateTime.now().minusDays(30);
            when(jobHistoryService.deleteOldHistory(olderThan)).thenReturn(42);

            ResponseEntity<Integer> response = jobController.cleanupHistory(olderThan);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(42);
        }
    }
}
