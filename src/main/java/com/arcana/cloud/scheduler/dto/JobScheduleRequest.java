package com.arcana.cloud.scheduler.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Request DTO for scheduling a job.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobScheduleRequest {

    @NotBlank(message = "Job name is required")
    private String jobName;

    @NotBlank(message = "Job group is required")
    private String jobGroup;

    @NotBlank(message = "Job class name is required")
    private String jobClassName;

    private String description;

    /**
     * Whether the job should remain stored after all triggers are removed.
     */
    @Builder.Default
    private boolean durable = true;

    /**
     * Whether the job should be re-executed if a recovery is required.
     */
    @Builder.Default
    private boolean requestsRecovery = true;

    /**
     * Job data map to pass to the job.
     */
    private Map<String, Object> jobData;

    /**
     * Trigger configuration.
     */
    private TriggerConfig trigger;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TriggerConfig {

        private String triggerName;
        private String triggerGroup;
        private String description;

        /**
         * Type of trigger: CRON or SIMPLE.
         */
        @Builder.Default
        private TriggerType triggerType = TriggerType.CRON;

        /**
         * Cron expression (for CRON triggers).
         * Examples:
         * - "0 0 * * * ?" - every hour
         * - "0 0/5 * * * ?" - every 5 minutes
         * - "0 0 8 * * ?" - every day at 8 AM
         */
        private String cronExpression;

        /**
         * Time zone for cron expression (e.g., "America/New_York").
         */
        private String timeZone;

        /**
         * Repeat count for simple triggers (-1 for indefinite).
         */
        @Builder.Default
        private int repeatCount = -1;

        /**
         * Repeat interval in milliseconds for simple triggers.
         */
        private Long repeatIntervalMs;

        /**
         * When the trigger should start.
         */
        private LocalDateTime startTime;

        /**
         * When the trigger should end (null for no end).
         */
        private LocalDateTime endTime;

        /**
         * Trigger priority (higher number = higher priority).
         */
        @Builder.Default
        private int priority = 5;

        /**
         * Misfire instruction.
         */
        @Builder.Default
        private MisfireInstruction misfireInstruction = MisfireInstruction.SMART_POLICY;

        public enum TriggerType {
            CRON,
            SIMPLE
        }

        public enum MisfireInstruction {
            SMART_POLICY,
            IGNORE_MISFIRE_POLICY,
            FIRE_NOW,
            DO_NOTHING
        }
    }
}
