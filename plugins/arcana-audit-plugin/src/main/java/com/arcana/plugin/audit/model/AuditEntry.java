package com.arcana.plugin.audit.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents an audit log entry.
 */
public class AuditEntry {

    private Long id;
    private String action;
    private String entityType;
    private String entityId;
    private Long userId;
    private String username;
    private String ipAddress;
    private String userAgent;
    private String details;
    private String oldValue;
    private String newValue;
    private AuditResult result;
    private String errorMessage;
    private Instant timestamp;

    public AuditEntry() {
        this.timestamp = Instant.now();
        this.result = AuditResult.SUCCESS;
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public AuditResult getResult() {
        return result;
    }

    public void setResult(AuditResult result) {
        this.result = result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    // Builder pattern

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AuditEntry entry = new AuditEntry();

        public Builder action(String action) {
            entry.action = action;
            return this;
        }

        public Builder entityType(String entityType) {
            entry.entityType = entityType;
            return this;
        }

        public Builder entityId(String entityId) {
            entry.entityId = entityId;
            return this;
        }

        public Builder userId(Long userId) {
            entry.userId = userId;
            return this;
        }

        public Builder username(String username) {
            entry.username = username;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            entry.ipAddress = ipAddress;
            return this;
        }

        public Builder userAgent(String userAgent) {
            entry.userAgent = userAgent;
            return this;
        }

        public Builder details(String details) {
            entry.details = details;
            return this;
        }

        public Builder oldValue(String oldValue) {
            entry.oldValue = oldValue;
            return this;
        }

        public Builder newValue(String newValue) {
            entry.newValue = newValue;
            return this;
        }

        public Builder result(AuditResult result) {
            entry.result = result;
            return this;
        }

        public Builder error(String errorMessage) {
            entry.result = AuditResult.FAILURE;
            entry.errorMessage = errorMessage;
            return this;
        }

        public AuditEntry build() {
            return entry;
        }
    }

    /**
     * Audit result enumeration.
     */
    public enum AuditResult {
        SUCCESS,
        FAILURE,
        DENIED
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditEntry that = (AuditEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AuditEntry{" +
            "id=" + id +
            ", action='" + action + '\'' +
            ", entityType='" + entityType + '\'' +
            ", entityId='" + entityId + '\'' +
            ", userId=" + userId +
            ", timestamp=" + timestamp +
            ", result=" + result +
            '}';
    }
}
