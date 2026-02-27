package com.arcana.cloud.scheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO representing trigger details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("java:S1068")
public class TriggerDetailDto {

    private String triggerName;
    private String triggerGroup;
    private String description;
    private TriggerType triggerType;
    private TriggerState state;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime previousFireTime;
    private LocalDateTime nextFireTime;
    private int priority;
    private String calendarName;
    private int misfireInstruction;

    // Cron trigger specific
    private String cronExpression;
    private String timeZone;

    // Simple trigger specific
    private Integer repeatCount;
    private Long repeatIntervalMs;
    private Integer timesTriggered;

    public enum TriggerType {
        CRON,
        SIMPLE,
        CALENDAR_INTERVAL,
        DAILY_TIME_INTERVAL
    }

    public enum TriggerState {
        NONE,
        NORMAL,
        PAUSED,
        COMPLETE,
        ERROR,
        BLOCKED
    }
}
