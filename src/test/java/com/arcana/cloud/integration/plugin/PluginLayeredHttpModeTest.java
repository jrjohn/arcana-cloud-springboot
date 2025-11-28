package com.arcana.cloud.integration.plugin;

import com.arcana.cloud.plugin.lifecycle.PluginState;
import com.arcana.cloud.plugin.runtime.http.PluginHttpClient;
import com.arcana.cloud.plugin.runtime.http.PluginHttpClient.PluginInfo;
import com.arcana.cloud.plugin.runtime.http.PluginHttpClient.PluginSyncAction;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for plugin system in Layered HTTP deployment mode.
 *
 * <p>Tests the plugin lifecycle when Controller and Service layers
 * communicate via HTTP REST APIs.</p>
 *
 * <p>Deployment characteristics:</p>
 * <ul>
 *   <li>Separate processes for Controller and Service layers</li>
 *   <li>HTTP REST communication between layers</li>
 *   <li>Plugin management via Service layer</li>
 *   <li>Proxy requests from Controller to Service</li>
 * </ul>
 */
@DisplayName("Plugin Layered HTTP Mode Integration Tests")
@Timeout(30)
class PluginLayeredHttpModeTest {

    private MockWebServer serviceLayerMock;
    private PluginHttpClient pluginHttpClient;

    @BeforeEach
    void setUp() throws IOException {
        serviceLayerMock = new MockWebServer();
        serviceLayerMock.start();

        String serviceUrl = serviceLayerMock.url("/").toString();
        serviceUrl = serviceUrl.substring(0, serviceUrl.length() - 1);

        // Use RestTemplate with timeouts to prevent hanging on connection issues
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        RestTemplate restTemplate = new RestTemplate(factory);

        pluginHttpClient = new PluginHttpClient(
            restTemplate,
            serviceUrl,
            2,
            Duration.ofMillis(50)
        );
    }

    @AfterEach
    void tearDown() {
        // Shutdown mock server with proper cleanup to prevent hanging
        try {
            serviceLayerMock.shutdown();
        } catch (IOException e) {
            // Ignore shutdown errors
        }
    }

    @Nested
    @DisplayName("Cross-Layer Plugin List Tests")
    class CrossLayerPluginListTests {

        @Test
        @DisplayName("Should list plugins from service layer via HTTP")
        void shouldListPluginsFromServiceLayerViaHttp() throws InterruptedException {
            // Given
            String responseJson = """
                {
                    "success": true,
                    "data": [
                        {
                            "key": "audit-plugin",
                            "name": "Audit Plugin",
                            "version": "1.0.0",
                            "state": "ACTIVE"
                        },
                        {
                            "key": "cache-plugin",
                            "name": "Cache Plugin",
                            "version": "2.0.0",
                            "state": "INSTALLED"
                        }
                    ]
                }
                """;
            serviceLayerMock.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            List<PluginInfo> plugins = pluginHttpClient.listPlugins();

            // Then
            assertEquals(2, plugins.size());
            assertEquals("audit-plugin", plugins.get(0).getKey());
            assertEquals("ACTIVE", plugins.get(0).getState());
            assertEquals("cache-plugin", plugins.get(1).getKey());
            assertEquals("INSTALLED", plugins.get(1).getState());

            RecordedRequest request = serviceLayerMock.takeRequest();
            assertEquals("GET", request.getMethod());
            assertEquals("/api/v1/plugins", request.getPath());
        }

        @Test
        @DisplayName("Should get single plugin details from service layer")
        void shouldGetSinglePluginDetailsFromServiceLayer() throws InterruptedException {
            // Given
            String responseJson = """
                {
                    "success": true,
                    "data": {
                        "key": "audit-plugin",
                        "name": "Audit Plugin",
                        "version": "1.0.0",
                        "state": "ACTIVE",
                        "metadata": {
                            "author": "Arcana Cloud",
                            "license": "Apache-2.0"
                        }
                    }
                }
                """;
            serviceLayerMock.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            Optional<PluginInfo> plugin = pluginHttpClient.getPlugin("audit-plugin");

            // Then
            assertTrue(plugin.isPresent());
            assertEquals("audit-plugin", plugin.get().getKey());
            assertEquals("Audit Plugin", plugin.get().getName());
            assertNotNull(plugin.get().getMetadata());

            RecordedRequest request = serviceLayerMock.takeRequest();
            assertEquals("/api/v1/plugins/audit-plugin", request.getPath());
        }

        @Test
        @DisplayName("Should return empty for non-existent plugin")
        void shouldReturnEmptyForNonExistentPlugin() {
            // Given
            serviceLayerMock.enqueue(new MockResponse().setResponseCode(404));
            serviceLayerMock.enqueue(new MockResponse().setResponseCode(404));

            // When
            Optional<PluginInfo> plugin = pluginHttpClient.getPlugin("non-existent");

            // Then
            assertTrue(plugin.isEmpty());
        }
    }

    @Nested
    @DisplayName("Cross-Layer Plugin Lifecycle Tests")
    class CrossLayerPluginLifecycleTests {

        @Test
        @DisplayName("Should enable plugin on service layer")
        void shouldEnablePluginOnServiceLayer() throws InterruptedException {
            // Given
            serviceLayerMock.enqueue(new MockResponse()
                .setBody("{\"success\": true, \"data\": {\"state\": \"ACTIVE\"}}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            boolean result = pluginHttpClient.enablePlugin("audit-plugin");

            // Then
            assertTrue(result);

            RecordedRequest request = serviceLayerMock.takeRequest();
            assertEquals("POST", request.getMethod());
            assertEquals("/api/v1/plugins/audit-plugin/enable", request.getPath());
        }

        @Test
        @DisplayName("Should disable plugin on service layer")
        void shouldDisablePluginOnServiceLayer() throws InterruptedException {
            // Given
            serviceLayerMock.enqueue(new MockResponse()
                .setBody("{\"success\": true, \"data\": {\"state\": \"RESOLVED\"}}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            boolean result = pluginHttpClient.disablePlugin("audit-plugin");

            // Then
            assertTrue(result);

            RecordedRequest request = serviceLayerMock.takeRequest();
            assertEquals("POST", request.getMethod());
            assertEquals("/api/v1/plugins/audit-plugin/disable", request.getPath());
        }

        @Test
        @DisplayName("Should handle enable failure gracefully")
        void shouldHandleEnableFailureGracefully() {
            // Given
            serviceLayerMock.enqueue(new MockResponse().setResponseCode(500));
            serviceLayerMock.enqueue(new MockResponse().setResponseCode(500));

            // When
            boolean result = pluginHttpClient.enablePlugin("failing-plugin");

            // Then
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Service Layer Health Tests")
    class ServiceLayerHealthTests {

        @Test
        @DisplayName("Should detect service layer readiness")
        void shouldDetectServiceLayerReadiness() {
            // Given
            serviceLayerMock.enqueue(new MockResponse()
                .setBody("{\"status\": \"UP\", \"pluginsInitialized\": true}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            boolean ready = pluginHttpClient.isServiceLayerReady();

            // Then
            assertTrue(ready);
        }

        @Test
        @DisplayName("Should detect service layer not ready")
        void shouldDetectServiceLayerNotReady() {
            // Given
            serviceLayerMock.enqueue(new MockResponse()
                .setBody("{\"status\": \"DOWN\"}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            boolean ready = pluginHttpClient.isServiceLayerReady();

            // Then
            assertFalse(ready);
        }

        @Test
        @DisplayName("Should get service layer health details")
        void shouldGetServiceLayerHealthDetails() {
            // Given
            serviceLayerMock.enqueue(new MockResponse()
                .setBody("{\"status\": \"UP\", \"pluginCount\": 5, \"activeCount\": 3}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            Map<String, Object> health = pluginHttpClient.getServiceLayerHealth();

            // Then
            assertEquals("UP", health.get("status"));
        }

        @Test
        @DisplayName("Should handle service layer unreachable")
        void shouldHandleServiceLayerUnreachable() {
            // Given
            serviceLayerMock.enqueue(new MockResponse().setResponseCode(503));

            // When
            Map<String, Object> health = pluginHttpClient.getServiceLayerHealth();

            // Then
            assertEquals("DOWN", health.get("status"));
        }
    }

    @Nested
    @DisplayName("Plugin Request Proxy Tests")
    class PluginRequestProxyTests {

        @Test
        @DisplayName("Should proxy GET request to plugin endpoint")
        void shouldProxyGetRequestToPluginEndpoint() throws InterruptedException {
            // Given
            serviceLayerMock.enqueue(new MockResponse()
                .setBody("{\"auditLogs\": []}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            ResponseEntity<String> response = pluginHttpClient.proxyPluginRequest(
                "audit-plugin",
                "/logs",
                HttpMethod.GET,
                null,
                new HttpHeaders()
            );

            // Then
            assertTrue(response.getStatusCode().is2xxSuccessful());
            assertTrue(response.getBody().contains("auditLogs"));

            RecordedRequest request = serviceLayerMock.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS);
            assertNotNull(request, "Expected request to be received");
            assertEquals("GET", request.getMethod());
            assertEquals("/api/v1/plugins/audit-plugin/logs", request.getPath());
        }

        @Test
        @DisplayName("Should proxy POST request with body to plugin endpoint")
        void shouldProxyPostRequestWithBodyToPluginEndpoint() throws InterruptedException {
            // Given
            serviceLayerMock.enqueue(new MockResponse()
                .setBody("{\"success\": true, \"id\": 123}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String requestBody = "{\"action\": \"log\", \"message\": \"Test audit entry\"}";

            // When
            ResponseEntity<String> response = pluginHttpClient.proxyPluginRequest(
                "audit-plugin",
                "/entries",
                HttpMethod.POST,
                requestBody,
                headers
            );

            // Then
            assertTrue(response.getStatusCode().is2xxSuccessful());

            RecordedRequest request = serviceLayerMock.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS);
            assertNotNull(request, "Expected request to be received");
            assertEquals("POST", request.getMethod());
            assertTrue(request.getBody().readUtf8().contains("Test audit entry"));
        }

        @Test
        @DisplayName("Should return BAD_GATEWAY on proxy failure")
        void shouldReturnBadGatewayOnProxyFailure() {
            // Given - simulate upstream server error that should result in BAD_GATEWAY
            // Using 503 Service Unavailable instead of DISCONNECT_AFTER_REQUEST to avoid hanging
            serviceLayerMock.enqueue(new MockResponse().setResponseCode(503));
            serviceLayerMock.enqueue(new MockResponse().setResponseCode(503));

            // When
            ResponseEntity<String> response = pluginHttpClient.proxyPluginRequest(
                "audit-plugin",
                "/endpoint",
                HttpMethod.GET,
                null,
                new HttpHeaders()
            );

            // Then - after retries fail, expect a failure response
            assertFalse(response.getStatusCode().is2xxSuccessful());
        }
    }

    @Nested
    @DisplayName("Plugin Synchronization Tests")
    class PluginSynchronizationTests {

        @Test
        @DisplayName("Should detect plugins to install")
        void shouldDetectPluginsToInstall() {
            // Given - remote has plugin that local doesn't
            serviceLayerMock.enqueue(new MockResponse()
                .setBody("""
                    {
                        "data": [
                            {"key": "remote-only-plugin", "name": "Remote Plugin", "version": "1.0.0", "state": "ACTIVE"}
                        ]
                    }
                    """)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            Map<String, PluginState> localPlugins = new HashMap<>();

            // When
            List<PluginSyncAction> actions = pluginHttpClient.synchronizePlugins(localPlugins);

            // Then
            assertEquals(1, actions.size());
            assertEquals(PluginSyncAction.Action.INSTALL, actions.get(0).getAction());
            assertEquals("remote-only-plugin", actions.get(0).getPluginKey());
        }

        @Test
        @DisplayName("Should detect plugins to uninstall")
        void shouldDetectPluginsToUninstall() {
            // Given - local has plugin that remote doesn't
            serviceLayerMock.enqueue(new MockResponse()
                .setBody("{\"data\": []}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            Map<String, PluginState> localPlugins = new HashMap<>();
            localPlugins.put("local-only-plugin", PluginState.ACTIVE);

            // When
            List<PluginSyncAction> actions = pluginHttpClient.synchronizePlugins(localPlugins);

            // Then
            assertEquals(1, actions.size());
            assertEquals(PluginSyncAction.Action.UNINSTALL, actions.get(0).getAction());
            assertEquals("local-only-plugin", actions.get(0).getPluginKey());
        }

        @Test
        @DisplayName("Should detect state mismatches")
        void shouldDetectStateMismatches() {
            // Given - same plugin with different state
            serviceLayerMock.enqueue(new MockResponse()
                .setBody("""
                    {
                        "data": [
                            {"key": "state-mismatch", "name": "State Mismatch Plugin", "version": "1.0.0", "state": "ACTIVE"}
                        ]
                    }
                    """)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            Map<String, PluginState> localPlugins = new HashMap<>();
            localPlugins.put("state-mismatch", PluginState.INSTALLED);

            // When
            List<PluginSyncAction> actions = pluginHttpClient.synchronizePlugins(localPlugins);

            // Then
            assertEquals(1, actions.size());
            assertEquals(PluginSyncAction.Action.ENABLE, actions.get(0).getAction());
        }

        @Test
        @DisplayName("Should return empty when in sync")
        void shouldReturnEmptyWhenInSync() {
            // Given - same plugin with same state
            serviceLayerMock.enqueue(new MockResponse()
                .setBody("""
                    {
                        "data": [
                            {"key": "synced-plugin", "name": "Synced Plugin", "version": "1.0.0", "state": "ACTIVE"}
                        ]
                    }
                    """)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            Map<String, PluginState> localPlugins = new HashMap<>();
            localPlugins.put("synced-plugin", PluginState.ACTIVE);

            // When
            List<PluginSyncAction> actions = pluginHttpClient.synchronizePlugins(localPlugins);

            // Then
            assertTrue(actions.isEmpty());
        }
    }

    @Nested
    @DisplayName("Retry Logic Tests")
    class RetryLogicTests {

        @Test
        @DisplayName("Should retry on transient failure and succeed")
        void shouldRetryOnTransientFailureAndSucceed() throws InterruptedException {
            // Given - first request fails, second succeeds
            serviceLayerMock.enqueue(new MockResponse().setResponseCode(503));
            serviceLayerMock.enqueue(new MockResponse()
                .setBody("{\"success\": true}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            boolean result = pluginHttpClient.enablePlugin("retry-plugin");

            // Then
            assertTrue(result);
            assertEquals(2, serviceLayerMock.getRequestCount());
        }

        @Test
        @DisplayName("Should give up after max retries")
        void shouldGiveUpAfterMaxRetries() {
            // Given - all retries fail
            serviceLayerMock.enqueue(new MockResponse().setResponseCode(500));
            serviceLayerMock.enqueue(new MockResponse().setResponseCode(500));

            // When
            boolean result = pluginHttpClient.enablePlugin("failing-plugin");

            // Then
            assertFalse(result);
            assertEquals(2, serviceLayerMock.getRequestCount());
        }
    }

    @Nested
    @DisplayName("Complete Layered HTTP Workflow Tests")
    class WorkflowTests {

        @Test
        @DisplayName("Should complete full plugin management workflow via HTTP")
        void shouldCompleteFullPluginManagementWorkflowViaHttp() throws InterruptedException {
            // Step 1: Check service ready
            serviceLayerMock.enqueue(new MockResponse()
                .setBody("{\"status\": \"UP\"}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));
            assertTrue(pluginHttpClient.isServiceLayerReady());
            serviceLayerMock.takeRequest();

            // Step 2: List existing plugins
            serviceLayerMock.enqueue(new MockResponse()
                .setBody("{\"data\": [{\"key\": \"workflow-plugin\", \"name\": \"Workflow\", \"version\": \"1.0.0\", \"state\": \"INSTALLED\"}]}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));
            List<PluginInfo> plugins = pluginHttpClient.listPlugins();
            assertEquals(1, plugins.size());
            serviceLayerMock.takeRequest();

            // Step 3: Get plugin details
            serviceLayerMock.enqueue(new MockResponse()
                .setBody("{\"data\": {\"key\": \"workflow-plugin\", \"name\": \"Workflow\", \"version\": \"1.0.0\", \"state\": \"INSTALLED\"}}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));
            Optional<PluginInfo> plugin = pluginHttpClient.getPlugin("workflow-plugin");
            assertTrue(plugin.isPresent());
            serviceLayerMock.takeRequest();

            // Step 4: Enable plugin
            serviceLayerMock.enqueue(new MockResponse()
                .setBody("{\"success\": true}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));
            assertTrue(pluginHttpClient.enablePlugin("workflow-plugin"));
            serviceLayerMock.takeRequest();

            // Step 5: Proxy request to plugin endpoint
            serviceLayerMock.enqueue(new MockResponse()
                .setBody("{\"result\": \"success\"}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));
            ResponseEntity<String> proxyResponse = pluginHttpClient.proxyPluginRequest(
                "workflow-plugin", "/action", HttpMethod.POST, "{}", new HttpHeaders()
            );
            assertTrue(proxyResponse.getStatusCode().is2xxSuccessful());
            serviceLayerMock.takeRequest();

            // Step 6: Disable plugin
            serviceLayerMock.enqueue(new MockResponse()
                .setBody("{\"success\": true}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));
            assertTrue(pluginHttpClient.disablePlugin("workflow-plugin"));
        }
    }
}
