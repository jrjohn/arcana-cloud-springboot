package com.arcana.plugin.audit;

import com.arcana.plugin.audit.api.AuditService;
import com.arcana.plugin.audit.model.AuditEntry;
import com.arcana.cloud.plugin.event.PluginEvent;
import com.arcana.cloud.plugin.extension.EventListenerExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event listener for user-related events.
 *
 * <p>Automatically creates audit entries for user lifecycle events
 * such as login, logout, creation, update, and deletion.</p>
 */
@EventListenerExtension(
    key = "user-event-listener",
    description = "Listens to user lifecycle events for auditing"
)
public class UserEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserEventListener.class);

    private final AuditService auditService;

    public UserEventListener(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Handles a plugin event.
     *
     * @param event the event to handle
     */
    public void handle(PluginEvent event) {
        log.debug("Received event: {}", event.getEventType());

        try {
            switch (event.getEventType()) {
                case "UserCreatedEvent" -> handleUserCreated(event);
                case "UserUpdatedEvent" -> handleUserUpdated(event);
                case "UserDeletedEvent" -> handleUserDeleted(event);
                case "UserLoginEvent" -> handleUserLogin(event);
                case "UserLogoutEvent" -> handleUserLogout(event);
                default -> log.debug("Unhandled event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Error handling event: {}", event.getEventType(), e);
        }
    }

    private void handleUserCreated(PluginEvent event) {
        // Extract user info from event using reflection or casting
        Long userId = extractUserId(event);
        String username = extractUsername(event);

        AuditEntry entry = AuditEntry.builder()
            .action("USER_CREATED")
            .entityType("User")
            .entityId(userId != null ? userId.toString() : null)
            .userId(userId)
            .username(username)
            .details("User account created: " + username)
            .build();

        auditService.log(entry);
        log.info("Audit logged: User created - {}", username);
    }

    private void handleUserUpdated(PluginEvent event) {
        Long userId = extractUserId(event);
        String username = extractUsername(event);
        String[] changedFields = extractChangedFields(event);

        AuditEntry entry = AuditEntry.builder()
            .action("USER_UPDATED")
            .entityType("User")
            .entityId(userId != null ? userId.toString() : null)
            .userId(userId)
            .username(username)
            .details("User updated. Changed fields: " + String.join(", ", changedFields))
            .build();

        auditService.log(entry);
        log.info("Audit logged: User updated - {}", username);
    }

    private void handleUserDeleted(PluginEvent event) {
        Long userId = extractUserId(event);
        String username = extractUsername(event);

        AuditEntry entry = AuditEntry.builder()
            .action("USER_DELETED")
            .entityType("User")
            .entityId(userId != null ? userId.toString() : null)
            .userId(userId)
            .username(username)
            .details("User account deleted: " + username)
            .build();

        auditService.log(entry);
        log.info("Audit logged: User deleted - {}", username);
    }

    private void handleUserLogin(PluginEvent event) {
        Long userId = extractUserId(event);
        String username = extractUsername(event);
        String ipAddress = extractIpAddress(event);
        String userAgent = extractUserAgent(event);

        AuditEntry entry = AuditEntry.builder()
            .action("USER_LOGIN")
            .entityType("User")
            .entityId(userId != null ? userId.toString() : null)
            .userId(userId)
            .username(username)
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .details("User logged in from " + ipAddress)
            .build();

        auditService.log(entry);
        log.info("Audit logged: User login - {} from {}", username, ipAddress);
    }

    private void handleUserLogout(PluginEvent event) {
        Long userId = extractUserId(event);
        String username = extractUsername(event);

        AuditEntry entry = AuditEntry.builder()
            .action("USER_LOGOUT")
            .entityType("User")
            .entityId(userId != null ? userId.toString() : null)
            .userId(userId)
            .username(username)
            .details("User logged out")
            .build();

        auditService.log(entry);
        log.info("Audit logged: User logout - {}", username);
    }

    // Helper methods to extract data from events
    // In a real implementation, these would use reflection or event interface methods

    private Long extractUserId(PluginEvent event) {
        try {
            var method = event.getClass().getMethod("getUserId");
            return (Long) method.invoke(event);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractUsername(PluginEvent event) {
        try {
            var method = event.getClass().getMethod("getUsername");
            return (String) method.invoke(event);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractIpAddress(PluginEvent event) {
        try {
            var method = event.getClass().getMethod("getIpAddress");
            return (String) method.invoke(event);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractUserAgent(PluginEvent event) {
        try {
            var method = event.getClass().getMethod("getUserAgent");
            return (String) method.invoke(event);
        } catch (Exception e) {
            return null;
        }
    }

    private String[] extractChangedFields(PluginEvent event) {
        try {
            var method = event.getClass().getMethod("getChangedFields");
            return (String[]) method.invoke(event);
        } catch (Exception e) {
            return new String[]{"unknown"};
        }
    }
}
