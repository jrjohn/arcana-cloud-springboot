package com.arcana.plugin.audit;

import com.arcana.plugin.audit.api.AuditService;
import com.arcana.plugin.audit.model.AuditEntry;
import com.arcana.cloud.plugin.extension.RestEndpointExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for audit log endpoints.
 *
 * <p>Provides REST API for querying and managing audit logs.
 * All endpoints require ADMIN permission.</p>
 */
@RestEndpointExtension(
    key = "audit-rest",
    path = "/api/v1/plugins/audit",
    description = "Audit log REST API endpoints",
    requiresPermission = "ADMIN"
)
@RestController
@RequestMapping("/api/v1/plugins/audit")
public class AuditRestController {

    private final AuditService auditService;

    public AuditRestController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Returns recent audit entries.
     *
     * @param limit maximum entries to return (default 100)
     * @return list of audit entries
     */
    @GetMapping("/entries")
    public ResponseEntity<Map<String, Object>> getRecentEntries(
            @RequestParam(defaultValue = "100") int limit) {

        List<AuditEntry> entries = auditService.findRecent(limit);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", entries);
        response.put("count", entries.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Returns an audit entry by ID.
     *
     * @param id the entry ID
     * @return the audit entry
     */
    @GetMapping("/entries/{id}")
    public ResponseEntity<Map<String, Object>> getEntry(@PathVariable Long id) {
        return auditService.findById(id)
            .map(entry -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", entry);
                return ResponseEntity.ok(response);
            })
            .orElseGet(() -> {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Audit entry not found");
                return ResponseEntity.notFound().build();
            });
    }

    /**
     * Returns audit entries for a specific entity.
     *
     * @param entityType the entity type
     * @param entityId the entity ID
     * @return list of audit entries
     */
    @GetMapping("/entities/{entityType}/{entityId}")
    public ResponseEntity<Map<String, Object>> getByEntity(
            @PathVariable String entityType,
            @PathVariable String entityId) {

        List<AuditEntry> entries = auditService.findByEntity(entityType, entityId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", entries);
        response.put("count", entries.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Returns audit entries for a specific user.
     *
     * @param userId the user ID
     * @param limit maximum entries to return
     * @return list of audit entries
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<Map<String, Object>> getByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "100") int limit) {

        List<AuditEntry> entries = auditService.findByUser(userId, limit);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", entries);
        response.put("count", entries.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Returns audit entries for a specific action.
     *
     * @param action the action type
     * @param limit maximum entries to return
     * @return list of audit entries
     */
    @GetMapping("/actions/{action}")
    public ResponseEntity<Map<String, Object>> getByAction(
            @PathVariable String action,
            @RequestParam(defaultValue = "100") int limit) {

        List<AuditEntry> entries = auditService.findByAction(action, limit);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", entries);
        response.put("count", entries.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Returns audit statistics.
     *
     * @return audit statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        AuditService.AuditStatistics stats = auditService.getStatistics();

        Map<String, Object> data = new HashMap<>();
        data.put("totalCount", stats.getTotalCount());
        data.put("todayCount", stats.getTodayCount());
        data.put("thisWeekCount", stats.getThisWeekCount());
        data.put("thisMonthCount", stats.getThisMonthCount());
        data.put("countByAction", stats.getCountByAction());
        data.put("countByEntityType", stats.getCountByEntityType());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);

        return ResponseEntity.ok(response);
    }

    /**
     * Returns today's audit count.
     *
     * @return today's count
     */
    @GetMapping("/today/count")
    public ResponseEntity<Map<String, Object>> getTodayCount() {
        long count = auditService.getTodayCount();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("count", count));

        return ResponseEntity.ok(response);
    }

    /**
     * Triggers cleanup of old audit entries.
     * Only accessible to administrators.
     *
     * @param days retention period in days
     * @return cleanup result
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanup(
            @RequestParam(defaultValue = "90") int days) {

        int deleted = auditService.deleteOlderThan(days);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of("deletedCount", deleted));
        response.put("message", "Deleted " + deleted + " audit entries older than " + days + " days");

        return ResponseEntity.ok(response);
    }
}
