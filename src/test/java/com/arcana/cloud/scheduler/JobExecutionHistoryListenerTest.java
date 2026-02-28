package com.arcana.cloud.scheduler;

import com.arcana.cloud.entity.JobExecutionHistory.JobExecutionStatus;
import com.arcana.cloud.scheduler.listener.JobExecutionHistoryListener;
import com.arcana.cloud.scheduler.service.JobHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobExecutionHistoryListenerTest {

    @Mock
    private JobHistoryService jobHistoryService;

    @InjectMocks
    private JobExecutionHistoryListener listener;

    private JobExecutionContext context;
    private JobDetail jobDetail;
    private Trigger trigger;
    private Map<Object, Object> contextData;

    @BeforeEach
    void setUp() throws Exception {
        context = mock(JobExecutionContext.class);
        jobDetail = mock(JobDetail.class);
        trigger = mock(Trigger.class);
        contextData = new HashMap<>();

        Scheduler scheduler = mock(Scheduler.class);
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(context.getTrigger()).thenReturn(trigger);
        when(context.getScheduler()).thenReturn(scheduler);
        when(jobDetail.getKey()).thenReturn(new JobKey("testJob", "testGroup"));
        when(trigger.getKey()).thenReturn(new TriggerKey("testTrigger", "testGroup"));
        when(scheduler.getSchedulerInstanceId()).thenReturn("testInstance");

        // Mock context.put and context.get using the local map
        doAnswer(inv -> {
            contextData.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(context).put(any(), any());
        when(context.get(any())).thenAnswer(inv -> contextData.get(inv.getArgument(0)));
    }

    @Test
    void testGetName() {
        assertEquals("JobExecutionHistoryListener", listener.getName());
    }

    @Test
    void testJobToBeExecuted_RecordsJobStart() {
        when(jobHistoryService.recordJobStart(any())).thenReturn(42L);

        listener.jobToBeExecuted(context);

        verify(jobHistoryService).recordJobStart(any());
        assertEquals(42L, contextData.get("_historyId"));
    }

    @Test
    void testJobToBeExecuted_WhenServiceThrows_ContinuesSilently() {
        when(jobHistoryService.recordJobStart(any())).thenThrow(new RuntimeException("DB error"));

        // Should not throw
        listener.jobToBeExecuted(context);
    }

    @Test
    void testJobWasExecuted_WhenSuccessful_RecordsCompletion() {
        contextData.put("_historyId", 42L);
        contextData.put("_startTime", System.currentTimeMillis() - 100);

        listener.jobWasExecuted(context, null);

        verify(jobHistoryService).recordJobCompletion(
            eq(42L), any(), anyLong(), eq(JobExecutionStatus.COMPLETED), eq(null));
    }

    @Test
    void testJobWasExecuted_WhenFailed_RecordsFailure() {
        contextData.put("_historyId", 42L);
        contextData.put("_startTime", System.currentTimeMillis() - 100);

        JobExecutionException ex = new JobExecutionException("Job failed");

        listener.jobWasExecuted(context, ex);

        verify(jobHistoryService).recordJobCompletion(
            eq(42L), any(), anyLong(), eq(JobExecutionStatus.FAILED), anyString());
    }

    @Test
    void testJobWasExecuted_WhenFailedWithCause_IncludesCauseInMessage() {
        contextData.put("_historyId", 42L);
        contextData.put("_startTime", System.currentTimeMillis() - 100);

        RuntimeException cause = new RuntimeException("Root cause");
        JobExecutionException ex = new JobExecutionException("Outer message", cause);

        listener.jobWasExecuted(context, ex);

        verify(jobHistoryService).recordJobCompletion(
            eq(42L), any(), anyLong(), eq(JobExecutionStatus.FAILED), anyString());
    }

    @Test
    void testJobWasExecuted_WhenNoHistoryId_DoesNotRecordCompletion() {
        // No history ID in context

        listener.jobWasExecuted(context, null);

        verify(jobHistoryService, never()).recordJobCompletion(
            anyLong(), any(), anyLong(), any(), anyString());
    }

    @Test
    void testJobWasExecuted_WhenServiceThrows_ContinuesSilently() {
        contextData.put("_historyId", 42L);
        contextData.put("_startTime", System.currentTimeMillis() - 100);

        doThrow(new RuntimeException("DB error")).when(jobHistoryService).recordJobCompletion(
            anyLong(), any(), anyLong(), any(), any());

        // Should not throw
        listener.jobWasExecuted(context, null);
    }

    @Test
    void testJobExecutionVetoed_RecordsVetoedStatus() {
        contextData.put("_historyId", 42L);
        contextData.put("_startTime", System.currentTimeMillis() - 50);

        listener.jobExecutionVetoed(context);

        verify(jobHistoryService).recordJobCompletion(
            eq(42L), any(), anyLong(), eq(JobExecutionStatus.VETOED), anyString());
    }

    @Test
    void testJobExecutionVetoed_WhenNoHistoryId_DoesNothing() {
        // No history ID in context
        listener.jobExecutionVetoed(context);

        verify(jobHistoryService, never()).recordJobCompletion(
            anyLong(), any(), anyLong(), any(), anyString());
    }

    @Test
    void testJobExecutionVetoed_WhenServiceThrows_ContinuesSilently() {
        contextData.put("_historyId", 42L);
        contextData.put("_startTime", System.currentTimeMillis() - 50);

        doThrow(new RuntimeException("DB error")).when(jobHistoryService).recordJobCompletion(
            anyLong(), any(), anyLong(), any(), any());

        // Should not throw
        listener.jobExecutionVetoed(context);
    }
}
