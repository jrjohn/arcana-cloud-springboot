-- Audit Plugin Database Schema
-- Version: 1.0.0
-- Plugin: com.arcana.plugin.audit

-- Create audit entries table
CREATE TABLE IF NOT EXISTS plugin_audit_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(100) NOT NULL COMMENT 'The action performed (e.g., USER_LOGIN, USER_CREATED)',
    entity_type VARCHAR(100) COMMENT 'Type of entity affected (e.g., User, Role)',
    entity_id VARCHAR(100) COMMENT 'ID of the entity affected',
    user_id BIGINT COMMENT 'ID of the user who performed the action',
    username VARCHAR(100) COMMENT 'Username of the user',
    ip_address VARCHAR(45) COMMENT 'IP address of the request',
    user_agent VARCHAR(500) COMMENT 'User agent string',
    details TEXT COMMENT 'Additional details about the action',
    old_value TEXT COMMENT 'Previous value (for updates)',
    new_value TEXT COMMENT 'New value (for updates)',
    result VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT 'Result of the action (SUCCESS, FAILURE, DENIED)',
    error_message TEXT COMMENT 'Error message if action failed',
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'When the action occurred',

    -- Indexes for common queries
    INDEX idx_audit_action (action),
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_timestamp (timestamp),
    INDEX idx_audit_result (result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Audit log entries for tracking user and system actions';
