package com.arcana.cloud.entity;

import com.arcana.cloud.entity.JobExecutionHistory.JobExecutionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JobExecutionHistoryTest {

    @Test
    void builder_allFields() {
        LocalDateTime firedTime = LocalDateTime.now().minusMinutes(5);
        LocalDateTime completedTime = LocalDateTime.now();

        JobExecutionHistory history = JobExecutionHistory.builder()
            .id(1L)
            .jobName("testJob")
            .jobGroup("testGroup")
            .triggerName("testTrigger")
            .triggerGroup("testTriggerGroup")
            .instanceName("instance1")
            .firedTime(firedTime)
            .completedTime(completedTime)
            .executionTimeMs(5000L)
            .status(JobExecutionStatus.COMPLETED)
            .errorMessage(null)
            .createdAt(firedTime)
            .build();

        assertThat(history.getId()).isEqualTo(1L);
        assertThat(history.getJobName()).isEqualTo("testJob");
        assertThat(history.getJobGroup()).isEqualTo("testGroup");
        assertThat(history.getTriggerName()).isEqualTo("testTrigger");
        assertThat(history.getTriggerGroup()).isEqualTo("testTriggerGroup");
        assertThat(history.getInstanceName()).isEqualTo("instance1");
        assertThat(history.getFiredTime()).isEqualTo(firedTime);
        assertThat(history.getCompletedTime()).isEqualTo(completedTime);
        assertThat(history.getExecutionTimeMs()).isEqualTo(5000L);
        assertThat(history.getStatus()).isEqualTo(JobExecutionStatus.COMPLETED);
        assertThat(history.getErrorMessage()).isNull();
    }

    @Test
    void allJobExecutionStatuses_enumValues() {
        assertThat(JobExecutionStatus.values()).containsExactlyInAnyOrder(
            JobExecutionStatus.STARTED,
            JobExecutionStatus.COMPLETED,
            JobExecutionStatus.FAILED,
            JobExecutionStatus.VETOED
        );
    }

    @Test
    void status_started() {
        JobExecutionHistory history = JobExecutionHistory.builder()
            .jobName("job").jobGroup("group").status(JobExecutionStatus.STARTED).build();
        assertThat(history.getStatus()).isEqualTo(JobExecutionStatus.STARTED);
    }

    @Test
    void status_failed_withErrorMessage() {
        JobExecutionHistory history = JobExecutionHistory.builder()
            .jobName("job").jobGroup("group")
            .status(JobExecutionStatus.FAILED)
            .errorMessage("NullPointerException at line 42")
            .build();

        assertThat(history.getStatus()).isEqualTo(JobExecutionStatus.FAILED);
        assertThat(history.getErrorMessage()).contains("NullPointerException");
    }

    @Test
    void status_vetoed() {
        JobExecutionHistory history = JobExecutionHistory.builder()
            .status(JobExecutionStatus.VETOED).build();
        assertThat(history.getStatus()).isEqualTo(JobExecutionStatus.VETOED);
    }

    @Test
    void noArgsConstructor_setters() {
        JobExecutionHistory history = new JobExecutionHistory();
        history.setId(5L);
        history.setJobName("myJob");
        history.setJobGroup("myGroup");
        history.setStatus(JobExecutionStatus.COMPLETED);
        history.setExecutionTimeMs(2000L);

        assertThat(history.getId()).isEqualTo(5L);
        assertThat(history.getJobName()).isEqualTo("myJob");
        assertThat(history.getStatus()).isEqualTo(JobExecutionStatus.COMPLETED);
        assertThat(history.getExecutionTimeMs()).isEqualTo(2000L);
    }

    @Test
    void equalsAndHashCode_sameFields() {
        LocalDateTime now = LocalDateTime.now();
        JobExecutionHistory a = JobExecutionHistory.builder()
            .id(1L).jobName("job").jobGroup("group")
            .firedTime(now).status(JobExecutionStatus.COMPLETED).build();
        JobExecutionHistory b = JobExecutionHistory.builder()
            .id(1L).jobName("job").jobGroup("group")
            .firedTime(now).status(JobExecutionStatus.COMPLETED).build();

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void equalsAndHashCode_differentFields_notEqual() {
        JobExecutionHistory a = JobExecutionHistory.builder().id(1L).build();
        JobExecutionHistory b = JobExecutionHistory.builder().id(2L).build();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void toString_containsJobName() {
        JobExecutionHistory history = JobExecutionHistory.builder()
            .jobName("specificJob").build();
        assertThat(history.toString()).contains("specificJob");
    }
}
