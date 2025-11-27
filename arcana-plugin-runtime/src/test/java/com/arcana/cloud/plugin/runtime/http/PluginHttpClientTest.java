package com.arcana.cloud.plugin.runtime.http;

import com.arcana.cloud.plugin.lifecycle.PluginState;
import com.arcana.cloud.plugin.runtime.http.PluginHttpClient.PluginInfo;
import com.arcana.cloud.plugin.runtime.http.PluginHttpClient.PluginSyncAction;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PluginHttpClient.
 */
class PluginHttpClientTest {

    private MockWebServer mockServer;
    private PluginHttpClient client;
    private String serverUrl;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        serverUrl = mockServer.url("/").toString();
        // Remove trailing slash
        serverUrl = serverUrl.substring(0, serverUrl.length() - 1);

        client = new PluginHttpClient(
            new RestTemplate(),
            serverUrl,
            2, // maxRetries
            Duration.ofMillis(50) // retryDelay
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Nested
    @DisplayName("List Plugins Tests")
    class ListPluginsTests {

        @Test
        @DisplayName("Should list all plugins")
        void shouldListAllPlugins() throws InterruptedException {
            // Given
            String responseJson = """
                {
                    "data": [
                        {
                            "key": "plugin1",
                            "name": "Plugin 1",
                            "version": "1.0.0",
                            "state": "ACTIVE"
                        },
                        {
                            "key": "plugin2",
                            "name": "Plugin 2",
                            "version": "2.0.0",
                            "state": "INSTALLED"
                        }
                    ]
                }
                """;
            mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            List<PluginInfo> plugins = client.listPlugins();

            // Then
            assertEquals(2, plugins.size());
            assertEquals("plugin1", plugins.get(0).getKey());
            assertEquals("Plugin 1", plugins.get(0).getName());
            assertEquals("1.0.0", plugins.get(0).getVersion());
            assertEquals("ACTIVE", plugins.get(0).getState());

            RecordedRequest request = mockServer.takeRequest();
            assertEquals("/api/v1/plugins", request.getPath());
            assertEquals("GET", request.getMethod());
        }

        @Test
        @DisplayName("Should return empty list on error")
        void shouldReturnEmptyListOnError() {
            // Given
            mockServer.enqueue(new MockResponse().setResponseCode(500));
            mockServer.enqueue(new MockResponse().setResponseCode(500));

            // When
            List<PluginInfo> plugins = client.listPlugins();

            // Then
            assertTrue(plugins.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list when data is null")
        void shouldReturnEmptyListWhenDataIsNull() {
            // Given
            String responseJson = """
                {
                    "data": null
                }
                """;
            mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            List<PluginInfo> plugins = client.listPlugins();

            // Then
            assertTrue(plugins.isEmpty());
        }
    }

    @Nested
    @DisplayName("Get Plugin Tests")
    class GetPluginTests {

        @Test
        @DisplayName("Should get single plugin")
        void shouldGetSinglePlugin() throws InterruptedException {
            // Given
            String responseJson = """
                {
                    "data": {
                        "key": "test.plugin",
                        "name": "Test Plugin",
                        "version": "1.0.0",
                        "state": "ACTIVE",
                        "metadata": {
                            "author": "Test Author"
                        }
                    }
                }
                """;
            mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            Optional<PluginInfo> result = client.getPlugin("test.plugin");

            // Then
            assertTrue(result.isPresent());
            assertEquals("test.plugin", result.get().getKey());
            assertEquals("Test Plugin", result.get().getName());
            assertNotNull(result.get().getMetadata());

            RecordedRequest request = mockServer.takeRequest();
            assertEquals("/api/v1/plugins/test.plugin", request.getPath());
        }

        @Test
        @DisplayName("Should return empty for non-existent plugin")
        void shouldReturnEmptyForNonExistentPlugin() {
            // Given
            mockServer.enqueue(new MockResponse().setResponseCode(404));
            mockServer.enqueue(new MockResponse().setResponseCode(404));

            // When
            Optional<PluginInfo> result = client.getPlugin("non.existent");

            // Then
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Enable Plugin Tests")
    class EnablePluginTests {

        @Test
        @DisplayName("Should enable plugin successfully")
        void shouldEnablePluginSuccessfully() throws InterruptedException {
            // Given
            mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"success\": true}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            boolean result = client.enablePlugin("test.plugin");

            // Then
            assertTrue(result);

            RecordedRequest request = mockServer.takeRequest();
            assertEquals("/api/v1/plugins/test.plugin/enable", request.getPath());
            assertEquals("POST", request.getMethod());
        }

        @Test
        @DisplayName("Should return false on enable failure")
        void shouldReturnFalseOnEnableFailure() {
            // Given
            mockServer.enqueue(new MockResponse().setResponseCode(500));
            mockServer.enqueue(new MockResponse().setResponseCode(500));

            // When
            boolean result = client.enablePlugin("test.plugin");

            // Then
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Disable Plugin Tests")
    class DisablePluginTests {

        @Test
        @DisplayName("Should disable plugin successfully")
        void shouldDisablePluginSuccessfully() throws InterruptedException {
            // Given
            mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"success\": true}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            boolean result = client.disablePlugin("test.plugin");

            // Then
            assertTrue(result);

            RecordedRequest request = mockServer.takeRequest();
            assertEquals("/api/v1/plugins/test.plugin/disable", request.getPath());
            assertEquals("POST", request.getMethod());
        }

        @Test
        @DisplayName("Should return false on disable failure")
        void shouldReturnFalseOnDisableFailure() {
            // Given
            mockServer.enqueue(new MockResponse().setResponseCode(500));
            mockServer.enqueue(new MockResponse().setResponseCode(500));

            // When
            boolean result = client.disablePlugin("test.plugin");

            // Then
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Service Layer Ready Tests")
    class ServiceLayerReadyTests {

        @Test
        @DisplayName("Should return true when service layer is ready")
        void shouldReturnTrueWhenServiceLayerIsReady() {
            // Given
            mockServer.enqueue(new MockResponse()
                .setBody("{\"status\": \"UP\"}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            boolean ready = client.isServiceLayerReady();

            // Then
            assertTrue(ready);
        }

        @Test
        @DisplayName("Should return false when service layer is not ready")
        void shouldReturnFalseWhenServiceLayerIsNotReady() {
            // Given
            mockServer.enqueue(new MockResponse()
                .setBody("{\"status\": \"DOWN\"}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            boolean ready = client.isServiceLayerReady();

            // Then
            assertFalse(ready);
        }

        @Test
        @DisplayName("Should return false on connection failure")
        void shouldReturnFalseOnConnectionFailure() {
            // Given
            mockServer.enqueue(new MockResponse().setResponseCode(500));

            // When
            boolean ready = client.isServiceLayerReady();

            // Then
            assertFalse(ready);
        }
    }

    @Nested
    @DisplayName("Service Layer Health Tests")
    class ServiceLayerHealthTests {

        @Test
        @DisplayName("Should get service layer health")
        void shouldGetServiceLayerHealth() {
            // Given
            mockServer.enqueue(new MockResponse()
                .setBody("{\"status\": \"UP\", \"pluginCount\": 5}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            Map<String, Object> health = client.getServiceLayerHealth();

            // Then
            assertEquals("UP", health.get("status"));
        }

        @Test
        @DisplayName("Should return DOWN status on failure")
        void shouldReturnDownStatusOnFailure() {
            // Given
            mockServer.enqueue(new MockResponse().setResponseCode(500));

            // When
            Map<String, Object> health = client.getServiceLayerHealth();

            // Then
            assertEquals("DOWN", health.get("status"));
            assertNotNull(health.get("error"));
        }
    }

    @Nested
    @DisplayName("Proxy Plugin Request Tests")
    class ProxyPluginRequestTests {

        @Test
        @DisplayName("Should proxy GET request")
        void shouldProxyGetRequest() throws InterruptedException {
            // Given
            mockServer.enqueue(new MockResponse()
                .setBody("{\"data\": \"response\"}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            ResponseEntity<String> response = client.proxyPluginRequest(
                "test.plugin", "/endpoint", HttpMethod.GET, null, new HttpHeaders()
            );

            // Then
            assertTrue(response.getStatusCode().is2xxSuccessful());

            RecordedRequest request = mockServer.takeRequest();
            assertEquals("/api/v1/plugins/test.plugin/endpoint", request.getPath());
            assertEquals("GET", request.getMethod());
        }

        @Test
        @DisplayName("Should proxy POST request with body")
        void shouldProxyPostRequestWithBody() throws InterruptedException {
            // Given
            mockServer.enqueue(new MockResponse()
                .setBody("{\"success\": true}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // When
            ResponseEntity<String> response = client.proxyPluginRequest(
                "test.plugin", "/action", HttpMethod.POST,
                "{\"action\": \"test\"}", headers
            );

            // Then
            assertTrue(response.getStatusCode().is2xxSuccessful());

            RecordedRequest request = mockServer.takeRequest();
            assertEquals("POST", request.getMethod());
            assertTrue(request.getBody().readUtf8().contains("action"));
        }

        @Test
        @DisplayName("Should return BAD_GATEWAY on proxy failure")
        void shouldReturnBadGatewayOnProxyFailure() {
            // Given
            mockServer.enqueue(new MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST));

            // When
            ResponseEntity<String> response = client.proxyPluginRequest(
                "test.plugin", "/endpoint", HttpMethod.GET, null, new HttpHeaders()
            );

            // Then
            assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
            assertTrue(response.getBody().contains("error"));
        }
    }

    @Nested
    @DisplayName("Plugin Synchronization Tests")
    class PluginSynchronizationTests {

        @Test
        @DisplayName("Should detect plugins to install")
        void shouldDetectPluginsToInstall() {
            // Given
            String responseJson = """
                {
                    "data": [
                        {
                            "key": "remote.plugin",
                            "name": "Remote Plugin",
                            "version": "1.0.0",
                            "state": "ACTIVE"
                        }
                    ]
                }
                """;
            mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            Map<String, PluginState> localPlugins = new HashMap<>();

            // When
            List<PluginSyncAction> actions = client.synchronizePlugins(localPlugins);

            // Then
            assertEquals(1, actions.size());
            assertEquals(PluginSyncAction.Action.INSTALL, actions.get(0).getAction());
            assertEquals("remote.plugin", actions.get(0).getPluginKey());
        }

        @Test
        @DisplayName("Should detect plugins to uninstall")
        void shouldDetectPluginsToUninstall() {
            // Given
            String responseJson = """
                {
                    "data": []
                }
                """;
            mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            Map<String, PluginState> localPlugins = new HashMap<>();
            localPlugins.put("local.plugin", PluginState.ACTIVE);

            // When
            List<PluginSyncAction> actions = client.synchronizePlugins(localPlugins);

            // Then
            assertEquals(1, actions.size());
            assertEquals(PluginSyncAction.Action.UNINSTALL, actions.get(0).getAction());
            assertEquals("local.plugin", actions.get(0).getPluginKey());
        }

        @Test
        @DisplayName("Should detect state mismatches to enable")
        void shouldDetectStateMismatchesToEnable() {
            // Given
            String responseJson = """
                {
                    "data": [
                        {
                            "key": "test.plugin",
                            "name": "Test Plugin",
                            "version": "1.0.0",
                            "state": "ACTIVE"
                        }
                    ]
                }
                """;
            mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            Map<String, PluginState> localPlugins = new HashMap<>();
            localPlugins.put("test.plugin", PluginState.INSTALLED);

            // When
            List<PluginSyncAction> actions = client.synchronizePlugins(localPlugins);

            // Then
            assertEquals(1, actions.size());
            assertEquals(PluginSyncAction.Action.ENABLE, actions.get(0).getAction());
        }

        @Test
        @DisplayName("Should detect state mismatches to disable")
        void shouldDetectStateMismatchesToDisable() {
            // Given
            String responseJson = """
                {
                    "data": [
                        {
                            "key": "test.plugin",
                            "name": "Test Plugin",
                            "version": "1.0.0",
                            "state": "RESOLVED"
                        }
                    ]
                }
                """;
            mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            Map<String, PluginState> localPlugins = new HashMap<>();
            localPlugins.put("test.plugin", PluginState.ACTIVE);

            // When
            List<PluginSyncAction> actions = client.synchronizePlugins(localPlugins);

            // Then
            assertEquals(1, actions.size());
            assertEquals(PluginSyncAction.Action.DISABLE, actions.get(0).getAction());
        }

        @Test
        @DisplayName("Should return empty list when in sync")
        void shouldReturnEmptyListWhenInSync() {
            // Given
            String responseJson = """
                {
                    "data": [
                        {
                            "key": "test.plugin",
                            "name": "Test Plugin",
                            "version": "1.0.0",
                            "state": "ACTIVE"
                        }
                    ]
                }
                """;
            mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            Map<String, PluginState> localPlugins = new HashMap<>();
            localPlugins.put("test.plugin", PluginState.ACTIVE);

            // When
            List<PluginSyncAction> actions = client.synchronizePlugins(localPlugins);

            // Then
            assertTrue(actions.isEmpty());
        }
    }

    @Nested
    @DisplayName("Retry Logic Tests")
    class RetryLogicTests {

        @Test
        @DisplayName("Should retry on failure and succeed")
        void shouldRetryOnFailureAndSucceed() throws InterruptedException {
            // Given
            mockServer.enqueue(new MockResponse().setResponseCode(500));
            mockServer.enqueue(new MockResponse()
                .setBody("{\"data\": []}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            List<PluginInfo> plugins = client.listPlugins();

            // Then - should succeed on retry
            assertEquals(0, plugins.size());
            assertEquals(2, mockServer.getRequestCount());
        }
    }

    @Nested
    @DisplayName("PluginInfo Tests")
    class PluginInfoTests {

        @Test
        @DisplayName("Should set and get all fields")
        void shouldSetAndGetAllFields() {
            // Given
            PluginInfo info = new PluginInfo();

            // When
            info.setKey("test.plugin");
            info.setName("Test Plugin");
            info.setVersion("1.0.0");
            info.setState("ACTIVE");
            info.setMetadata(Map.of("author", "Test"));

            // Then
            assertEquals("test.plugin", info.getKey());
            assertEquals("Test Plugin", info.getName());
            assertEquals("1.0.0", info.getVersion());
            assertEquals("ACTIVE", info.getState());
            assertEquals("Test", info.getMetadata().get("author"));
        }
    }

    @Nested
    @DisplayName("PluginSyncAction Tests")
    class PluginSyncActionTests {

        @Test
        @DisplayName("Should create sync action with all fields")
        void shouldCreateSyncActionWithAllFields() {
            // Given
            PluginInfo info = new PluginInfo();
            info.setKey("test.plugin");

            // When
            PluginSyncAction action = new PluginSyncAction(
                PluginSyncAction.Action.INSTALL, "test.plugin", info
            );

            // Then
            assertEquals(PluginSyncAction.Action.INSTALL, action.getAction());
            assertEquals("test.plugin", action.getPluginKey());
            assertSame(info, action.getPluginInfo());
        }
    }

    @Nested
    @DisplayName("URL Handling Tests")
    class UrlHandlingTests {

        @Test
        @DisplayName("Should handle trailing slash in URL")
        void shouldHandleTrailingSlashInUrl() throws IOException, InterruptedException {
            // Given - create client with trailing slash
            PluginHttpClient clientWithSlash = new PluginHttpClient(
                new RestTemplate(),
                serverUrl + "/",
                1,
                Duration.ofMillis(50)
            );

            mockServer.enqueue(new MockResponse()
                .setBody("{\"data\": []}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            clientWithSlash.listPlugins();

            // Then - path should not have double slashes
            RecordedRequest request = mockServer.takeRequest();
            assertEquals("/api/v1/plugins", request.getPath());
        }
    }
}
