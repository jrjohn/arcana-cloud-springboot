package com.arcana.plugin.audit.api;

import com.arcana.plugin.audit.model.AuditEntry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for audit logging operations.
 *
 * <p>This interface is exported by the Audit Plugin and can be used
 * by other plugins to log audit events.</p>
 */
public interface AuditService {

    /**
     * Logs an audit event.
     *
     * @param entry the audit entry to log
     * @return the saved audit entry with generated ID
     */
    AuditEntry log(AuditEntry entry);

    /**
     * Logs an audit event with the given parameters.
     *
     * @param action the action performed
     * @param entityType the type of entity affected
     * @param entityId the ID of the entity
     * @param userId the ID of the user who performed the action
     * @param details additional details
     * @return the saved audit entry
     */
    AuditEntry log(String action, String entityType, String entityId,
                   Long userId, String details);

    /**
     * Finds an audit entry by ID.
     *
     * @param id the audit entry ID
     * @return the audit entry if found
     */
    Optional<AuditEntry> findById(Long id);

    /**
     * Finds audit entries for a specific entity.
     *
     * @param entityType the entity type
     * @param entityId the entity ID
     * @return list of audit entries
     */
    List<AuditEntry> findByEntity(String entityType, String entityId);

    /**
     * Finds audit entries by user.
     *
     * @param userId the user ID
     * @param limit maximum entries to return
     * @return list of audit entries
     */
    List<AuditEntry> findByUser(Long userId, int limit);

    /**
     * Finds audit entries by action.
     *
     * @param action the action
     * @param limit maximum entries to return
     * @return list of audit entries
     */
    List<AuditEntry> findByAction(String action, int limit);

    /**
     * Finds recent audit entries.
     *
     * @param limit maximum entries to return
     * @return list of recent audit entries
     */
    List<AuditEntry> findRecent(int limit);

    /**
     * Finds audit entries within a time range.
     *
     * @param from start time
     * @param to end time
     * @param limit maximum entries to return
     * @return list of audit entries
     */
    List<AuditEntry> findByTimeRange(Instant from, Instant to, int limit);

    /**
     * Returns the count of audit entries for today.
     *
     * @return count of today's entries
     */
    long getTodayCount();

    /**
     * Returns statistics about audit entries.
     *
     * @return audit statistics
     */
    AuditStatistics getStatistics();

    /**
     * Deletes audit entries older than the specified number of days.
     *
     * @param days retention period in days
     * @return number of entries deleted
     */
    int deleteOlderThan(int days);

    /**
     * Audit statistics.
     */
    interface AuditStatistics {
        long getTotalCount();
        long getTodayCount();
        long getThisWeekCount();
        long getThisMonthCount();
        java.util.Map<String, Long> getCountByAction();
        java.util.Map<String, Long> getCountByEntityType();
    }
}
