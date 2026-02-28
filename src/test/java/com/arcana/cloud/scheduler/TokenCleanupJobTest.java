package com.arcana.cloud.scheduler;

import com.arcana.cloud.dao.OAuthTokenDao;
import com.arcana.cloud.scheduler.job.TokenCleanupJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenCleanupJob Unit Tests")
@SuppressWarnings("java:S2187")
class TokenCleanupJobTest {

    @Mock
    private OAuthTokenDao tokenDao;

    @InjectMocks
    private TokenCleanupJob job;

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
        when(jobDetail.getKey()).thenReturn(new JobKey("tokenCleanup", "MAINTENANCE"));
        when(trigger.getKey()).thenReturn(new TriggerKey("tokenCleanupTrigger", "MAINTENANCE"));
        when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
        when(scheduler.getSchedulerInstanceId()).thenReturn("NODE-1");
    }

    @Test
    @DisplayName("Should execute token cleanup without exceptions")
    void execute_Success_NoException() {
        when(tokenDao.count()).thenReturn(100L, 80L);

        assertDoesNotThrow(() -> job.execute(context));
    }

    @Test
    @DisplayName("Should call count twice — before and after cleanup")
    void execute_CallsCountTwice() throws JobExecutionException {
        when(tokenDao.count()).thenReturn(50L, 40L);

        job.execute(context);

        verify(tokenDao, times(2)).count();
    }

    @Test
    @DisplayName("Should increment execution counter on each run")
    void execute_IncrementsExecutionCount() throws JobExecutionException {
        when(tokenDao.count()).thenReturn(10L, 10L, 10L, 10L);

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
        when(tokenDao.count()).thenReturn(25L, 20L);

        long before = System.currentTimeMillis();
        job.execute(context);
        long after = System.currentTimeMillis();

        assertTrue(jobDataMap.containsKey("lastExecutionTime"),
                "Should set lastExecutionTime in job data");
        long lastExec = (long) jobDataMap.get("lastExecutionTime");
        assertTrue(lastExec >= before && lastExec <= after,
                "lastExecutionTime should be within test window");
    }

    @Test
    @DisplayName("Should use default retention period of 30 days when not configured")
    void execute_DefaultRetentionDays_Success() {
        // No retentionDays set in jobDataMap → defaults to 30
        when(tokenDao.count()).thenReturn(0L, 0L);

        assertDoesNotThrow(() -> job.execute(context));
    }

    @Test
    @DisplayName("Should use configured retention period from job data")
    void execute_CustomRetentionDays_Success() {
        jobDataMap.put("retentionDays", 7);
        when(tokenDao.count()).thenReturn(200L, 150L);

        assertDoesNotThrow(() -> job.execute(context));
        verify(tokenDao, times(2)).count();
    }

    @Test
    @DisplayName("Should handle zero total tokens gracefully")
    void execute_ZeroTokens_Success() {
        when(tokenDao.count()).thenReturn(0L, 0L);

        assertDoesNotThrow(() -> job.execute(context));
    }

    @Test
    @DisplayName("Should wrap DAO exception in JobExecutionException")
    void execute_DaoThrows_WrapsInJobExecutionException() {
        when(tokenDao.count()).thenThrow(new RuntimeException("DB unavailable"));

        assertThrows(JobExecutionException.class, () -> job.execute(context));
    }

    @Test
    @DisplayName("Should handle large token counts without overflow")
    void execute_LargeTokenCount_Success() {
        when(tokenDao.count()).thenReturn(1_000_000L, 999_000L);

        assertDoesNotThrow(() -> job.execute(context));
    }
}
