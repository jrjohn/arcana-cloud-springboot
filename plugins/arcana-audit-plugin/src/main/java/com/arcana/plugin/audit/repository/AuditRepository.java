package com.arcana.plugin.audit.repository;

import com.arcana.plugin.audit.model.AuditEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repository for audit entries.
 *
 * <p>Supports both database-backed storage (when DataSource is available)
 * and in-memory storage (for testing or when database is unavailable).</p>
 */
public class AuditRepository {

    private static final Logger log = LoggerFactory.getLogger(AuditRepository.class);

    private static final String TABLE_NAME = "plugin_audit_entries";

    private final DataSource dataSource;
    private final boolean useDatabase;

    // In-memory storage fallback
    private final Map<Long, AuditEntry> memoryStore = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public AuditRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        this.useDatabase = dataSource != null;
    }

    /**
     * Ensures the database schema exists.
     */
    public void ensureSchemaExists() {
        if (!useDatabase) {
            return;
        }

        String createTable = """
            CREATE TABLE IF NOT EXISTS %s (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                action VARCHAR(100) NOT NULL,
                entity_type VARCHAR(100),
                entity_id VARCHAR(100),
                user_id BIGINT,
                username VARCHAR(100),
                ip_address VARCHAR(45),
                user_agent VARCHAR(500),
                details TEXT,
                old_value TEXT,
                new_value TEXT,
                result VARCHAR(20) NOT NULL,
                error_message TEXT,
                timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_action (action),
                INDEX idx_entity (entity_type, entity_id),
                INDEX idx_user (user_id),
                INDEX idx_timestamp (timestamp)
            )
            """.formatted(TABLE_NAME);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTable);
            log.info("Audit table created/verified");
        } catch (SQLException e) {
            log.error("Failed to create audit table", e);
        }
    }

    /**
     * Saves an audit entry.
     */
    public AuditEntry save(AuditEntry entry) {
        if (useDatabase) {
            return saveToDatabase(entry);
        } else {
            return saveToMemory(entry);
        }
    }

    private AuditEntry saveToDatabase(AuditEntry entry) {
        String sql = """
            INSERT INTO %s (action, entity_type, entity_id, user_id, username,
                ip_address, user_agent, details, old_value, new_value, result,
                error_message, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.formatted(TABLE_NAME);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, entry.getAction());
            stmt.setString(2, entry.getEntityType());
            stmt.setString(3, entry.getEntityId());
            stmt.setObject(4, entry.getUserId());
            stmt.setString(5, entry.getUsername());
            stmt.setString(6, entry.getIpAddress());
            stmt.setString(7, entry.getUserAgent());
            stmt.setString(8, entry.getDetails());
            stmt.setString(9, entry.getOldValue());
            stmt.setString(10, entry.getNewValue());
            stmt.setString(11, entry.getResult().name());
            stmt.setString(12, entry.getErrorMessage());
            stmt.setTimestamp(13, Timestamp.from(entry.getTimestamp()));

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    entry.setId(rs.getLong(1));
                }
            }

            return entry;

        } catch (SQLException e) {
            log.error("Failed to save audit entry", e);
            // Fallback to memory
            return saveToMemory(entry);
        }
    }

    private AuditEntry saveToMemory(AuditEntry entry) {
        entry.setId(idGenerator.getAndIncrement());
        memoryStore.put(entry.getId(), entry);
        return entry;
    }

    /**
     * Finds an entry by ID.
     */
    public Optional<AuditEntry> findById(Long id) {
        if (useDatabase) {
            return findByIdFromDatabase(id);
        } else {
            return Optional.ofNullable(memoryStore.get(id));
        }
    }

    private Optional<AuditEntry> findByIdFromDatabase(Long id) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }

        } catch (SQLException e) {
            log.error("Failed to find audit entry", e);
        }

        return Optional.empty();
    }

    /**
     * Finds entries by entity.
     */
    public List<AuditEntry> findByEntity(String entityType, String entityId) {
        if (useDatabase) {
            return findByEntityFromDatabase(entityType, entityId);
        } else {
            return memoryStore.values().stream()
                .filter(e -> Objects.equals(e.getEntityType(), entityType) &&
                             Objects.equals(e.getEntityId(), entityId))
                .sorted(Comparator.comparing(AuditEntry::getTimestamp).reversed())
                .toList();
        }
    }

    private List<AuditEntry> findByEntityFromDatabase(String entityType, String entityId) {
        String sql = "SELECT * FROM " + TABLE_NAME +
                     " WHERE entity_type = ? AND entity_id = ? ORDER BY timestamp DESC";

        List<AuditEntry> entries = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, entityType);
            stmt.setString(2, entityId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(mapResultSet(rs));
                }
            }

        } catch (SQLException e) {
            log.error("Failed to find audit entries by entity", e);
        }

        return entries;
    }

    /**
     * Finds recent entries.
     */
    public List<AuditEntry> findRecent(int limit) {
        if (useDatabase) {
            return findRecentFromDatabase(limit);
        } else {
            return memoryStore.values().stream()
                .sorted(Comparator.comparing(AuditEntry::getTimestamp).reversed())
                .limit(limit)
                .toList();
        }
    }

    private List<AuditEntry> findRecentFromDatabase(int limit) {
        String sql = "SELECT * FROM " + TABLE_NAME + " ORDER BY timestamp DESC LIMIT ?";

        List<AuditEntry> entries = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(mapResultSet(rs));
                }
            }

        } catch (SQLException e) {
            log.error("Failed to find recent audit entries", e);
        }

        return entries;
    }

    /**
     * Counts entries for today.
     */
    public long countToday() {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);

        if (useDatabase) {
            return countFromDatabase("timestamp >= ?", startOfDay);
        } else {
            return memoryStore.values().stream()
                .filter(e -> e.getTimestamp().isAfter(startOfDay))
                .count();
        }
    }

    /**
     * Counts total entries.
     */
    public long countTotal() {
        if (useDatabase) {
            return countFromDatabase(null, (Object[]) null);
        } else {
            return memoryStore.size();
        }
    }

    private long countFromDatabase(String whereClause, Object... params) {
        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME;
        if (whereClause != null) {
            sql += " WHERE " + whereClause;
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof Instant) {
                    stmt.setTimestamp(i + 1, Timestamp.from((Instant) params[i]));
                } else {
                    stmt.setObject(i + 1, params[i]);
                }
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }

        } catch (SQLException e) {
            log.error("Failed to count audit entries", e);
        }

        return 0;
    }

    /**
     * Deletes entries older than the specified days.
     */
    public int deleteOlderThan(int days) {
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);

        if (useDatabase) {
            return deleteFromDatabase(cutoff);
        } else {
            int count = 0;
            Iterator<Map.Entry<Long, AuditEntry>> it = memoryStore.entrySet().iterator();
            while (it.hasNext()) {
                if (it.next().getValue().getTimestamp().isBefore(cutoff)) {
                    it.remove();
                    count++;
                }
            }
            return count;
        }
    }

    private int deleteFromDatabase(Instant cutoff) {
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE timestamp < ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.from(cutoff));
            return stmt.executeUpdate();

        } catch (SQLException e) {
            log.error("Failed to delete old audit entries", e);
            return 0;
        }
    }

    /**
     * Closes the repository.
     */
    public void close() {
        memoryStore.clear();
    }

    private AuditEntry mapResultSet(ResultSet rs) throws SQLException {
        AuditEntry entry = new AuditEntry();
        entry.setId(rs.getLong("id"));
        entry.setAction(rs.getString("action"));
        entry.setEntityType(rs.getString("entity_type"));
        entry.setEntityId(rs.getString("entity_id"));
        entry.setUserId(rs.getObject("user_id", Long.class));
        entry.setUsername(rs.getString("username"));
        entry.setIpAddress(rs.getString("ip_address"));
        entry.setUserAgent(rs.getString("user_agent"));
        entry.setDetails(rs.getString("details"));
        entry.setOldValue(rs.getString("old_value"));
        entry.setNewValue(rs.getString("new_value"));
        entry.setResult(AuditEntry.AuditResult.valueOf(rs.getString("result")));
        entry.setErrorMessage(rs.getString("error_message"));
        entry.setTimestamp(rs.getTimestamp("timestamp").toInstant());
        return entry;
    }
}
