package com.arcana.cloud.controller;

import com.arcana.cloud.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;

/**
 * REST Controller for plugin management.
 *
 * <p>Provides HTTP endpoints for:</p>
 * <ul>
 *   <li>Plugin installation, enabling, disabling, and uninstallation</li>
 *   <li>Plugin listing and status queries</li>
 *   <li>Plugin health checks for Kubernetes/layered deployments</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/plugins")
@Tag(name = "Plugins", description = "Plugin management APIs")
@ConditionalOnExpression("'${deployment.layer:}' == '' or '${deployment.layer:}' == 'controller' or '${deployment.layer:}' == 'service'")
public class PluginController {

    private static final Logger log = LoggerFactory.getLogger(PluginController.class);

    @Value("${arcana.plugin.enabled:true}")
    private boolean pluginSystemEnabled;

    @Value("${arcana.plugin.plugins-directory:plugins}")
    private String pluginsDirectory;

    // These would be injected from PluginManager in a real implementation
    // For now, we'll use a simple in-memory tracking
    private final Map<String, PluginInfo> plugins = new LinkedHashMap<>();
    private volatile boolean pluginsInitialized = false;
    private Instant initializationTime;

    /**
     * Returns list of all installed plugins.
     */
    @GetMapping
    @Operation(summary = "List all plugins")
    public ResponseEntity<ApiResponse<List<PluginInfo>>> listPlugins() {
        if (!pluginSystemEnabled) {
            return ResponseEntity.ok(ApiResponse.success(Collections.emptyList(), "Plugin system is disabled"));
        }

        List<PluginInfo> pluginList = new ArrayList<>(plugins.values());
        return ResponseEntity.ok(ApiResponse.success(pluginList, "Plugins retrieved successfully"));
    }

    /**
     * Returns details of a specific plugin.
     */
    @GetMapping("/{key}")
    @Operation(summary = "Get plugin details")
    public ResponseEntity<ApiResponse<PluginInfo>> getPlugin(@PathVariable String key) {
        PluginInfo plugin = plugins.get(key);
        if (plugin == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Plugin not found: " + key));
        }
        return ResponseEntity.ok(ApiResponse.success(plugin, "Plugin retrieved successfully"));
    }

    /**
     * Installs a new plugin from uploaded JAR file.
     */
    @PostMapping("/install")
    @Operation(summary = "Install a plugin")
    public ResponseEntity<ApiResponse<PluginInfo>> installPlugin(
            @RequestParam("file") MultipartFile file) {

        if (!pluginSystemEnabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("Plugin system is disabled"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("No file provided"));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.endsWith(".jar")) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("File must be a JAR file"));
        }

        try {
            // Save to plugins directory
            Path pluginPath = Path.of(pluginsDirectory, filename);
            Files.createDirectories(pluginPath.getParent());
            Files.copy(file.getInputStream(), pluginPath, StandardCopyOption.REPLACE_EXISTING);

            // Extract plugin key from filename (simplified)
            String pluginKey = filename.replace(".jar", "").replaceAll("-\\d+\\.\\d+\\.\\d+.*", "");

            PluginInfo pluginInfo = new PluginInfo(
                pluginKey,
                filename.replace(".jar", ""),
                "1.0.0",
                "INSTALLED",
                Instant.now()
            );
            plugins.put(pluginKey, pluginInfo);

            log.info("Plugin installed: {}", pluginKey);
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(pluginInfo, "Plugin installed successfully"));

        } catch (IOException e) {
            log.error("Failed to install plugin: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to install plugin due to an internal error"));
        }
    }

    /**
     * Enables a plugin.
     */
    @PostMapping("/{key}/enable")
    @Operation(summary = "Enable a plugin")
    public ResponseEntity<ApiResponse<PluginInfo>> enablePlugin(@PathVariable String key) {
        PluginInfo plugin = plugins.get(key);
        if (plugin == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Plugin not found: " + key));
        }

        plugin.setState("ACTIVE");
        log.info("Plugin enabled: {}", key);
        return ResponseEntity.ok(ApiResponse.success(plugin, "Plugin enabled successfully"));
    }

    /**
     * Disables a plugin.
     */
    @PostMapping("/{key}/disable")
    @Operation(summary = "Disable a plugin")
    public ResponseEntity<ApiResponse<PluginInfo>> disablePlugin(@PathVariable String key) {
        PluginInfo plugin = plugins.get(key);
        if (plugin == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Plugin not found: " + key));
        }

        plugin.setState("RESOLVED");
        log.info("Plugin disabled: {}", key);
        return ResponseEntity.ok(ApiResponse.success(plugin, "Plugin disabled successfully"));
    }

    /**
     * Uninstalls a plugin.
     */
    @DeleteMapping("/{key}")
    @Operation(summary = "Uninstall a plugin")
    public ResponseEntity<ApiResponse<Void>> uninstallPlugin(@PathVariable String key) {
        PluginInfo plugin = plugins.remove(key);
        if (plugin == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Plugin not found: " + key));
        }

        log.info("Plugin uninstalled: {}", key);
        return ResponseEntity.ok(ApiResponse.success(null, "Plugin uninstalled successfully"));
    }

    /**
     * Returns plugin system health status.
     * Used by Kubernetes HTTP health probes.
     */
    @GetMapping("/health")
    @Operation(summary = "Plugin system health check")
    public ResponseEntity<ApiResponse<PluginHealthStatus>> getHealth() {
        PluginHealthStatus health = new PluginHealthStatus(
            pluginSystemEnabled,
            pluginsInitialized,
            plugins.size(),
            (int) plugins.values().stream().filter(p -> "ACTIVE".equals(p.getState())).count(),
            initializationTime
        );

        if (!pluginSystemEnabled) {
            return ResponseEntity.ok(ApiResponse.success(health, "Plugin system disabled"));
        }

        if (!pluginsInitialized) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.success(health, "Plugin system initializing"));
        }

        return ResponseEntity.ok(ApiResponse.success(health, "Plugin system healthy"));
    }

    /**
     * Returns readiness status for Kubernetes readiness probe.
     */
    @GetMapping("/health/ready")
    @Operation(summary = "Plugin readiness check")
    public ResponseEntity<Map<String, Object>> getReadiness() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", pluginsInitialized ? "UP" : "DOWN");
        status.put("pluginSystemEnabled", pluginSystemEnabled);
        status.put("pluginsInitialized", pluginsInitialized);
        status.put("pluginCount", plugins.size());

        if (pluginsInitialized) {
            return ResponseEntity.ok(status);
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(status);
        }
    }

    /**
     * Returns liveness status for Kubernetes liveness probe.
     */
    @GetMapping("/health/live")
    @Operation(summary = "Plugin liveness check")
    public ResponseEntity<Map<String, Object>> getLiveness() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "UP");
        status.put("pluginSystemEnabled", pluginSystemEnabled);
        return ResponseEntity.ok(status);
    }

    /**
     * Marks plugins as initialized (called by PluginManager).
     */
    public void setPluginsInitialized(boolean initialized) {
        this.pluginsInitialized = initialized;
        if (initialized) {
            this.initializationTime = Instant.now();
        }
    }

    /**
     * Registers a plugin (called by PluginManager).
     */
    public void registerPlugin(String key, String name, String version, String state) {
        plugins.put(key, new PluginInfo(key, name, version, state, Instant.now()));
    }

    /**
     * Plugin information DTO.
     */
    public static class PluginInfo {
        private String key;
        private String name;
        private String version;
        private String state;
        private Instant installedAt;
        private Map<String, String> metadata;

        public PluginInfo() {}

        public PluginInfo(String key, String name, String version, String state, Instant installedAt) {
            this.key = key;
            this.name = name;
            this.version = version;
            this.state = state;
            this.installedAt = installedAt;
            this.metadata = new HashMap<>();
        }

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public String getState() { return state; }
        public void setState(String state) { this.state = state; }

        public Instant getInstalledAt() { return installedAt; }
        public void setInstalledAt(Instant installedAt) { this.installedAt = installedAt; }

        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    }

    /**
     * Plugin health status DTO.
     */
    public static class PluginHealthStatus {
        private boolean enabled;
        private boolean initialized;
        private int totalPlugins;
        private int activePlugins;
        private Instant initializationTime;

        public PluginHealthStatus() {}

        public PluginHealthStatus(boolean enabled, boolean initialized, int totalPlugins,
                                  int activePlugins, Instant initializationTime) {
            this.enabled = enabled;
            this.initialized = initialized;
            this.totalPlugins = totalPlugins;
            this.activePlugins = activePlugins;
            this.initializationTime = initializationTime;
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isInitialized() { return initialized; }
        public void setInitialized(boolean initialized) { this.initialized = initialized; }

        public int getTotalPlugins() { return totalPlugins; }
        public void setTotalPlugins(int totalPlugins) { this.totalPlugins = totalPlugins; }

        public int getActivePlugins() { return activePlugins; }
        public void setActivePlugins(int activePlugins) { this.activePlugins = activePlugins; }

        public Instant getInitializationTime() { return initializationTime; }
        public void setInitializationTime(Instant initializationTime) { this.initializationTime = initializationTime; }
    }
}
