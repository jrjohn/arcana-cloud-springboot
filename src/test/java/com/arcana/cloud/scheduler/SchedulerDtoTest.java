package com.arcana.cloud.scheduler;

import com.arcana.cloud.scheduler.dto.JobDetailDto;
import com.arcana.cloud.scheduler.dto.JobDetailDto.JobStatus;
import com.arcana.cloud.scheduler.dto.JobScheduleRequest;
import com.arcana.cloud.scheduler.dto.JobScheduleRequest.TriggerConfig;
import com.arcana.cloud.scheduler.dto.JobScheduleRequest.TriggerConfig.MisfireInstruction;
import com.arcana.cloud.scheduler.dto.JobScheduleRequest.TriggerConfig.TriggerType;
import com.arcana.cloud.scheduler.dto.TriggerDetailDto;
import com.arcana.cloud.scheduler.dto.TriggerDetailDto.TriggerState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerDtoTest {

    @Test
    void jobDetailDto_builderAndGetters() {
        JobDetailDto dto = JobDetailDto.builder()
            .jobName("testJob")
            .jobGroup("testGroup")
            .description("Test description")
            .jobClassName("com.example.TestJob")
            .isDurable(true)
            .requestsRecovery(false)
            .jobData(Map.of("key", "value"))
            .triggers(List.of())
            .status(JobStatus.NORMAL)
            .build();

        assertThat(dto.getJobName()).isEqualTo("testJob");
        assertThat(dto.getJobGroup()).isEqualTo("testGroup");
        assertThat(dto.getDescription()).isEqualTo("Test description");
        assertThat(dto.getJobClassName()).isEqualTo("com.example.TestJob");
        assertThat(dto.isDurable()).isTrue();
        assertThat(dto.isRequestsRecovery()).isFalse();
        assertThat(dto.getJobData()).containsKey("key");
        assertThat(dto.getTriggers()).isEmpty();
        assertThat(dto.getStatus()).isEqualTo(JobStatus.NORMAL);
    }

    @Test
    void jobDetailDto_allJobStatuses() {
        for (JobStatus status : JobStatus.values()) {
            JobDetailDto dto = JobDetailDto.builder().status(status).build();
            assertThat(dto.getStatus()).isEqualTo(status);
        }
    }

    @Test
    void jobDetailDto_noArgsConstructor_setters() {
        JobDetailDto dto = new JobDetailDto();
        dto.setJobName("job1");
        dto.setJobGroup("group1");
        dto.setStatus(JobStatus.PAUSED);

        assertThat(dto.getJobName()).isEqualTo("job1");
        assertThat(dto.getJobGroup()).isEqualTo("group1");
        assertThat(dto.getStatus()).isEqualTo(JobStatus.PAUSED);
    }

    @Test
    void jobDetailDto_equalsAndHashCode() {
        JobDetailDto a = JobDetailDto.builder().jobName("job").jobGroup("group").build();
        JobDetailDto b = JobDetailDto.builder().jobName("job").jobGroup("group").build();
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void jobDetailDto_toString_containsFields() {
        JobDetailDto dto = JobDetailDto.builder().jobName("myJob").build();
        assertThat(dto.toString()).contains("myJob");
    }

    // ==================== JobScheduleRequest ====================

    @Test
    void jobScheduleRequest_defaultValues() {
        JobScheduleRequest request = new JobScheduleRequest();

        assertThat(request.isDurable()).isTrue();
        assertThat(request.isRequestsRecovery()).isTrue();
    }

    @Test
    void jobScheduleRequest_builderWithAllFields() {
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime endTime = startTime.plusHours(1);

        TriggerConfig triggerConfig = TriggerConfig.builder()
            .triggerName("trigger1")
            .triggerGroup("group1")
            .description("Test trigger")
            .triggerType(TriggerType.CRON)
            .cronExpression("0 0 * * * ?")
            .timeZone("UTC")
            .repeatCount(5)
            .repeatIntervalMs(60000L)
            .startTime(startTime)
            .endTime(endTime)
            .priority(7)
            .misfireInstruction(MisfireInstruction.FIRE_NOW)
            .build();

        JobScheduleRequest request = JobScheduleRequest.builder()
            .jobName("testJob")
            .jobGroup("testGroup")
            .jobClassName("com.example.TestJob")
            .description("Test job")
            .durable(false)
            .requestsRecovery(false)
            .jobData(Map.of("param", "value"))
            .trigger(triggerConfig)
            .build();

        assertThat(request.getJobName()).isEqualTo("testJob");
        assertThat(request.getJobGroup()).isEqualTo("testGroup");
        assertThat(request.getJobClassName()).isEqualTo("com.example.TestJob");
        assertThat(request.isDurable()).isFalse();
        assertThat(request.isRequestsRecovery()).isFalse();
        assertThat(request.getJobData()).containsEntry("param", "value");
        assertThat(request.getTrigger()).isNotNull();
        assertThat(request.getTrigger().getCronExpression()).isEqualTo("0 0 * * * ?");
        assertThat(request.getTrigger().getPriority()).isEqualTo(7);
    }

    @Test
    void triggerConfig_defaultValues() {
        TriggerConfig config = new TriggerConfig();

        assertThat(config.getTriggerType()).isEqualTo(TriggerType.CRON);
        assertThat(config.getRepeatCount()).isEqualTo(-1);
        assertThat(config.getPriority()).isEqualTo(5);
        assertThat(config.getMisfireInstruction()).isEqualTo(MisfireInstruction.SMART_POLICY);
    }

    @Test
    void triggerConfig_allTriggerTypes() {
        for (TriggerType type : TriggerType.values()) {
            TriggerConfig config = TriggerConfig.builder().triggerType(type).build();
            assertThat(config.getTriggerType()).isEqualTo(type);
        }
    }

    @Test
    void triggerConfig_allMisfireInstructions() {
        for (MisfireInstruction instruction : MisfireInstruction.values()) {
            TriggerConfig config = TriggerConfig.builder().misfireInstruction(instruction).build();
            assertThat(config.getMisfireInstruction()).isEqualTo(instruction);
        }
    }

    @Test
    void triggerConfig_simpleTrigger() {
        TriggerConfig config = TriggerConfig.builder()
            .triggerType(TriggerType.SIMPLE)
            .repeatCount(10)
            .repeatIntervalMs(5000L)
            .build();

        assertThat(config.getTriggerType()).isEqualTo(TriggerType.SIMPLE);
        assertThat(config.getRepeatCount()).isEqualTo(10);
        assertThat(config.getRepeatIntervalMs()).isEqualTo(5000L);
    }

    // ==================== TriggerDetailDto ====================

    @Test
    void triggerDetailDto_builderAndGetters() {
        LocalDateTime now = LocalDateTime.now();

        TriggerDetailDto dto = TriggerDetailDto.builder()
            .triggerName("trigger1")
            .triggerGroup("group1")
            .description("Test trigger")
            .triggerType(TriggerDetailDto.TriggerType.CRON)
            .state(TriggerState.NORMAL)
            .startTime(now)
            .endTime(now.plusHours(1))
            .previousFireTime(now.minusMinutes(5))
            .nextFireTime(now.plusMinutes(5))
            .priority(5)
            .calendarName("myCalendar")
            .misfireInstruction(1)
            .cronExpression("0 0 * * * ?")
            .timeZone("UTC")
            .repeatCount(3)
            .repeatIntervalMs(60000L)
            .timesTriggered(2)
            .build();

        assertThat(dto.getTriggerName()).isEqualTo("trigger1");
        assertThat(dto.getTriggerGroup()).isEqualTo("group1");
        assertThat(dto.getTriggerType()).isEqualTo(TriggerDetailDto.TriggerType.CRON);
        assertThat(dto.getState()).isEqualTo(TriggerState.NORMAL);
        assertThat(dto.getCronExpression()).isEqualTo("0 0 * * * ?");
        assertThat(dto.getRepeatCount()).isEqualTo(3);
        assertThat(dto.getRepeatIntervalMs()).isEqualTo(60000L);
    }

    @Test
    void triggerDetailDto_allTriggerTypes() {
        for (TriggerDetailDto.TriggerType type : TriggerDetailDto.TriggerType.values()) {
            TriggerDetailDto dto = TriggerDetailDto.builder().triggerType(type).build();
            assertThat(dto.getTriggerType()).isEqualTo(type);
        }
    }

    @Test
    void triggerDetailDto_allTriggerStates() {
        for (TriggerState state : TriggerState.values()) {
            TriggerDetailDto dto = TriggerDetailDto.builder().state(state).build();
            assertThat(dto.getState()).isEqualTo(state);
        }
    }

    @Test
    void triggerDetailDto_noArgsConstructor_setters() {
        TriggerDetailDto dto = new TriggerDetailDto();
        dto.setTriggerName("t");
        dto.setState(TriggerState.PAUSED);
        dto.setTriggerType(TriggerDetailDto.TriggerType.SIMPLE);

        assertThat(dto.getTriggerName()).isEqualTo("t");
        assertThat(dto.getState()).isEqualTo(TriggerState.PAUSED);
    }

    @Test
    void triggerDetailDto_equalsHashCodeToString() {
        TriggerDetailDto a = TriggerDetailDto.builder().triggerName("t").triggerGroup("g").build();
        TriggerDetailDto b = TriggerDetailDto.builder().triggerName("t").triggerGroup("g").build();
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("t");
    }
}
