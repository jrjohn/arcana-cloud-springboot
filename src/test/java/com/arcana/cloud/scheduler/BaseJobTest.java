package com.arcana.cloud.scheduler;

import com.arcana.cloud.scheduler.job.BaseJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BaseJobTest {

    private JobExecutionContext context;
    private JobDetail jobDetail;
    private JobDataMap jobDataMap;
    private Trigger trigger;

    @BeforeEach
    void setUp() throws Exception {
        context = mock(JobExecutionContext.class);
        jobDetail = mock(JobDetail.class);
        trigger = mock(Trigger.class);
        jobDataMap = new JobDataMap();

        Scheduler scheduler = mock(Scheduler.class);
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(context.getTrigger()).thenReturn(trigger);
        when(context.getScheduler()).thenReturn(scheduler);
        when(jobDetail.getKey()).thenReturn(new JobKey("testJob", "testGroup"));
        when(trigger.getKey()).thenReturn(new TriggerKey("testTrigger", "testGroup"));
        when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
        when(scheduler.getSchedulerInstanceId()).thenReturn("testInstance");
    }

    @Test
    void testExecute_Success() throws JobExecutionException {
        TestSuccessJob job = new TestSuccessJob();
        job.execute(context);
        // No exception should be thrown
    }

    @Test
    void testExecute_ThrowsException_WrapsInJobExecutionException() {
        TestFailingJob job = new TestFailingJob();
        assertThrows(JobExecutionException.class, () -> job.execute(context));
    }

    @Test
    void testGetInt_WithValue() {
        jobDataMap.put("count", 42);
        TestSuccessJob job = new TestSuccessJob();
        int result = job.testGetInt(jobDataMap, "count", 0);
        assertEquals(42, result);
    }

    @Test
    void testGetInt_DefaultValue() {
        TestSuccessJob job = new TestSuccessJob();
        int result = job.testGetInt(jobDataMap, "missingKey", 10);
        assertEquals(10, result);
    }

    @Test
    void testGetLong_WithValue() {
        jobDataMap.put("longVal", 100L);
        TestSuccessJob job = new TestSuccessJob();
        long result = job.testGetLong(jobDataMap, "longVal", 0L);
        assertEquals(100L, result);
    }

    @Test
    void testGetLong_DefaultValue() {
        TestSuccessJob job = new TestSuccessJob();
        long result = job.testGetLong(jobDataMap, "missingKey", 99L);
        assertEquals(99L, result);
    }

    @Test
    void testGetBoolean_WithValue() {
        jobDataMap.put("flag", true);
        TestSuccessJob job = new TestSuccessJob();
        boolean result = job.testGetBoolean(jobDataMap, "flag", false);
        assertEquals(true, result);
    }

    @Test
    void testGetBoolean_DefaultValue() {
        TestSuccessJob job = new TestSuccessJob();
        boolean result = job.testGetBoolean(jobDataMap, "missingKey", true);
        assertEquals(true, result);
    }

    @Test
    void testGetOptionalString_WithValue() {
        jobDataMap.put("key", "value");
        TestSuccessJob job = new TestSuccessJob();
        String result = job.testGetOptionalString(jobDataMap, "key", "default");
        assertEquals("value", result);
    }

    @Test
    void testGetOptionalString_DefaultValue() {
        TestSuccessJob job = new TestSuccessJob();
        String result = job.testGetOptionalString(jobDataMap, "missingKey", "default");
        assertEquals("default", result);
    }

    @Test
    void testGetRequiredString_WithValue() throws JobExecutionException {
        jobDataMap.put("required", "value");
        TestSuccessJob job = new TestSuccessJob();
        String result = job.testGetRequiredString(jobDataMap, "required");
        assertEquals("value", result);
    }

    @Test
    void testGetRequiredString_ThrowsWhenMissing() {
        TestSuccessJob job = new TestSuccessJob();
        assertThrows(JobExecutionException.class,
            () -> job.testGetRequiredString(jobDataMap, "missingKey"));
    }

    @Test
    void testIncrementCounter() throws JobExecutionException {
        TestSuccessJob job = new TestSuccessJob();
        job.testIncrementCounter(jobDataMap, "counter");
        assertEquals(1, jobDataMap.getInt("counter"));
        job.testIncrementCounter(jobDataMap, "counter");
        assertEquals(2, jobDataMap.getInt("counter"));
    }

    @Test
    void testRecordLastExecution() throws JobExecutionException {
        TestSuccessJob job = new TestSuccessJob();
        long before = System.currentTimeMillis();
        job.testRecordLastExecution(jobDataMap);
        long after = System.currentTimeMillis();

        long recorded = jobDataMap.getLong("lastExecutionTime");
        assertEquals(true, recorded >= before && recorded <= after);
    }

    // Test implementations of BaseJob for testing
    static class TestSuccessJob extends BaseJob {
        @Override
        protected void doExecute(JobExecutionContext context) {
            // Success - do nothing
        }

        public int testGetInt(JobDataMap dataMap, String key, int defaultValue) {
            return getInt(dataMap, key, defaultValue);
        }

        public long testGetLong(JobDataMap dataMap, String key, long defaultValue) {
            return getLong(dataMap, key, defaultValue);
        }

        public boolean testGetBoolean(JobDataMap dataMap, String key, boolean defaultValue) {
            return getBoolean(dataMap, key, defaultValue);
        }

        public String testGetOptionalString(JobDataMap dataMap, String key, String defaultValue) {
            return getOptionalString(dataMap, key, defaultValue);
        }

        public String testGetRequiredString(JobDataMap dataMap, String key) throws JobExecutionException {
            return getRequiredString(dataMap, key);
        }

        public void testIncrementCounter(JobDataMap dataMap, String key) {
            incrementCounter(dataMap, key);
        }

        public void testRecordLastExecution(JobDataMap dataMap) {
            recordLastExecution(dataMap);
        }
    }

    static class TestFailingJob extends BaseJob {
        @Override
        protected void doExecute(JobExecutionContext context) throws Exception {
            throw new RuntimeException("Job execution failed for testing");
        }
    }
}
