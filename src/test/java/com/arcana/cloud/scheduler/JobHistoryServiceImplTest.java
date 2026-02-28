package com.arcana.cloud.scheduler;

import com.arcana.cloud.entity.JobExecutionHistory;
import com.arcana.cloud.entity.JobExecutionHistory.JobExecutionStatus;
import com.arcana.cloud.scheduler.mapper.JobExecutionHistoryMapper;
import com.arcana.cloud.scheduler.service.impl.JobHistoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobHistoryServiceImplTest {

    @Mock
    private JobExecutionHistoryMapper historyMapper;

    @InjectMocks
    private JobHistoryServiceImpl jobHistoryService;

    private JobExecutionHistory testHistory;

    @BeforeEach
    void setUp() {
        testHistory = JobExecutionHistory.builder()
            .id(1L)
            .jobName("testJob")
            .jobGroup("testGroup")
            .triggerName("testTrigger")
            .triggerGroup("testTriggerGroup")
            .instanceName("instance1")
            .firedTime(LocalDateTime.now())
            .status(JobExecutionStatus.STARTED)
            .build();
    }

    @Test
    void testRecordJobStart() {
        doAnswer(inv -> {
            JobExecutionHistory h = inv.getArgument(0);
            h.setId(1L);
            return null;
        }).when(historyMapper).insert(any(JobExecutionHistory.class));

        Long id = jobHistoryService.recordJobStart(testHistory);

        assertNotNull(id);
        assertEquals(1L, id);
        verify(historyMapper).insert(testHistory);
    }

    @Test
    void testRecordJobCompletion() {
        LocalDateTime completedTime = LocalDateTime.now();
        jobHistoryService.recordJobCompletion(1L, completedTime, 500L,
            JobExecutionStatus.COMPLETED, null);

        verify(historyMapper).updateCompletion(1L, completedTime, 500L,
            JobExecutionStatus.COMPLETED, null);
    }

    @Test
    void testGetJobHistory() {
        List<JobExecutionHistory> content = List.of(testHistory);
        when(historyMapper.findByJob(anyString(), anyString(), anyInt(), anyInt()))
            .thenReturn(content);
        when(historyMapper.countByJob(anyString(), anyString())).thenReturn(1L);

        Pageable pageable = PageRequest.of(0, 10);
        Page<JobExecutionHistory> result = jobHistoryService.getJobHistory("testJob", "testGroup", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("testJob", result.getContent().get(0).getJobName());
    }

    @Test
    void testGetAllHistory() {
        List<JobExecutionHistory> content = List.of(testHistory);
        when(historyMapper.findAll(anyInt(), anyInt())).thenReturn(content);
        when(historyMapper.countAll()).thenReturn(1L);

        Pageable pageable = PageRequest.of(0, 10);
        Page<JobExecutionHistory> result = jobHistoryService.getAllHistory(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void testGetRecentHistory() {
        when(historyMapper.findRecent(5)).thenReturn(List.of(testHistory));

        List<JobExecutionHistory> result = jobHistoryService.getRecentHistory(5);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testGetHistoryByStatus() {
        List<JobExecutionHistory> content = List.of(testHistory);
        when(historyMapper.findByStatus(any(), anyInt(), anyInt())).thenReturn(content);
        when(historyMapper.countByStatus(any())).thenReturn(1L);

        Pageable pageable = PageRequest.of(0, 10);
        Page<JobExecutionHistory> result = jobHistoryService.getHistoryByStatus(
            JobExecutionStatus.COMPLETED, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void testGetHistoryByTimeRange() {
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now();
        List<JobExecutionHistory> content = List.of(testHistory);
        when(historyMapper.findByTimeRange(any(), any(), anyInt(), anyInt())).thenReturn(content);
        when(historyMapper.countByTimeRange(any(), any())).thenReturn(1L);

        Pageable pageable = PageRequest.of(0, 10);
        Page<JobExecutionHistory> result = jobHistoryService.getHistoryByTimeRange(start, end, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void testGetById_Found() {
        when(historyMapper.findById(1L)).thenReturn(testHistory);

        Optional<JobExecutionHistory> result = jobHistoryService.getById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
    }

    @Test
    void testGetById_NotFound() {
        when(historyMapper.findById(anyLong())).thenReturn(null);

        Optional<JobExecutionHistory> result = jobHistoryService.getById(999L);

        assertFalse(result.isPresent());
    }

    @Test
    void testDeleteOldHistory() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        when(historyMapper.deleteOlderThan(cutoff)).thenReturn(5);

        int deleted = jobHistoryService.deleteOldHistory(cutoff);

        assertEquals(5, deleted);
        verify(historyMapper).deleteOlderThan(cutoff);
    }

    @Test
    void testGetStatistics_ForSpecificJob() {
        Map<String, Object> statsData = new HashMap<>();
        statsData.put("total_executions", 100L);
        statsData.put("completed_count", 95L);
        statsData.put("failed_count", 5L);
        statsData.put("vetoed_count", 0L);
        statsData.put("avg_execution_time_ms", 250.0);
        statsData.put("min_execution_time_ms", 100L);
        statsData.put("max_execution_time_ms", 500L);

        when(historyMapper.getStatisticsByJob(anyString(), anyString(), any(), any()))
            .thenReturn(statsData);

        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();

        var stats = jobHistoryService.getStatistics("testJob", "testGroup", start, end);

        assertNotNull(stats);
        assertEquals(100L, stats.totalExecutions());
        assertEquals(95L, stats.completedCount());
        assertEquals(5L, stats.failedCount());
    }

    @Test
    void testGetStatistics_AllJobs() {
        Map<String, Object> statsData = new HashMap<>();
        statsData.put("total_executions", 200L);
        statsData.put("completed_count", 190L);
        statsData.put("failed_count", 10L);
        statsData.put("vetoed_count", 0L);
        statsData.put("avg_execution_time_ms", 300.0);
        statsData.put("min_execution_time_ms", null);
        statsData.put("max_execution_time_ms", null);

        when(historyMapper.getStatisticsAll(any(), any())).thenReturn(statsData);

        var stats = jobHistoryService.getStatistics(null, null, null, null);

        assertNotNull(stats);
        assertEquals(200L, stats.totalExecutions());
    }

    @Test
    void testGetStatistics_WithNullValues() {
        Map<String, Object> statsData = new HashMap<>();
        statsData.put("total_executions", null);
        statsData.put("completed_count", null);
        statsData.put("failed_count", null);
        statsData.put("vetoed_count", null);
        statsData.put("avg_execution_time_ms", null);
        statsData.put("min_execution_time_ms", null);
        statsData.put("max_execution_time_ms", null);

        when(historyMapper.getStatisticsAll(any(), any())).thenReturn(statsData);

        var stats = jobHistoryService.getStatistics(null, null, null, null);

        assertNotNull(stats);
        assertEquals(0L, stats.totalExecutions());
        assertEquals(0L, stats.completedCount());
    }
}
