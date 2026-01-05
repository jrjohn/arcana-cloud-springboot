package com.arcana.cloud.integration.plugin;

import com.arcana.cloud.config.TestSecurityConfig;
import com.arcana.cloud.controller.PluginController;
import com.arcana.cloud.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for plugin system in Monolithic deployment mode.
 *
 * <p>Tests the complete plugin lifecycle in a single-instance deployment
 * where all layers run in the same JVM.</p>
 *
 * <p>Deployment characteristics:</p>
 * <ul>
 *   <li>Single JVM with all layers</li>
 *   <li>Direct method calls between layers</li>
 *   <li>Local plugin storage</li>
 *   <li>No distributed state synchronization</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Plugin Monolithic Mode Integration Tests")
@Import(TestSecurityConfig.class)
@Timeout(60)
class PluginMonolithicModeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PluginController pluginController;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private static String installedPluginKey;

    @BeforeEach
    void setUp() {
        // Ensure plugin system is initialized for tests
        pluginController.setPluginsInitialized(true);
    }

    @Nested
    @DisplayName("Plugin Installation Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class InstallationTests {

        @Test
        @Order(1)
        @DisplayName("Should install plugin via REST API")
        void shouldInstallPluginViaRestApi() throws Exception {
            // Given
            MockMultipartFile pluginFile = new MockMultipartFile(
                "file",
                "monolithic-test-plugin-1.0.0.jar",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "mock plugin content".getBytes()
            );

            // When
            MvcResult result = mockMvc.perform(multipart("/api/v1/plugins/install")
                    .file(pluginFile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.key").exists())
                .andReturn();

            // Then
            JsonNode response = jsonMapper.readTree(result.getResponse().getContentAsString());
            installedPluginKey = response.get("data").get("key").stringValue();
            assertNotNull(installedPluginKey);
            assertEquals("monolithic-test-plugin", installedPluginKey);
        }

        @Test
        @Order(2)
        @DisplayName("Should list installed plugin")
        void shouldListInstalledPlugin() throws Exception {
            // Skip if not installed
            if (installedPluginKey == null) {
                installedPluginKey = "monolithic-test-plugin";
                pluginController.registerPlugin(installedPluginKey, "Monolithic Test Plugin", "1.0.0", "INSTALLED");
            }

            // When/Then
            mockMvc.perform(get("/api/v1/plugins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.key == '" + installedPluginKey + "')]").exists());
        }

        @Test
        @Order(3)
        @DisplayName("Should get plugin details")
        void shouldGetPluginDetails() throws Exception {
            // Skip if not installed
            if (installedPluginKey == null) {
                installedPluginKey = "monolithic-test-plugin";
                pluginController.registerPlugin(installedPluginKey, "Monolithic Test Plugin", "1.0.0", "INSTALLED");
            }

            // When/Then
            mockMvc.perform(get("/api/v1/plugins/" + installedPluginKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.key").value(installedPluginKey))
                .andExpect(jsonPath("$.data.version").exists())
                .andExpect(jsonPath("$.data.state").exists());
        }
    }

    @Nested
    @DisplayName("Plugin Lifecycle Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class LifecycleTests {

        @Test
        @Order(1)
        @DisplayName("Should enable plugin")
        void shouldEnablePlugin() throws Exception {
            // Given
            String pluginKey = "lifecycle-test-plugin";
            pluginController.registerPlugin(pluginKey, "Lifecycle Test Plugin", "1.0.0", "INSTALLED");

            // When/Then
            mockMvc.perform(post("/api/v1/plugins/" + pluginKey + "/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.state").value("ACTIVE"));
        }

        @Test
        @Order(2)
        @DisplayName("Should disable plugin")
        void shouldDisablePlugin() throws Exception {
            // Given
            String pluginKey = "disable-test-plugin";
            pluginController.registerPlugin(pluginKey, "Disable Test Plugin", "1.0.0", "ACTIVE");

            // When/Then
            mockMvc.perform(post("/api/v1/plugins/" + pluginKey + "/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.state").value("RESOLVED"));
        }

        @Test
        @Order(3)
        @DisplayName("Should uninstall plugin")
        void shouldUninstallPlugin() throws Exception {
            // Given
            String pluginKey = "uninstall-test-plugin";
            pluginController.registerPlugin(pluginKey, "Uninstall Test Plugin", "1.0.0", "RESOLVED");

            // When
            mockMvc.perform(delete("/api/v1/plugins/" + pluginKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

            // Then - verify removed
            mockMvc.perform(get("/api/v1/plugins/" + pluginKey))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Plugin Health Check Tests")
    class HealthCheckTests {

        @Test
        @DisplayName("Should return healthy status when initialized")
        void shouldReturnHealthyStatusWhenInitialized() throws Exception {
            // Given
            pluginController.setPluginsInitialized(true);

            // When/Then
            mockMvc.perform(get("/api/v1/plugins/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.initialized").value(true));
        }

        @Test
        @DisplayName("Should return UP for readiness when ready")
        void shouldReturnUpForReadinessWhenReady() throws Exception {
            // Given
            pluginController.setPluginsInitialized(true);

            // When/Then
            mockMvc.perform(get("/api/v1/plugins/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.pluginsInitialized").value(true));
        }

        @Test
        @DisplayName("Should return DOWN for readiness when not ready")
        void shouldReturnDownForReadinessWhenNotReady() throws Exception {
            // Given
            pluginController.setPluginsInitialized(false);

            // When/Then
            mockMvc.perform(get("/api/v1/plugins/health/ready"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
        }

        @Test
        @DisplayName("Should always return UP for liveness")
        void shouldAlwaysReturnUpForLiveness() throws Exception {
            // When/Then
            mockMvc.perform(get("/api/v1/plugins/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        }
    }

    @Nested
    @DisplayName("Plugin Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should return 404 for non-existent plugin")
        void shouldReturn404ForNonExistentPlugin() throws Exception {
            mockMvc.perform(get("/api/v1/plugins/non.existent.plugin"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("Should return 400 for invalid plugin file")
        void shouldReturn400ForInvalidPluginFile() throws Exception {
            // Given
            MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "invalid.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "not a plugin".getBytes()
            );

            // When/Then
            mockMvc.perform(multipart("/api/v1/plugins/install")
                    .file(invalidFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("File must be a JAR file"));
        }

        @Test
        @DisplayName("Should handle enable on non-existent plugin")
        void shouldHandleEnableOnNonExistentPlugin() throws Exception {
            mockMvc.perform(post("/api/v1/plugins/non.existent/enable"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("Should handle disable on non-existent plugin")
        void shouldHandleDisableOnNonExistentPlugin() throws Exception {
            mockMvc.perform(post("/api/v1/plugins/non.existent/disable"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("Complete Plugin Workflow Tests")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class WorkflowTests {

        private static String workflowPluginKey;

        @Test
        @Order(1)
        @DisplayName("Should complete full plugin lifecycle workflow")
        void shouldCompleteFullPluginLifecycleWorkflow() throws Exception {
            // Step 1: Install
            MockMultipartFile pluginFile = new MockMultipartFile(
                "file",
                "workflow-plugin-1.0.0.jar",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "plugin content".getBytes()
            );

            MvcResult installResult = mockMvc.perform(multipart("/api/v1/plugins/install")
                    .file(pluginFile))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode response = jsonMapper.readTree(installResult.getResponse().getContentAsString());
            workflowPluginKey = response.get("data").get("key").stringValue();

            // Step 2: Verify installed state
            mockMvc.perform(get("/api/v1/plugins/" + workflowPluginKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("INSTALLED"));

            // Step 3: Enable
            mockMvc.perform(post("/api/v1/plugins/" + workflowPluginKey + "/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("ACTIVE"));

            // Step 4: Verify active state
            mockMvc.perform(get("/api/v1/plugins/" + workflowPluginKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("ACTIVE"));

            // Step 5: Disable
            mockMvc.perform(post("/api/v1/plugins/" + workflowPluginKey + "/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("RESOLVED"));

            // Step 6: Re-enable
            mockMvc.perform(post("/api/v1/plugins/" + workflowPluginKey + "/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("ACTIVE"));

            // Step 7: Disable again before uninstall
            mockMvc.perform(post("/api/v1/plugins/" + workflowPluginKey + "/disable"))
                .andExpect(status().isOk());

            // Step 8: Uninstall
            mockMvc.perform(delete("/api/v1/plugins/" + workflowPluginKey))
                .andExpect(status().isOk());

            // Step 9: Verify uninstalled
            mockMvc.perform(get("/api/v1/plugins/" + workflowPluginKey))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Multiple Plugin Tests")
    class MultiplePluginTests {

        @Test
        @DisplayName("Should manage multiple plugins independently")
        void shouldManageMultiplePluginsIndependently() throws Exception {
            // Given - register multiple plugins
            pluginController.registerPlugin("multi-plugin-1", "Multi Plugin 1", "1.0.0", "INSTALLED");
            pluginController.registerPlugin("multi-plugin-2", "Multi Plugin 2", "2.0.0", "INSTALLED");
            pluginController.registerPlugin("multi-plugin-3", "Multi Plugin 3", "3.0.0", "ACTIVE");

            // When - list all plugins
            mockMvc.perform(get("/api/v1/plugins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));

            // Then - enable plugin 1
            mockMvc.perform(post("/api/v1/plugins/multi-plugin-1/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("ACTIVE"));

            // And - plugin 2 should still be INSTALLED
            mockMvc.perform(get("/api/v1/plugins/multi-plugin-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("INSTALLED"));

            // And - plugin 3 should still be ACTIVE
            mockMvc.perform(get("/api/v1/plugins/multi-plugin-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("ACTIVE"));
        }
    }
}
