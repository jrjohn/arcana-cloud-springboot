package com.arcana.cloud.scheduler;

import com.arcana.cloud.scheduler.listener.JobExecutionHistoryListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.*;
import org.quartz.impl.matchers.EverythingMatcher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobInitializerTest {

    @Mock
    private Scheduler scheduler;

    @Mock
    private JobExecutionHistoryListener historyListener;

    @Mock
    private ListenerManager listenerManager;

    private JobInitializer jobInitializer;

    @BeforeEach
    void setUp() throws SchedulerException {
        jobInitializer = new JobInitializer(scheduler, historyListener);
        when(scheduler.getListenerManager()).thenReturn(listenerManager);
        doNothing().when(listenerManager).addJobListener(any(JobListener.class));
    }

    @Test
    void onApplicationReady_registersListenerAndDefaultJobs() throws SchedulerException {
        ReflectionTestUtils.setField(jobInitializer, "autoRegisterJobs", true);
        ReflectionTestUtils.setField(jobInitializer, "deploymentMode", "monolithic");

        when(scheduler.checkExists(any(JobKey.class))).thenReturn(false);
        when(scheduler.scheduleJob(any(JobDetail.class), any(Trigger.class)))
            .thenReturn(null);

        jobInitializer.onApplicationReady();

        verify(listenerManager).addJobListener(historyListener);
        verify(scheduler, atLeastOnce()).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    void onApplicationReady_autoRegisterDisabled_onlyRegistersListener() throws SchedulerException {
        ReflectionTestUtils.setField(jobInitializer, "autoRegisterJobs", false);
        ReflectionTestUtils.setField(jobInitializer, "deploymentMode", "monolithic");

        jobInitializer.onApplicationReady();

        verify(listenerManager).addJobListener(historyListener);
        verify(scheduler, never()).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    void onApplicationReady_jobsAlreadyExist_doesNotScheduleAgain() throws SchedulerException {
        ReflectionTestUtils.setField(jobInitializer, "autoRegisterJobs", true);
        ReflectionTestUtils.setField(jobInitializer, "deploymentMode", "monolithic");

        when(scheduler.checkExists(any(JobKey.class))).thenReturn(true);

        jobInitializer.onApplicationReady();

        verify(listenerManager).addJobListener(historyListener);
        verify(scheduler, never()).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    void onApplicationReady_schedulerThrows_logsErrorAndDoesNotCrash() throws SchedulerException {
        ReflectionTestUtils.setField(jobInitializer, "autoRegisterJobs", true);
        ReflectionTestUtils.setField(jobInitializer, "deploymentMode", "monolithic");

        when(scheduler.getListenerManager()).thenThrow(new SchedulerException("scheduler error"));

        // Should not throw
        jobInitializer.onApplicationReady();
    }

    @Test
    void onApplicationReady_partialJobExists_schedulesOnlyMissing() throws SchedulerException {
        ReflectionTestUtils.setField(jobInitializer, "autoRegisterJobs", true);
        ReflectionTestUtils.setField(jobInitializer, "deploymentMode", "monolithic");

        // First job (tokenCleanup) exists, second (systemHealthCheck) does not
        when(scheduler.checkExists(any(JobKey.class)))
            .thenReturn(true)  // first call: tokenCleanup exists
            .thenReturn(false); // second call: systemHealthCheck missing

        when(scheduler.scheduleJob(any(JobDetail.class), any(Trigger.class)))
            .thenReturn(null);

        jobInitializer.onApplicationReady();

        verify(scheduler, times(1)).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }
}
