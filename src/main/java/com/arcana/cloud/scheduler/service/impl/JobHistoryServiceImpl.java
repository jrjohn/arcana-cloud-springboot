package com.arcana.cloud.scheduler.service.impl;

import com.arcana.cloud.entity.JobExecutionHistory;
import com.arcana.cloud.entity.JobExecutionHistory.JobExecutionStatus;
import com.arcana.cloud.scheduler.mapper.JobExecutionHistoryMapper;
import com.arcana.cloud.scheduler.service.JobHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of JobHistoryService using MyBatis.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "quartz.enabled", havingValue = "true", matchIfMissing = false)
public class JobHistoryServiceImpl implements JobHistoryService {

    private final JobExecutionHistoryMapper historyMapper;

    @Override
    @Transactional
    public Long recordJobStart(JobExecutionHistory history) {
        historyMapper.insert(history);
        log.debug("Recorded job start: {}.{} with ID {}",
                 history.getJobGroup(), history.getJobName(), history.getId());
        return history.getId();
    }

    @Override
    @Transactional
    public void recordJobCompletion(Long historyId, LocalDateTime completedTime,
                                   Long executionTimeMs, JobExecutionStatus status,
                                   String errorMessage) {
        historyMapper.updateCompletion(historyId, completedTime, executionTimeMs, status, errorMessage);
        log.debug("Recorded job completion for ID {}: status={}, duration={}ms",
                 historyId, status, executionTimeMs);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JobExecutionHistory> getJobHistory(String jobName, String jobGroup, Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();

        List<JobExecutionHistory> content = historyMapper.findByJob(jobName, jobGroup, limit, offset);
        long total = historyMapper.countByJob(jobName, jobGroup);

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JobExecutionHistory> getAllHistory(Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();

        List<JobExecutionHistory> content = historyMapper.findAll(limit, offset);
        long total = historyMapper.countAll();

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobExecutionHistory> getRecentHistory(int limit) {
        return historyMapper.findRecent(limit);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JobExecutionHistory> getHistoryByStatus(JobExecutionStatus status, Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();

        List<JobExecutionHistory> content = historyMapper.findByStatus(status, limit, offset);
        long total = historyMapper.countByStatus(status);

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JobExecutionHistory> getHistoryByTimeRange(LocalDateTime startTime,
                                                           LocalDateTime endTime,
                                                           Pageable pageable) {
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();

        List<JobExecutionHistory> content = historyMapper.findByTimeRange(startTime, endTime, limit, offset);
        long total = historyMapper.countByTimeRange(startTime, endTime);

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JobExecutionHistory> getById(Long id) {
        return Optional.ofNullable(historyMapper.findById(id));
    }

    @Override
    @Transactional
    public int deleteOldHistory(LocalDateTime olderThan) {
        int deleted = historyMapper.deleteOlderThan(olderThan);
        log.info("Deleted {} old job history records older than {}", deleted, olderThan);
        return deleted;
    }

    @Override
    @Transactional(readOnly = true)
    public JobExecutionStats getStatistics(String jobName, String jobGroup,
                                           LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats;

        if (jobName != null && jobGroup != null) {
            stats = historyMapper.getStatisticsByJob(jobName, jobGroup, startTime, endTime);
        } else {
            stats = historyMapper.getStatisticsAll(startTime, endTime);
        }

        return new JobExecutionStats(
                toLong(stats.get("total_executions")),
                toLong(stats.get("completed_count")),
                toLong(stats.get("failed_count")),
                toLong(stats.get("vetoed_count")),
                toDouble(stats.get("avg_execution_time_ms")),
                toLongOrNull(stats.get("min_execution_time_ms")),
                toLongOrNull(stats.get("max_execution_time_ms"))
        );
    }

    private long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }

    private Long toLongOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }

    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof BigDecimal) return ((BigDecimal) value).doubleValue();
        return Double.parseDouble(value.toString());
    }
}
