package com.arcana.plugin.audit;

import com.arcana.plugin.audit.api.AuditService;
import com.arcana.plugin.audit.model.AuditEntry;
import com.arcana.plugin.audit.repository.AuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Implementation of the AuditService interface.
 */
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditRepository repository;

    public AuditServiceImpl(AuditRepository repository) {
        this.repository = repository;
    }

    @Override
    public AuditEntry log(AuditEntry entry) {
        log.debug("Logging audit entry: {}", entry);
        return repository.save(entry);
    }

    @Override
    public AuditEntry log(String action, String entityType, String entityId,
                          Long userId, String details) {
        AuditEntry entry = AuditEntry.builder()
            .action(action)
            .entityType(entityType)
            .entityId(entityId)
            .userId(userId)
            .details(details)
            .build();

        return log(entry);
    }

    @Override
    public Optional<AuditEntry> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public List<AuditEntry> findByEntity(String entityType, String entityId) {
        return repository.findByEntity(entityType, entityId);
    }

    @Override
    public List<AuditEntry> findByUser(Long userId, int limit) {
        // For now, delegate to findRecent and filter
        return findRecent(1000).stream()
            .filter(e -> Objects.equals(e.getUserId(), userId))
            .limit(limit)
            .toList();
    }

    @Override
    public List<AuditEntry> findByAction(String action, int limit) {
        return findRecent(1000).stream()
            .filter(e -> Objects.equals(e.getAction(), action))
            .limit(limit)
            .toList();
    }

    @Override
    public List<AuditEntry> findRecent(int limit) {
        return repository.findRecent(limit);
    }

    @Override
    public List<AuditEntry> findByTimeRange(Instant from, Instant to, int limit) {
        return findRecent(10000).stream()
            .filter(e -> e.getTimestamp().isAfter(from) && e.getTimestamp().isBefore(to))
            .limit(limit)
            .toList();
    }

    @Override
    public long getTodayCount() {
        return repository.countToday();
    }

    @Override
    public AuditStatistics getStatistics() {
        return new AuditStatisticsImpl();
    }

    @Override
    public int deleteOlderThan(int days) {
        log.info("Deleting audit entries older than {} days", days);
        int deleted = repository.deleteOlderThan(days);
        log.info("Deleted {} audit entries", deleted);
        return deleted;
    }

    /**
     * Implementation of AuditStatistics.
     */
    private class AuditStatisticsImpl implements AuditStatistics {

        private final long totalCount;
        private final long todayCount;
        private final long weekCount;
        private final long monthCount;
        private final Map<String, Long> countByAction;
        private final Map<String, Long> countByEntityType;

        AuditStatisticsImpl() {
            this.totalCount = repository.countTotal();
            this.todayCount = repository.countToday();

            // Calculate week and month counts
            List<AuditEntry> recent = repository.findRecent(10000);
            Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
            Instant monthAgo = Instant.now().minus(30, ChronoUnit.DAYS);

            this.weekCount = recent.stream()
                .filter(e -> e.getTimestamp().isAfter(weekAgo))
                .count();

            this.monthCount = recent.stream()
                .filter(e -> e.getTimestamp().isAfter(monthAgo))
                .count();

            // Group by action
            this.countByAction = new HashMap<>();
            for (AuditEntry entry : recent) {
                countByAction.merge(entry.getAction(), 1L, Long::sum);
            }

            // Group by entity type
            this.countByEntityType = new HashMap<>();
            for (AuditEntry entry : recent) {
                if (entry.getEntityType() != null) {
                    countByEntityType.merge(entry.getEntityType(), 1L, Long::sum);
                }
            }
        }

        @Override
        public long getTotalCount() {
            return totalCount;
        }

        @Override
        public long getTodayCount() {
            return todayCount;
        }

        @Override
        public long getThisWeekCount() {
            return weekCount;
        }

        @Override
        public long getThisMonthCount() {
            return monthCount;
        }

        @Override
        public Map<String, Long> getCountByAction() {
            return Collections.unmodifiableMap(countByAction);
        }

        @Override
        public Map<String, Long> getCountByEntityType() {
            return Collections.unmodifiableMap(countByEntityType);
        }
    }
}
