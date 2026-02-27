package com.arcana.cloud.scheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO representing job details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("java:S1068")
public class JobDetailDto {

    private String jobName;
    private String jobGroup;
    private String description;
    private String jobClassName;
    private boolean isDurable;
    private boolean requestsRecovery;
    private Map<String, Object> jobData;
    private List<TriggerDetailDto> triggers;
    private JobStatus status;

    public enum JobStatus {
        NORMAL,
        PAUSED,
        BLOCKED,
        ERROR,
        NONE
    }
}
