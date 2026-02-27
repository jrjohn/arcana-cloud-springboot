package com.arcana.cloud.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing job execution history for monitoring and auditing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("java:S1068")
public class JobExecutionHistory {

    private Long id;
    private String jobName;
    private String jobGroup;
    private String triggerName;
    private String triggerGroup;
    private String instanceName;
    private LocalDateTime firedTime;
    private LocalDateTime completedTime;
    private Long executionTimeMs;
    private JobExecutionStatus status;
    private String errorMessage;
    private LocalDateTime createdAt;

    public enum JobExecutionStatus {
        STARTED,
        COMPLETED,
        FAILED,
        VETOED
    }
}
