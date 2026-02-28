package com.arcana.cloud.scheduler;

import com.arcana.cloud.scheduler.dto.JobDetailDto;
import com.arcana.cloud.scheduler.dto.JobDetailDto.JobStatus;
import com.arcana.cloud.scheduler.dto.JobScheduleRequest;
import com.arcana.cloud.scheduler.dto.JobScheduleRequest.TriggerConfig;
import com.arcana.cloud.scheduler.dto.TriggerDetailDto;
import com.arcana.cloud.scheduler.service.impl.JobManagementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.*;
import org.quartz.impl.JobDetailImpl;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.impl.triggers.CronTriggerImpl;
import org.quartz.impl.triggers.SimpleTriggerImpl;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobManagementServiceImpl Unit Tests")
@SuppressWarnings("java:S2187")
class JobManagementServiceImplTest {

    @Mock
    private Scheduler scheduler;

    @InjectMocks
    private JobManagementServiceImpl jobManagementService;

    private JobDetail testJobDetail;
    private CronTrigger testCronTrigger;
    private JobKey testJobKey;

    @BeforeEach
    void setUp() throws SchedulerException {
        testJobKey = JobKey.jobKey("testJob", "testGroup");

        testJobDetail = JobBuilder.newJob(NoOpJob.class)
                .withIdentity(testJobKey)
                .withDescription("Test job description")
                .storeDurably(true)
                .requestRecovery(true)
                .build();

        testCronTrigger = TriggerBuilder.newTrigger()
                .withIdentity("testTrigger", "testGroup")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0/5 * * * ?"))
                .build();
    }

    // Minimal no-op job for testing class resolution
    public static class NoOpJob implements Job {
        @Override
        public void execute(JobExecutionContext context) {
            // no-op
        }
    }

    // -------------------------------------------------------------------------
    // scheduleJob
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("scheduleJob")
    class ScheduleJobTests {

        @Test
        @DisplayName("Should schedule a cron job successfully")
        void scheduleJob_CronTrigger_Success() throws SchedulerException {
            JobScheduleRequest request = buildCronRequest("testJob", "testGroup",
                    NoOpJob.class.getName(), "0 0/5 * * * ?");

            when(scheduler.scheduleJob(any(JobDetail.class), any(Trigger.class))).thenReturn(new Date());
            when(scheduler.getJobDetail(any(JobKey.class))).thenReturn(testJobDetail);
            when(scheduler.getTriggersOfJob(any(JobKey.class))).thenReturn(List.of(testCronTrigger));
            when(scheduler.getTriggerState(any(TriggerKey.class))).thenReturn(Trigger.TriggerState.NORMAL);

            JobDetailDto result = jobManagementService.scheduleJob(request);

            assertNotNull(result);
            assertEquals("testJob", result.getJobName());
            assertEquals("testGroup", result.getJobGroup());
            verify(scheduler, times(1)).scheduleJob(any(JobDetail.class), any(Trigger.class));
        }

        @Test
        @DisplayName("Should schedule a simple trigger job successfully")
        void scheduleJob_SimpleTrigger_Success() throws SchedulerException {
            JobScheduleRequest request = buildSimpleRequest("simpleJob", "simpleGroup",
                    NoOpJob.class.getName(), 5000L);

            SimpleTrigger simpleTrigger = TriggerBuilder.newTrigger()
                    .withIdentity("simpleJobTrigger", "simpleGroup")
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMilliseconds(5000L)
                            .repeatForever())
                    .build();

            JobDetail simpleDetail = JobBuilder.newJob(NoOpJob.class)
                    .withIdentity("simpleJob", "simpleGroup")
                    .build();

            when(scheduler.scheduleJob(any(JobDetail.class), any(Trigger.class))).thenReturn(new Date());
            when(scheduler.getJobDetail(any(JobKey.class))).thenReturn(simpleDetail);
            when(scheduler.getTriggersOfJob(any(JobKey.class))).thenReturn(List.of(simpleTrigger));
            when(scheduler.getTriggerState(any(TriggerKey.class))).thenReturn(Trigger.TriggerState.NORMAL);

            JobDetailDto result = jobManagementService.scheduleJob(request);

            assertNotNull(result);
            verify(scheduler, times(1)).scheduleJob(any(JobDetail.class), any(Trigger.class));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for unknown job class")
        void scheduleJob_UnknownClass_ThrowsIllegalArgument() {
            JobScheduleRequest request = buildCronRequest("testJob", "testGroup",
                    "com.arcana.cloud.NonExistentJob", "0 0/5 * * * ?");

            assertThrows(IllegalArgumentException.class,
                    () -> jobManagementService.scheduleJob(request));
        }

        @Test
        @DisplayName("Should throw RuntimeException when SchedulerException occurs")
        void scheduleJob_SchedulerException_ThrowsRuntime() throws SchedulerException {
            JobScheduleRequest request = buildCronRequest("testJob", "testGroup",
                    NoOpJob.class.getName(), "0 0/5 * * * ?");

            when(scheduler.scheduleJob(any(JobDetail.class), any(Trigger.class)))
                    .thenThrow(new SchedulerException("Scheduler error"));

            assertThrows(RuntimeException.class,
                    () -> jobManagementService.scheduleJob(request));
        }

        @Test
        @DisplayName("Should schedule job with extra job data")
        void scheduleJob_WithJobData_Success() throws SchedulerException {
            Map<String, Object> jobData = new HashMap<>();
            jobData.put("param1", "value1");
            jobData.put("param2", 42);

            JobScheduleRequest request = JobScheduleRequest.builder()
                    .jobName("dataJob")
                    .jobGroup("dataGroup")
                    .jobClassName(NoOpJob.class.getName())
                    .description("Job with data")
                    .jobData(jobData)
                    .trigger(TriggerConfig.builder()
                            .triggerType(TriggerConfig.TriggerType.CRON)
                            .cronExpression("0 0 * * * ?")
                            .build())
                    .build();

            JobDetail dataDetail = JobBuilder.newJob(NoOpJob.class)
                    .withIdentity("dataJob", "dataGroup")
                    .build();
            dataDetail.getJobDataMap().put("param1", "value1");
            dataDetail.getJobDataMap().put("param2", 42);

            when(scheduler.scheduleJob(any(JobDetail.class), any(Trigger.class))).thenReturn(new Date());
            when(scheduler.getJobDetail(any(JobKey.class))).thenReturn(dataDetail);
            when(scheduler.getTriggersOfJob(any(JobKey.class))).thenReturn(List.of(testCronTrigger));
            when(scheduler.getTriggerState(any(TriggerKey.class))).thenReturn(Trigger.TriggerState.NORMAL);

            JobDetailDto result = jobManagementService.scheduleJob(request);

            assertNotNull(result);
        }
    }

    // -------------------------------------------------------------------------
    // rescheduleJob
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("rescheduleJob")
    class RescheduleJobTests {

        @Test
        @DisplayName("Should reschedule existing job successfully")
        void rescheduleJob_Success() throws SchedulerException {
            JobScheduleRequest request = buildCronRequest("testJob", "testGroup",
                    NoOpJob.class.getName(), "0 0 * * * ?");

            when(scheduler.checkExists(testJobKey)).thenReturn(true);
            when(scheduler.getTriggersOfJob(testJobKey)).thenReturn(List.of(testCronTrigger));
            when(scheduler.rescheduleJob(any(TriggerKey.class), any(Trigger.class))).thenReturn(new Date());
            when(scheduler.getJobDetail(any(JobKey.class))).thenReturn(testJobDetail);
            when(scheduler.getTriggerState(any(TriggerKey.class))).thenReturn(Trigger.TriggerState.NORMAL);

            JobDetailDto result = jobManagementService.rescheduleJob("testJob", "testGroup", request);

            assertNotNull(result);
            verify(scheduler, times(1)).rescheduleJob(any(TriggerKey.class), any(Trigger.class));
        }

        @Test
        @DisplayName("Should schedule new trigger when no existing triggers")
        void rescheduleJob_NoExistingTriggers_SchedulesNew() throws SchedulerException {
            JobScheduleRequest request = buildCronRequest("testJob", "testGroup",
                    NoOpJob.class.getName(), "0 0 * * * ?");

            when(scheduler.checkExists(testJobKey)).thenReturn(true);
            when(scheduler.getTriggersOfJob(testJobKey)).thenReturn(Collections.emptyList());
            when(scheduler.scheduleJob(any(Trigger.class))).thenReturn(new Date());
            when(scheduler.getJobDetail(any(JobKey.class))).thenReturn(testJobDetail);
            when(scheduler.getTriggerState(any(TriggerKey.class))).thenReturn(Trigger.TriggerState.NORMAL);

            JobDetailDto result = jobManagementService.rescheduleJob("testJob", "testGroup", request);

            assertNotNull(result);
            verify(scheduler, times(1)).scheduleJob(any(Trigger.class));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when job not found")
        void rescheduleJob_NotFound_ThrowsIllegalArgument() throws SchedulerException {
            JobScheduleRequest request = buildCronRequest("unknownJob", "testGroup",
                    NoOpJob.class.getName(), "0 0 * * * ?");

            when(scheduler.checkExists(JobKey.jobKey("unknownJob", "testGroup"))).thenReturn(false);

            assertThrows(IllegalArgumentException.class,
                    () -> jobManagementService.rescheduleJob("unknownJob", "testGroup", request));
        }
    }

    // -------------------------------------------------------------------------
    // getJob
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("getJob")
    class GetJobTests {

        @Test
        @DisplayName("Should return job details for existing job")
        void getJob_Exists_ReturnsDetails() throws SchedulerException {
            when(scheduler.getJobDetail(testJobKey)).thenReturn(testJobDetail);
            when(scheduler.getTriggersOfJob(testJobKey)).thenReturn(List.of(testCronTrigger));
            when(scheduler.getTriggerState(any(TriggerKey.class))).thenReturn(Trigger.TriggerState.NORMAL);

            JobDetailDto result = jobManagementService.getJob("testJob", "testGroup");

            assertNotNull(result);
            assertEquals("testJob", result.getJobName());
            assertEquals("testGroup", result.getJobGroup());
            assertEquals(NoOpJob.class.getName(), result.getJobClassName());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when job does not exist")
        void getJob_NotFound_ThrowsIllegalArgument() throws SchedulerException {
            when(scheduler.getJobDetail(any(JobKey.class))).thenReturn(null);

            assertThrows(IllegalArgumentException.class,
                    () -> jobManagementService.getJob("missing", "testGroup"));
        }

        @Test
        @DisplayName("Should throw RuntimeException on SchedulerException")
        void getJob_SchedulerException_ThrowsRuntime() throws SchedulerException {
            when(scheduler.getJobDetail(any(JobKey.class)))
                    .thenThrow(new SchedulerException("DB error"));

            assertThrows(RuntimeException.class,
                    () -> jobManagementService.getJob("testJob", "testGroup"));
        }
    }

    // -------------------------------------------------------------------------
    // getAllJobs
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("getAllJobs")
    class GetAllJobsTests {

        @Test
        @DisplayName("Should return all jobs from all groups")
        void getAllJobs_ReturnsAllJobs() throws SchedulerException {
            Set<JobKey> jobKeys = Set.of(testJobKey);

            when(scheduler.getJobGroupNames()).thenReturn(List.of("testGroup"));
            when(scheduler.getJobKeys(any(GroupMatcher.class))).thenReturn(jobKeys);
            when(scheduler.getJobDetail(testJobKey)).thenReturn(testJobDetail);
            when(scheduler.getTriggersOfJob(testJobKey)).thenReturn(List.of(testCronTrigger));
            when(scheduler.getTriggerState(any(TriggerKey.class))).thenReturn(Trigger.TriggerState.NORMAL);

            List<JobDetailDto> result = jobManagementService.getAllJobs();

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("testJob", result.get(0).getJobName());
        }

        @Test
        @DisplayName("Should return empty list when no groups exist")
        void getAllJobs_NoGroups_ReturnsEmpty() throws SchedulerException {
            when(scheduler.getJobGroupNames()).thenReturn(Collections.emptyList());

            List<JobDetailDto> result = jobManagementService.getAllJobs();

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should skip null job details")
        void getAllJobs_NullJobDetail_Skipped() throws SchedulerException {
            Set<JobKey> jobKeys = Set.of(testJobKey);

            when(scheduler.getJobGroupNames()).thenReturn(List.of("testGroup"));
            when(scheduler.getJobKeys(any(GroupMatcher.class))).thenReturn(jobKeys);
            when(scheduler.getJobDetail(testJobKey)).thenReturn(null);

            List<JobDetailDto> result = jobManagementService.getAllJobs();

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // getJobsByGroup
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("getJobsByGroup")
    class GetJobsByGroupTests {

        @Test
        @DisplayName("Should return jobs for a specific group")
        void getJobsByGroup_ReturnsGroupJobs() throws SchedulerException {
            Set<JobKey> jobKeys = Set.of(testJobKey);

            when(scheduler.getJobKeys(any(GroupMatcher.class))).thenReturn(jobKeys);
            when(scheduler.getJobDetail(testJobKey)).thenReturn(testJobDetail);
            when(scheduler.getTriggersOfJob(testJobKey)).thenReturn(List.of(testCronTrigger));
            when(scheduler.getTriggerState(any(TriggerKey.class))).thenReturn(Trigger.TriggerState.NORMAL);

            List<JobDetailDto> result = jobManagementService.getJobsByGroup("testGroup");

            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Should return empty list when group has no jobs")
        void getJobsByGroup_EmptyGroup_ReturnsEmpty() throws SchedulerException {
            when(scheduler.getJobKeys(any(GroupMatcher.class))).thenReturn(Collections.emptySet());

            List<JobDetailDto> result = jobManagementService.getJobsByGroup("emptyGroup");

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // pauseJob / resumeJob
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("pauseJob and resumeJob")
    class PauseResumeJobTests {

        @Test
        @DisplayName("Should pause a job successfully")
        void pauseJob_Success() throws SchedulerException {
            doNothing().when(scheduler).pauseJob(testJobKey);

            assertDoesNotThrow(() -> jobManagementService.pauseJob("testJob", "testGroup"));
            verify(scheduler, times(1)).pauseJob(testJobKey);
        }

        @Test
        @DisplayName("Should throw RuntimeException when pause fails")
        void pauseJob_SchedulerException_ThrowsRuntime() throws SchedulerException {
            doThrow(new SchedulerException("Pause failed")).when(scheduler).pauseJob(any(JobKey.class));

            assertThrows(RuntimeException.class,
                    () -> jobManagementService.pauseJob("testJob", "testGroup"));
        }

        @Test
        @DisplayName("Should resume a job successfully")
        void resumeJob_Success() throws SchedulerException {
            doNothing().when(scheduler).resumeJob(testJobKey);

            assertDoesNotThrow(() -> jobManagementService.resumeJob("testJob", "testGroup"));
            verify(scheduler, times(1)).resumeJob(testJobKey);
        }

        @Test
        @DisplayName("Should throw RuntimeException when resume fails")
        void resumeJob_SchedulerException_ThrowsRuntime() throws SchedulerException {
            doThrow(new SchedulerException("Resume failed")).when(scheduler).resumeJob(any(JobKey.class));

            assertThrows(RuntimeException.class,
                    () -> jobManagementService.resumeJob("testJob", "testGroup"));
        }
    }

    // -------------------------------------------------------------------------
    // triggerJob
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("triggerJob")
    class TriggerJobTests {

        @Test
        @DisplayName("Should trigger a job immediately with data")
        void triggerJob_WithData_Success() throws SchedulerException {
            Map<String, Object> data = Map.of("key", "value");
            doNothing().when(scheduler).triggerJob(any(JobKey.class), any(JobDataMap.class));

            assertDoesNotThrow(() -> jobManagementService.triggerJob("testJob", "testGroup", data));
            verify(scheduler, times(1)).triggerJob(any(JobKey.class), any(JobDataMap.class));
        }

        @Test
        @DisplayName("Should trigger a job with null data map")
        void triggerJob_NullData_Success() throws SchedulerException {
            doNothing().when(scheduler).triggerJob(any(JobKey.class), any(JobDataMap.class));

            assertDoesNotThrow(() -> jobManagementService.triggerJob("testJob", "testGroup", null));
            verify(scheduler, times(1)).triggerJob(any(JobKey.class), any(JobDataMap.class));
        }

        @Test
        @DisplayName("Should throw RuntimeException on scheduler error")
        void triggerJob_SchedulerException_ThrowsRuntime() throws SchedulerException {
            doThrow(new SchedulerException("Trigger failed"))
                    .when(scheduler).triggerJob(any(JobKey.class), any(JobDataMap.class));

            assertThrows(RuntimeException.class,
                    () -> jobManagementService.triggerJob("testJob", "testGroup", null));
        }
    }

    // -------------------------------------------------------------------------
    // deleteJob
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("deleteJob")
    class DeleteJobTests {

        @Test
        @DisplayName("Should delete an existing job and return true")
        void deleteJob_Exists_ReturnsTrue() throws SchedulerException {
            when(scheduler.deleteJob(testJobKey)).thenReturn(true);

            boolean deleted = jobManagementService.deleteJob("testJob", "testGroup");

            assertTrue(deleted);
            verify(scheduler, times(1)).deleteJob(testJobKey);
        }

        @Test
        @DisplayName("Should return false when job does not exist")
        void deleteJob_NotFound_ReturnsFalse() throws SchedulerException {
            when(scheduler.deleteJob(any(JobKey.class))).thenReturn(false);

            boolean deleted = jobManagementService.deleteJob("missingJob", "testGroup");

            assertFalse(deleted);
        }

        @Test
        @DisplayName("Should throw RuntimeException on scheduler error")
        void deleteJob_SchedulerException_ThrowsRuntime() throws SchedulerException {
            when(scheduler.deleteJob(any(JobKey.class)))
                    .thenThrow(new SchedulerException("Delete failed"));

            assertThrows(RuntimeException.class,
                    () -> jobManagementService.deleteJob("testJob", "testGroup"));
        }
    }

    // -------------------------------------------------------------------------
    // pauseJobGroup / resumeJobGroup
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("pauseJobGroup and resumeJobGroup")
    class PauseResumeGroupTests {

        @Test
        @DisplayName("Should pause all jobs in a group")
        void pauseJobGroup_Success() throws SchedulerException {
            doNothing().when(scheduler).pauseJobs(any(GroupMatcher.class));

            assertDoesNotThrow(() -> jobManagementService.pauseJobGroup("testGroup"));
            verify(scheduler, times(1)).pauseJobs(any(GroupMatcher.class));
        }

        @Test
        @DisplayName("Should throw RuntimeException when pausing group fails")
        void pauseJobGroup_SchedulerException_ThrowsRuntime() throws SchedulerException {
            doThrow(new SchedulerException("Group pause failed"))
                    .when(scheduler).pauseJobs(any(GroupMatcher.class));

            assertThrows(RuntimeException.class,
                    () -> jobManagementService.pauseJobGroup("testGroup"));
        }

        @Test
        @DisplayName("Should resume all jobs in a group")
        void resumeJobGroup_Success() throws SchedulerException {
            doNothing().when(scheduler).resumeJobs(any(GroupMatcher.class));

            assertDoesNotThrow(() -> jobManagementService.resumeJobGroup("testGroup"));
            verify(scheduler, times(1)).resumeJobs(any(GroupMatcher.class));
        }

        @Test
        @DisplayName("Should throw RuntimeException when resuming group fails")
        void resumeJobGroup_SchedulerException_ThrowsRuntime() throws SchedulerException {
            doThrow(new SchedulerException("Group resume failed"))
                    .when(scheduler).resumeJobs(any(GroupMatcher.class));

            assertThrows(RuntimeException.class,
                    () -> jobManagementService.resumeJobGroup("testGroup"));
        }
    }

    // -------------------------------------------------------------------------
    // getJobTriggers
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("getJobTriggers")
    class GetJobTriggersTests {

        @Test
        @DisplayName("Should return triggers for a job")
        void getJobTriggers_ReturnsTriggers() throws SchedulerException {
            when(scheduler.getTriggersOfJob(testJobKey)).thenReturn(List.of(testCronTrigger));
            when(scheduler.getTriggerState(any(TriggerKey.class))).thenReturn(Trigger.TriggerState.NORMAL);

            List<TriggerDetailDto> result = jobManagementService.getJobTriggers("testJob", "testGroup");

            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Should return empty list when no triggers exist")
        void getJobTriggers_NoTriggers_ReturnsEmpty() throws SchedulerException {
            when(scheduler.getTriggersOfJob(testJobKey)).thenReturn(Collections.emptyList());

            List<TriggerDetailDto> result = jobManagementService.getJobTriggers("testJob", "testGroup");

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should throw RuntimeException on scheduler error")
        void getJobTriggers_SchedulerException_ThrowsRuntime() throws SchedulerException {
            when(scheduler.getTriggersOfJob(any(JobKey.class)))
                    .thenThrow(new SchedulerException("Trigger fetch failed"));

            assertThrows(RuntimeException.class,
                    () -> jobManagementService.getJobTriggers("testJob", "testGroup"));
        }
    }

    // -------------------------------------------------------------------------
    // jobExists
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("jobExists")
    class JobExistsTests {

        @Test
        @DisplayName("Should return true when job exists")
        void jobExists_Exists_ReturnsTrue() throws SchedulerException {
            when(scheduler.checkExists(testJobKey)).thenReturn(true);

            assertTrue(jobManagementService.jobExists("testJob", "testGroup"));
        }

        @Test
        @DisplayName("Should return false when job does not exist")
        void jobExists_NotFound_ReturnsFalse() throws SchedulerException {
            when(scheduler.checkExists(any(JobKey.class))).thenReturn(false);

            assertFalse(jobManagementService.jobExists("unknownJob", "testGroup"));
        }

        @Test
        @DisplayName("Should throw RuntimeException on scheduler error")
        void jobExists_SchedulerException_ThrowsRuntime() throws SchedulerException {
            when(scheduler.checkExists(any(JobKey.class)))
                    .thenThrow(new SchedulerException("Check failed"));

            assertThrows(RuntimeException.class,
                    () -> jobManagementService.jobExists("testJob", "testGroup"));
        }
    }

    // -------------------------------------------------------------------------
    // getSchedulerStatus
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("getSchedulerStatus")
    class SchedulerStatusTests {

        @Test
        @DisplayName("Should return scheduler status metadata")
        void getSchedulerStatus_ReturnsStatus() throws SchedulerException {
            SchedulerMetaData meta = mock(SchedulerMetaData.class);
            when(meta.getSchedulerName()).thenReturn("testScheduler");
            when(meta.getSchedulerInstanceId()).thenReturn("inst-01");
            when(meta.isStarted()).thenReturn(true);
            when(meta.isInStandbyMode()).thenReturn(false);
            when(meta.isShutdown()).thenReturn(false);
            when(meta.getThreadPoolSize()).thenReturn(10);
            when(meta.getVersion()).thenReturn("2.3.2");
            when(scheduler.getMetaData()).thenReturn(meta);

            JobManagementService.SchedulerStatus status = jobManagementService.getSchedulerStatus();

            assertNotNull(status);
            assertEquals("testScheduler", status.schedulerName());
            assertEquals("inst-01", status.instanceId());
            assertTrue(status.isStarted());
            assertFalse(status.isInStandbyMode());
            assertFalse(status.isShutdown());
            assertEquals(10, status.threadPoolSize());
            assertEquals("2.3.2", status.version());
        }

        @Test
        @DisplayName("Should throw RuntimeException on scheduler error")
        void getSchedulerStatus_SchedulerException_ThrowsRuntime() throws SchedulerException {
            when(scheduler.getMetaData()).thenThrow(new SchedulerException("Meta error"));

            assertThrows(RuntimeException.class, () -> jobManagementService.getSchedulerStatus());
        }
    }

    // -------------------------------------------------------------------------
    // startScheduler / pauseScheduler / shutdownScheduler
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("Scheduler lifecycle methods")
    class SchedulerLifecycleTests {

        @Test
        @DisplayName("Should start the scheduler")
        void startScheduler_Success() throws SchedulerException {
            doNothing().when(scheduler).start();

            assertDoesNotThrow(() -> jobManagementService.startScheduler());
            verify(scheduler, times(1)).start();
        }

        @Test
        @DisplayName("Should throw RuntimeException when start fails")
        void startScheduler_SchedulerException_ThrowsRuntime() throws SchedulerException {
            doThrow(new SchedulerException("Start failed")).when(scheduler).start();

            assertThrows(RuntimeException.class, () -> jobManagementService.startScheduler());
        }

        @Test
        @DisplayName("Should pause the scheduler (standby)")
        void pauseScheduler_Success() throws SchedulerException {
            doNothing().when(scheduler).standby();

            assertDoesNotThrow(() -> jobManagementService.pauseScheduler());
            verify(scheduler, times(1)).standby();
        }

        @Test
        @DisplayName("Should throw RuntimeException when standby fails")
        void pauseScheduler_SchedulerException_ThrowsRuntime() throws SchedulerException {
            doThrow(new SchedulerException("Standby failed")).when(scheduler).standby();

            assertThrows(RuntimeException.class, () -> jobManagementService.pauseScheduler());
        }

        @Test
        @DisplayName("Should shutdown the scheduler gracefully")
        void shutdownScheduler_Graceful_Success() throws SchedulerException {
            doNothing().when(scheduler).shutdown(true);

            assertDoesNotThrow(() -> jobManagementService.shutdownScheduler(true));
            verify(scheduler, times(1)).shutdown(true);
        }

        @Test
        @DisplayName("Should shutdown the scheduler immediately")
        void shutdownScheduler_Immediate_Success() throws SchedulerException {
            doNothing().when(scheduler).shutdown(false);

            assertDoesNotThrow(() -> jobManagementService.shutdownScheduler(false));
            verify(scheduler, times(1)).shutdown(false);
        }

        @Test
        @DisplayName("Should throw RuntimeException when shutdown fails")
        void shutdownScheduler_SchedulerException_ThrowsRuntime() throws SchedulerException {
            doThrow(new SchedulerException("Shutdown failed")).when(scheduler).shutdown(anyBoolean());

            assertThrows(RuntimeException.class,
                    () -> jobManagementService.shutdownScheduler(true));
        }
    }

    // -------------------------------------------------------------------------
    // getCurrentlyExecutingJobs
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("getCurrentlyExecutingJobs")
    class CurrentlyExecutingJobsTests {

        @Test
        @DisplayName("Should return currently executing jobs")
        void getCurrentlyExecutingJobs_ReturnsJobs() throws SchedulerException {
            JobExecutionContext ctx = mock(JobExecutionContext.class);
            when(ctx.getJobDetail()).thenReturn(testJobDetail);

            when(scheduler.getCurrentlyExecutingJobs()).thenReturn(List.of(ctx));
            when(scheduler.getTriggersOfJob(any(JobKey.class))).thenReturn(List.of(testCronTrigger));
            when(scheduler.getTriggerState(any(TriggerKey.class))).thenReturn(Trigger.TriggerState.NORMAL);

            List<JobDetailDto> result = jobManagementService.getCurrentlyExecutingJobs();

            assertNotNull(result);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("Should return empty list when no jobs are executing")
        void getCurrentlyExecutingJobs_None_ReturnsEmpty() throws SchedulerException {
            when(scheduler.getCurrentlyExecutingJobs()).thenReturn(Collections.emptyList());

            List<JobDetailDto> result = jobManagementService.getCurrentlyExecutingJobs();

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should throw RuntimeException on scheduler error")
        void getCurrentlyExecutingJobs_SchedulerException_ThrowsRuntime() throws SchedulerException {
            when(scheduler.getCurrentlyExecutingJobs())
                    .thenThrow(new SchedulerException("Executing jobs error"));

            assertThrows(RuntimeException.class,
                    () -> jobManagementService.getCurrentlyExecutingJobs());
        }
    }

    // -------------------------------------------------------------------------
    // Job status derivation (PAUSED, ERROR, BLOCKED, NONE, NORMAL)
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("Job status determination")
    class JobStatusTests {

        @Test
        @DisplayName("Should return PAUSED status when all triggers are paused")
        void getJob_AllTriggersPaused_StatusPaused() throws SchedulerException {
            when(scheduler.getJobDetail(testJobKey)).thenReturn(testJobDetail);
            when(scheduler.getTriggersOfJob(testJobKey)).thenReturn(List.of(testCronTrigger));
            when(scheduler.getTriggerState(any(TriggerKey.class)))
                    .thenReturn(Trigger.TriggerState.PAUSED);

            JobDetailDto result = jobManagementService.getJob("testJob", "testGroup");

            assertEquals(JobStatus.PAUSED, result.getStatus());
        }

        @Test
        @DisplayName("Should return ERROR status when a trigger is in error state")
        void getJob_TriggerError_StatusError() throws SchedulerException {
            when(scheduler.getJobDetail(testJobKey)).thenReturn(testJobDetail);
            when(scheduler.getTriggersOfJob(testJobKey)).thenReturn(List.of(testCronTrigger));
            when(scheduler.getTriggerState(any(TriggerKey.class)))
                    .thenReturn(Trigger.TriggerState.ERROR);

            JobDetailDto result = jobManagementService.getJob("testJob", "testGroup");

            assertEquals(JobStatus.ERROR, result.getStatus());
        }

        @Test
        @DisplayName("Should return BLOCKED status when a trigger is blocked")
        void getJob_TriggerBlocked_StatusBlocked() throws SchedulerException {
            when(scheduler.getJobDetail(testJobKey)).thenReturn(testJobDetail);
            when(scheduler.getTriggersOfJob(testJobKey)).thenReturn(List.of(testCronTrigger));
            when(scheduler.getTriggerState(any(TriggerKey.class)))
                    .thenReturn(Trigger.TriggerState.BLOCKED);

            JobDetailDto result = jobManagementService.getJob("testJob", "testGroup");

            assertEquals(JobStatus.BLOCKED, result.getStatus());
        }

        @Test
        @DisplayName("Should return NONE status when job has no triggers")
        void getJob_NoTriggers_StatusNone() throws SchedulerException {
            when(scheduler.getJobDetail(testJobKey)).thenReturn(testJobDetail);
            when(scheduler.getTriggersOfJob(testJobKey)).thenReturn(Collections.emptyList());

            JobDetailDto result = jobManagementService.getJob("testJob", "testGroup");

            assertEquals(JobStatus.NONE, result.getStatus());
        }

        @Test
        @DisplayName("Should return NORMAL status when trigger is running normally")
        void getJob_TriggerNormal_StatusNormal() throws SchedulerException {
            when(scheduler.getJobDetail(testJobKey)).thenReturn(testJobDetail);
            when(scheduler.getTriggersOfJob(testJobKey)).thenReturn(List.of(testCronTrigger));
            when(scheduler.getTriggerState(any(TriggerKey.class)))
                    .thenReturn(Trigger.TriggerState.NORMAL);

            JobDetailDto result = jobManagementService.getJob("testJob", "testGroup");

            assertEquals(JobStatus.NORMAL, result.getStatus());
        }
    }

    // -------------------------------------------------------------------------
    // Trigger type mapping (CRON vs SIMPLE)
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("Trigger type mapping")
    class TriggerTypeMappingTests {

        @Test
        @DisplayName("Should map cron trigger with expression and timezone")
        void getJobTriggers_CronTrigger_MapsCorrectly() throws SchedulerException {
            when(scheduler.getTriggersOfJob(testJobKey)).thenReturn(List.of(testCronTrigger));
            when(scheduler.getTriggerState(any(TriggerKey.class)))
                    .thenReturn(Trigger.TriggerState.NORMAL);

            List<TriggerDetailDto> triggers = jobManagementService.getJobTriggers("testJob", "testGroup");

            assertNotNull(triggers);
            assertEquals(1, triggers.size());
            assertEquals(TriggerDetailDto.TriggerType.CRON, triggers.get(0).getTriggerType());
            assertEquals("0 0/5 * * * ?", triggers.get(0).getCronExpression());
        }

        @Test
        @DisplayName("Should map simple trigger with repeat count and interval")
        void getJobTriggers_SimpleTrigger_MapsCorrectly() throws SchedulerException {
            SimpleTrigger simpleTrigger = TriggerBuilder.newTrigger()
                    .withIdentity("simpleTrig", "testGroup")
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMilliseconds(10000L)
                            .withRepeatCount(5))
                    .build();

            when(scheduler.getTriggersOfJob(testJobKey)).thenReturn(List.of(simpleTrigger));
            when(scheduler.getTriggerState(any(TriggerKey.class)))
                    .thenReturn(Trigger.TriggerState.NORMAL);

            List<TriggerDetailDto> triggers = jobManagementService.getJobTriggers("testJob", "testGroup");

            assertNotNull(triggers);
            assertEquals(1, triggers.size());
            assertEquals(TriggerDetailDto.TriggerType.SIMPLE, triggers.get(0).getTriggerType());
            assertEquals(5, triggers.get(0).getRepeatCount());
            assertEquals(10000L, triggers.get(0).getRepeatIntervalMs());
        }
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private JobScheduleRequest buildCronRequest(String jobName, String jobGroup,
                                                 String className, String cronExpr) {
        return JobScheduleRequest.builder()
                .jobName(jobName)
                .jobGroup(jobGroup)
                .jobClassName(className)
                .description("Test description")
                .trigger(TriggerConfig.builder()
                        .triggerType(TriggerConfig.TriggerType.CRON)
                        .cronExpression(cronExpr)
                        .build())
                .build();
    }

    private JobScheduleRequest buildSimpleRequest(String jobName, String jobGroup,
                                                   String className, long intervalMs) {
        return JobScheduleRequest.builder()
                .jobName(jobName)
                .jobGroup(jobGroup)
                .jobClassName(className)
                .description("Simple trigger description")
                .trigger(TriggerConfig.builder()
                        .triggerType(TriggerConfig.TriggerType.SIMPLE)
                        .repeatIntervalMs(intervalMs)
                        .repeatCount(-1)
                        .build())
                .build();
    }
}
