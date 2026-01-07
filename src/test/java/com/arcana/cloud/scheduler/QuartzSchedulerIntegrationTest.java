package com.arcana.cloud.scheduler;

import com.arcana.cloud.scheduler.dto.JobDetailDto;
import com.arcana.cloud.scheduler.dto.JobScheduleRequest;
import com.arcana.cloud.scheduler.dto.JobScheduleRequest.TriggerConfig;
import com.arcana.cloud.scheduler.job.BaseJob;
import com.arcana.cloud.scheduler.service.JobManagementService;
import com.arcana.cloud.scheduler.service.JobManagementService.SchedulerStatus;
import org.junit.jupiter.api.*;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Quartz Scheduler with JDBC JobStore.
 * Uses Testcontainers with MySQL for realistic database testing.
 * Flyway migrations create QRTZ_* tables before Quartz starts via @DependsOn("flyway").
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test-quartz")
@DisplayName("Quartz Scheduler Integration Tests")
class QuartzSchedulerIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("arcana_cloud_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("database.type", () -> "mysql");
        registry.add("database.orm", () -> "mybatis");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/mysql");
        // Quartz configuration
        registry.add("quartz.enabled", () -> "true");
        registry.add("quartz.cluster.enabled", () -> "false");
        registry.add("quartz.jobs.auto-register", () -> "false");
        // Disable gRPC
        registry.add("spring.grpc.server.enabled", () -> "false");
        registry.add("spring.grpc.server.port", () -> "-1");
        registry.add("grpc.server.port", () -> "-1");
    }

    @Autowired
    private JobManagementService jobManagementService;

    @Autowired
    private Scheduler scheduler;

    // Static counter for test job execution tracking
    static AtomicInteger testJobExecutionCount = new AtomicInteger(0);
    static CountDownLatch jobExecutionLatch;

    @BeforeEach
    void setUp() throws Exception {
        testJobExecutionCount.set(0);
        // Clean up any existing test jobs
        for (JobDetailDto job : jobManagementService.getAllJobs()) {
            if (job.getJobGroup().equals("test")) {
                jobManagementService.deleteJob(job.getJobName(), job.getJobGroup());
            }
        }
    }

    @Nested
    @DisplayName("Scheduler Status Tests")
    class SchedulerStatusTests {

        @Test
        @DisplayName("Should return scheduler status")
        void getSchedulerStatus_ShouldReturnStatus() {
            SchedulerStatus status = jobManagementService.getSchedulerStatus();

            assertThat(status).isNotNull();
            assertThat(status.schedulerName()).isEqualTo("arcanaScheduler");
            assertThat(status.isStarted()).isTrue();
            assertThat(status.isShutdown()).isFalse();
        }
    }

    @Nested
    @DisplayName("Job Scheduling Tests")
    class JobSchedulingTests {

        @Test
        @DisplayName("Should schedule a cron job")
        void scheduleJob_CronTrigger_ShouldSchedule() {
            JobScheduleRequest request = JobScheduleRequest.builder()
                    .jobName("testCronJob")
                    .jobGroup("test")
                    .jobClassName(TestJob.class.getName())
                    .description("Test cron job")
                    .trigger(TriggerConfig.builder()
                            .triggerType(TriggerConfig.TriggerType.CRON)
                            .cronExpression("0 0 * * * ?") // Every hour
                            .build())
                    .build();

            JobDetailDto job = jobManagementService.scheduleJob(request);

            assertThat(job).isNotNull();
            assertThat(job.getJobName()).isEqualTo("testCronJob");
            assertThat(job.getJobGroup()).isEqualTo("test");
            assertThat(job.getTriggers()).hasSize(1);
        }

        @Test
        @DisplayName("Should schedule a simple trigger job")
        void scheduleJob_SimpleTrigger_ShouldSchedule() {
            JobScheduleRequest request = JobScheduleRequest.builder()
                    .jobName("testSimpleJob")
                    .jobGroup("test")
                    .jobClassName(TestJob.class.getName())
                    .description("Test simple job")
                    .trigger(TriggerConfig.builder()
                            .triggerType(TriggerConfig.TriggerType.SIMPLE)
                            .repeatIntervalMs(60000L) // Every minute
                            .repeatCount(-1) // Indefinite
                            .build())
                    .build();

            JobDetailDto job = jobManagementService.scheduleJob(request);

            assertThat(job).isNotNull();
            assertThat(job.getJobName()).isEqualTo("testSimpleJob");
        }

        @Test
        @DisplayName("Should schedule job with job data")
        void scheduleJob_WithJobData_ShouldIncludeData() {
            Map<String, Object> jobData = Map.of(
                    "key1", "value1",
                    "key2", 42
            );

            JobScheduleRequest request = JobScheduleRequest.builder()
                    .jobName("testJobWithData")
                    .jobGroup("test")
                    .jobClassName(TestJob.class.getName())
                    .jobData(jobData)
                    .trigger(TriggerConfig.builder()
                            .triggerType(TriggerConfig.TriggerType.CRON)
                            .cronExpression("0 0 * * * ?")
                            .build())
                    .build();

            JobDetailDto job = jobManagementService.scheduleJob(request);

            assertThat(job.getJobData()).containsEntry("key1", "value1");
            assertThat(job.getJobData()).containsEntry("key2", 42);
        }
    }

    @Nested
    @DisplayName("Job Lifecycle Tests")
    class JobLifecycleTests {

        @Test
        @DisplayName("Should pause and resume job")
        void pauseAndResume_ShouldChangeState() {
            // Schedule a job first
            JobScheduleRequest request = JobScheduleRequest.builder()
                    .jobName("testPauseJob")
                    .jobGroup("test")
                    .jobClassName(TestJob.class.getName())
                    .trigger(TriggerConfig.builder()
                            .triggerType(TriggerConfig.TriggerType.CRON)
                            .cronExpression("0 0 * * * ?")
                            .build())
                    .build();
            jobManagementService.scheduleJob(request);

            // Pause
            jobManagementService.pauseJob("testPauseJob", "test");
            JobDetailDto pausedJob = jobManagementService.getJob("testPauseJob", "test");
            assertThat(pausedJob.getStatus()).isEqualTo(JobDetailDto.JobStatus.PAUSED);

            // Resume
            jobManagementService.resumeJob("testPauseJob", "test");
            JobDetailDto resumedJob = jobManagementService.getJob("testPauseJob", "test");
            assertThat(resumedJob.getStatus()).isEqualTo(JobDetailDto.JobStatus.NORMAL);
        }

        @Test
        @DisplayName("Should delete job")
        void deleteJob_ShouldRemoveJob() {
            // Schedule a job
            JobScheduleRequest request = JobScheduleRequest.builder()
                    .jobName("testDeleteJob")
                    .jobGroup("test")
                    .jobClassName(TestJob.class.getName())
                    .trigger(TriggerConfig.builder()
                            .triggerType(TriggerConfig.TriggerType.CRON)
                            .cronExpression("0 0 * * * ?")
                            .build())
                    .build();
            jobManagementService.scheduleJob(request);

            // Verify it exists
            assertThat(jobManagementService.jobExists("testDeleteJob", "test")).isTrue();

            // Delete
            boolean deleted = jobManagementService.deleteJob("testDeleteJob", "test");

            assertThat(deleted).isTrue();
            assertThat(jobManagementService.jobExists("testDeleteJob", "test")).isFalse();
        }

        @Test
        @DisplayName("Should trigger job immediately")
        void triggerJob_ShouldExecuteImmediately() throws Exception {
            jobExecutionLatch = new CountDownLatch(1);

            // Schedule a job with far future cron
            JobScheduleRequest request = JobScheduleRequest.builder()
                    .jobName("testTriggerJob")
                    .jobGroup("test")
                    .jobClassName(TestJob.class.getName())
                    .trigger(TriggerConfig.builder()
                            .triggerType(TriggerConfig.TriggerType.CRON)
                            .cronExpression("0 0 0 1 1 ? 2099") // Far future
                            .build())
                    .build();
            jobManagementService.scheduleJob(request);

            // Trigger immediately
            jobManagementService.triggerJob("testTriggerJob", "test", null);

            // Wait for execution
            boolean executed = jobExecutionLatch.await(10, TimeUnit.SECONDS);

            assertThat(executed).isTrue();
            assertThat(testJobExecutionCount.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Job Query Tests")
    class JobQueryTests {

        @Test
        @DisplayName("Should get all jobs")
        void getAllJobs_ShouldReturnAllJobs() {
            // Schedule multiple jobs
            for (int i = 0; i < 3; i++) {
                JobScheduleRequest request = JobScheduleRequest.builder()
                        .jobName("testQueryJob" + i)
                        .jobGroup("test")
                        .jobClassName(TestJob.class.getName())
                        .trigger(TriggerConfig.builder()
                                .triggerType(TriggerConfig.TriggerType.CRON)
                                .cronExpression("0 0 * * * ?")
                                .build())
                        .build();
                jobManagementService.scheduleJob(request);
            }

            List<JobDetailDto> jobs = jobManagementService.getJobsByGroup("test");

            assertThat(jobs).hasSizeGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("Should get job triggers")
        void getJobTriggers_ShouldReturnTriggers() {
            JobScheduleRequest request = JobScheduleRequest.builder()
                    .jobName("testTriggerQueryJob")
                    .jobGroup("test")
                    .jobClassName(TestJob.class.getName())
                    .trigger(TriggerConfig.builder()
                            .triggerType(TriggerConfig.TriggerType.CRON)
                            .cronExpression("0 0 * * * ?")
                            .build())
                    .build();
            jobManagementService.scheduleJob(request);

            var triggers = jobManagementService.getJobTriggers("testTriggerQueryJob", "test");

            assertThat(triggers).hasSize(1);
            assertThat(triggers.get(0).getCronExpression()).isEqualTo("0 0 * * * ?");
        }
    }

    /**
     * Test job implementation for testing purposes.
     */
    public static class TestJob extends BaseJob {
        @Override
        protected void doExecute(JobExecutionContext context) {
            testJobExecutionCount.incrementAndGet();
            if (jobExecutionLatch != null) {
                jobExecutionLatch.countDown();
            }
        }
    }
}
