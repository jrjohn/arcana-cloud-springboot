package com.arcana.cloud.scheduler.mapper;

import com.arcana.cloud.entity.JobExecutionHistory;
import com.arcana.cloud.entity.JobExecutionHistory.JobExecutionStatus;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MyBatis mapper for job execution history.
 */
@Mapper
public interface JobExecutionHistoryMapper {

    @Insert("""
        INSERT INTO job_execution_history
        (job_name, job_group, trigger_name, trigger_group, instance_name,
         fired_time, status, created_at)
        VALUES (#{jobName}, #{jobGroup}, #{triggerName}, #{triggerGroup},
                #{instanceName}, #{firedTime}, #{status}, NOW())
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(JobExecutionHistory history);

    @Update("""
        UPDATE job_execution_history
        SET completed_time = #{completedTime},
            execution_time_ms = #{executionTimeMs},
            status = #{status},
            error_message = #{errorMessage}
        WHERE id = #{id}
        """)
    int updateCompletion(@Param("id") Long id,
                        @Param("completedTime") LocalDateTime completedTime,
                        @Param("executionTimeMs") Long executionTimeMs,
                        @Param("status") JobExecutionStatus status,
                        @Param("errorMessage") String errorMessage);

    @Select("""
        SELECT * FROM job_execution_history
        WHERE id = #{id}
        """)
    JobExecutionHistory findById(Long id);

    @Select("""
        SELECT * FROM job_execution_history
        WHERE job_name = #{jobName} AND job_group = #{jobGroup}
        ORDER BY fired_time DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<JobExecutionHistory> findByJob(@Param("jobName") String jobName,
                                        @Param("jobGroup") String jobGroup,
                                        @Param("limit") int limit,
                                        @Param("offset") int offset);

    @Select("""
        SELECT COUNT(*) FROM job_execution_history
        WHERE job_name = #{jobName} AND job_group = #{jobGroup}
        """)
    long countByJob(@Param("jobName") String jobName, @Param("jobGroup") String jobGroup);

    @Select("""
        SELECT * FROM job_execution_history
        ORDER BY fired_time DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<JobExecutionHistory> findAll(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM job_execution_history")
    long countAll();

    @Select("""
        SELECT * FROM job_execution_history
        ORDER BY fired_time DESC
        LIMIT #{limit}
        """)
    List<JobExecutionHistory> findRecent(int limit);

    @Select("""
        SELECT * FROM job_execution_history
        WHERE status = #{status}
        ORDER BY fired_time DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<JobExecutionHistory> findByStatus(@Param("status") JobExecutionStatus status,
                                           @Param("limit") int limit,
                                           @Param("offset") int offset);

    @Select("""
        SELECT COUNT(*) FROM job_execution_history
        WHERE status = #{status}
        """)
    long countByStatus(JobExecutionStatus status);

    @Select("""
        SELECT * FROM job_execution_history
        WHERE fired_time BETWEEN #{startTime} AND #{endTime}
        ORDER BY fired_time DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<JobExecutionHistory> findByTimeRange(@Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime,
                                              @Param("limit") int limit,
                                              @Param("offset") int offset);

    @Select("""
        SELECT COUNT(*) FROM job_execution_history
        WHERE fired_time BETWEEN #{startTime} AND #{endTime}
        """)
    long countByTimeRange(@Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);

    @Delete("""
        DELETE FROM job_execution_history
        WHERE fired_time < #{olderThan}
        """)
    int deleteOlderThan(LocalDateTime olderThan);

    @Select("""
        SELECT
            COUNT(*) as total_executions,
            SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed_count,
            SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed_count,
            SUM(CASE WHEN status = 'VETOED' THEN 1 ELSE 0 END) as vetoed_count,
            AVG(execution_time_ms) as avg_execution_time_ms,
            MIN(execution_time_ms) as min_execution_time_ms,
            MAX(execution_time_ms) as max_execution_time_ms
        FROM job_execution_history
        WHERE fired_time BETWEEN #{startTime} AND #{endTime}
        """)
    Map<String, Object> getStatisticsAll(@Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime);

    @Select("""
        SELECT
            COUNT(*) as total_executions,
            SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed_count,
            SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed_count,
            SUM(CASE WHEN status = 'VETOED' THEN 1 ELSE 0 END) as vetoed_count,
            AVG(execution_time_ms) as avg_execution_time_ms,
            MIN(execution_time_ms) as min_execution_time_ms,
            MAX(execution_time_ms) as max_execution_time_ms
        FROM job_execution_history
        WHERE job_name = #{jobName} AND job_group = #{jobGroup}
        AND fired_time BETWEEN #{startTime} AND #{endTime}
        """)
    Map<String, Object> getStatisticsByJob(@Param("jobName") String jobName,
                                           @Param("jobGroup") String jobGroup,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);
}
