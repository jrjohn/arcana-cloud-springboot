package com.arcana.cloud.scheduler;

import com.arcana.cloud.scheduler.job.SystemHealthCheckJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.*;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemHealthCheckJob Unit Tests")
@SuppressWarnings("java:S2187")
class SystemHealthCheckJobTest {

    private SystemHealthCheckJob job;
    private JobExecutionContext context;
    private JobDetail jobDetail;
    private JobDataMap jobDataMap;
    private Trigger trigger;

    @BeforeEach
    void setUp() throws Exception {
        job = new SystemHealthCheckJob();
        ReflectionTestUtils.setField(job, "deploymentMode", "monolithic");

        context = mock(JobExecutionContext.class);
        jobDetail = mock(JobDetail.class);
        trigger = mock(Trigger.class);
        jobDataMap = new JobDataMap();

        Scheduler scheduler = mock(Scheduler.class);
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(context.getTrigger()).thenReturn(trigger);
        when(context.getScheduler()).thenReturn(scheduler);
        when(jobDetail.getKey()).thenReturn(new JobKey("systemHealthCheck", "SYSTEM"));
        when(trigger.getKey()).thenReturn(new TriggerKey("systemHealthCheckTrigger", "SYSTEM"));
        when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
        when(scheduler.getSchedulerInstanceId()).thenReturn("NODE-1");
    }

    @Test
    @DisplayName("Should execute health check without throwing exceptions")
    void execute_Success_NoException() {
        assertDoesNotThrow(() -> job.execute(context));
    }

    @Test
    @DisplayName("Should populate memory metrics in job data map")
    void execute_PopulatesMemoryMetrics() throws JobExecutionException {
        job.execute(context);

        assertTrue(jobDataMap.containsKey("usedMemoryMB"),
                "Should record usedMemoryMB");
        assertTrue(jobDataMap.containsKey("freeMemoryMB"),
                "Should record freeMemoryMB");
        assertTrue(jobDataMap.containsKey("maxMemoryMB"),
                "Should record maxMemoryMB");
        assertTrue(jobDataMap.containsKey("memoryUsagePercent"),
                "Should record memoryUsagePercent");
    }

    @Test
    @DisplayName("Should populate processor count in job data map")
    void execute_PopulatesProcessorCount() throws JobExecutionException {
        job.execute(context);

        assertTrue(jobDataMap.containsKey("availableProcessors"),
                "Should record availableProcessors");
        int processors = (int) jobDataMap.get("availableProcessors");
        assertTrue(processors > 0, "Processor count should be positive");
    }

    @Test
    @DisplayName("Should record last check time in job data map")
    void execute_RecordsLastCheckTime() throws JobExecutionException {
        long before = System.currentTimeMillis();
        job.execute(context);
        long after = System.currentTimeMillis();

        assertTrue(jobDataMap.containsKey("lastCheckTime"),
                "Should record lastCheckTime");
        long lastCheckTime = (long) jobDataMap.get("lastCheckTime");
        assertTrue(lastCheckTime >= before && lastCheckTime <= after,
                "lastCheckTime should be within test window");
    }

    @Test
    @DisplayName("Should increment execution counter on each run")
    void execute_IncrementsExecutionCount() throws JobExecutionException {
        job.execute(context);
        int countAfterFirst = jobDataMap.getInt("executionCount");

        job.execute(context);
        int countAfterSecond = jobDataMap.getInt("executionCount");

        assertEquals(1, countAfterFirst, "Execution count should be 1 after first run");
        assertEquals(2, countAfterSecond, "Execution count should be 2 after second run");
    }

    @Test
    @DisplayName("Should record last execution time")
    void execute_RecordsLastExecutionTime() throws JobExecutionException {
        long before = System.currentTimeMillis();
        job.execute(context);
        long after = System.currentTimeMillis();

        assertTrue(jobDataMap.containsKey("lastExecutionTime"),
                "Should record lastExecutionTime");
        long lastExec = (long) jobDataMap.get("lastExecutionTime");
        assertTrue(lastExec >= before && lastExec <= after,
                "lastExecutionTime should be within test window");
    }

    @Test
    @DisplayName("Should report reasonable memory usage percentage")
    void execute_MemoryUsagePercentIsReasonable() throws JobExecutionException {
        job.execute(context);

        double usagePercent = (double) jobDataMap.get("memoryUsagePercent");
        assertTrue(usagePercent > 0 && usagePercent <= 100,
                "Memory usage percent should be between 0 and 100");
    }

    @Test
    @DisplayName("Should work in layered deployment mode")
    void execute_LayeredMode_Success() {
        ReflectionTestUtils.setField(job, "deploymentMode", "layered");
        assertDoesNotThrow(() -> job.execute(context));
    }

    @Test
    @DisplayName("Should work in k8s deployment mode")
    void execute_K8sMode_Success() {
        ReflectionTestUtils.setField(job, "deploymentMode", "k8s");
        assertDoesNotThrow(() -> job.execute(context));
    }
}
