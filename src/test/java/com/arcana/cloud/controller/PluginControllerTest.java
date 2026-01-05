package com.arcana.cloud.controller;

import com.arcana.cloud.config.TestSecurityConfig;
import com.arcana.cloud.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for PluginController.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class PluginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PluginController pluginController;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        // Reset controller state
        pluginController.setPluginsInitialized(false);
    }

    @Nested
    @DisplayName("List Plugins Tests")
    class ListPluginsTests {

        @Test
        @DisplayName("Should list all plugins")
        void shouldListAllPlugins() throws Exception {
            // Given - register a plugin
            pluginController.registerPlugin("test.plugin", "Test Plugin", "1.0.0", "INSTALLED");

            // When/Then
            mockMvc.perform(get("/api/v1/plugins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.key == 'test.plugin')]").exists());
        }

        @Test
        @DisplayName("Should return empty list when no plugins")
        void shouldReturnEmptyListWhenNoPlugins() throws Exception {
            mockMvc.perform(get("/api/v1/plugins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested
    @DisplayName("Get Plugin Tests")
    class GetPluginTests {

        @Test
        @DisplayName("Should get single plugin")
        void shouldGetSinglePlugin() throws Exception {
            // Given
            pluginController.registerPlugin("test.plugin", "Test Plugin", "1.0.0", "ACTIVE");

            // When/Then
            mockMvc.perform(get("/api/v1/plugins/test.plugin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.key").value("test.plugin"))
                .andExpect(jsonPath("$.data.name").value("Test Plugin"))
                .andExpect(jsonPath("$.data.version").value("1.0.0"))
                .andExpect(jsonPath("$.data.state").value("ACTIVE"));
        }

        @Test
        @DisplayName("Should return 404 for non-existent plugin")
        void shouldReturn404ForNonExistentPlugin() throws Exception {
            mockMvc.perform(get("/api/v1/plugins/non.existent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("Install Plugin Tests")
    class InstallPluginTests {

        @Test
        @DisplayName("Should install plugin from JAR file")
        void shouldInstallPluginFromJarFile() throws Exception {
            // Given
            MockMultipartFile pluginFile = new MockMultipartFile(
                "file",
                "test-plugin-1.0.0.jar",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "fake jar content".getBytes()
            );

            // When/Then
            mockMvc.perform(multipart("/api/v1/plugins/install")
                    .file(pluginFile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.key").value("test-plugin"));
        }

        @Test
        @DisplayName("Should reject non-JAR file")
        void shouldRejectNonJarFile() throws Exception {
            // Given
            MockMultipartFile nonJarFile = new MockMultipartFile(
                "file",
                "test-plugin.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "not a jar".getBytes()
            );

            // When/Then
            mockMvc.perform(multipart("/api/v1/plugins/install")
                    .file(nonJarFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("File must be a JAR file"));
        }

        @Test
        @DisplayName("Should reject empty file")
        void shouldRejectEmptyFile() throws Exception {
            // Given
            MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "test-plugin.jar",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new byte[0]
            );

            // When/Then
            mockMvc.perform(multipart("/api/v1/plugins/install")
                    .file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("No file provided"));
        }
    }

    @Nested
    @DisplayName("Enable Plugin Tests")
    class EnablePluginTests {

        @Test
        @DisplayName("Should enable plugin")
        void shouldEnablePlugin() throws Exception {
            // Given
            pluginController.registerPlugin("test.plugin", "Test Plugin", "1.0.0", "INSTALLED");

            // When/Then
            mockMvc.perform(post("/api/v1/plugins/test.plugin/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.state").value("ACTIVE"));
        }

        @Test
        @DisplayName("Should return 404 when enabling non-existent plugin")
        void shouldReturn404WhenEnablingNonExistentPlugin() throws Exception {
            mockMvc.perform(post("/api/v1/plugins/non.existent/enable"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("Disable Plugin Tests")
    class DisablePluginTests {

        @Test
        @DisplayName("Should disable plugin")
        void shouldDisablePlugin() throws Exception {
            // Given
            pluginController.registerPlugin("test.plugin", "Test Plugin", "1.0.0", "ACTIVE");

            // When/Then
            mockMvc.perform(post("/api/v1/plugins/test.plugin/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.state").value("RESOLVED"));
        }

        @Test
        @DisplayName("Should return 404 when disabling non-existent plugin")
        void shouldReturn404WhenDisablingNonExistentPlugin() throws Exception {
            mockMvc.perform(post("/api/v1/plugins/non.existent/disable"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("Uninstall Plugin Tests")
    class UninstallPluginTests {

        @Test
        @DisplayName("Should uninstall plugin")
        void shouldUninstallPlugin() throws Exception {
            // Given
            pluginController.registerPlugin("test.plugin", "Test Plugin", "1.0.0", "INSTALLED");

            // When/Then
            mockMvc.perform(delete("/api/v1/plugins/test.plugin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

            // Verify plugin is removed
            mockMvc.perform(get("/api/v1/plugins/test.plugin"))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 404 when uninstalling non-existent plugin")
        void shouldReturn404WhenUninstallingNonExistentPlugin() throws Exception {
            mockMvc.perform(delete("/api/v1/plugins/non.existent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("Health Check Tests")
    class HealthCheckTests {

        @Test
        @DisplayName("Should return health status")
        void shouldReturnHealthStatus() throws Exception {
            // Given - initialize the plugin system
            pluginController.setPluginsInitialized(true);

            // When/Then
            mockMvc.perform(get("/api/v1/plugins/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enabled").exists())
                .andExpect(jsonPath("$.data.initialized").exists())
                .andExpect(jsonPath("$.data.totalPlugins").exists())
                .andExpect(jsonPath("$.data.activePlugins").exists());
        }

        @Test
        @DisplayName("Should return 503 when not initialized")
        void shouldReturn503WhenNotInitialized() throws Exception {
            mockMvc.perform(get("/api/v1/plugins/health"))
                .andExpect(status().is5xxServerError());
        }

        @Test
        @DisplayName("Should return healthy status when initialized")
        void shouldReturnHealthyStatusWhenInitialized() throws Exception {
            // Given
            pluginController.setPluginsInitialized(true);

            // When/Then
            mockMvc.perform(get("/api/v1/plugins/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.initialized").value(true));
        }
    }

    @Nested
    @DisplayName("Readiness Check Tests")
    class ReadinessCheckTests {

        @Test
        @DisplayName("Should return UP when initialized")
        void shouldReturnUpWhenInitialized() throws Exception {
            // Given
            pluginController.setPluginsInitialized(true);

            // When/Then
            mockMvc.perform(get("/api/v1/plugins/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.pluginsInitialized").value(true));
        }

        @Test
        @DisplayName("Should return DOWN when not initialized")
        void shouldReturnDownWhenNotInitialized() throws Exception {
            mockMvc.perform(get("/api/v1/plugins/health/ready"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.pluginsInitialized").value(false));
        }
    }

    @Nested
    @DisplayName("Liveness Check Tests")
    class LivenessCheckTests {

        @Test
        @DisplayName("Should always return UP for liveness")
        void shouldAlwaysReturnUpForLiveness() throws Exception {
            mockMvc.perform(get("/api/v1/plugins/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        }
    }

    @Nested
    @DisplayName("Plugin Workflow Tests")
    class PluginWorkflowTests {

        @Test
        @DisplayName("Should complete full plugin lifecycle")
        void shouldCompleteFullPluginLifecycle() throws Exception {
            // 1. Install plugin
            MockMultipartFile pluginFile = new MockMultipartFile(
                "file",
                "lifecycle-plugin-1.0.0.jar",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "fake jar content".getBytes()
            );

            mockMvc.perform(multipart("/api/v1/plugins/install")
                    .file(pluginFile))
                .andExpect(status().isCreated());

            // 2. Enable plugin
            mockMvc.perform(post("/api/v1/plugins/lifecycle-plugin/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("ACTIVE"));

            // 3. Verify plugin is active
            mockMvc.perform(get("/api/v1/plugins/lifecycle-plugin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("ACTIVE"));

            // 4. Disable plugin
            mockMvc.perform(post("/api/v1/plugins/lifecycle-plugin/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("RESOLVED"));

            // 5. Uninstall plugin
            mockMvc.perform(delete("/api/v1/plugins/lifecycle-plugin"))
                .andExpect(status().isOk());

            // 6. Verify plugin is gone
            mockMvc.perform(get("/api/v1/plugins/lifecycle-plugin"))
                .andExpect(status().isNotFound());
        }
    }
}
