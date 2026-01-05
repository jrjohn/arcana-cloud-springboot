package com.arcana.cloud.plugin.runtime.distributed;

import com.arcana.cloud.plugin.lifecycle.PluginState;
import com.arcana.cloud.plugin.runtime.distributed.HttpPluginRegistryClient.PluginRegistryEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HttpPluginRegistryClient.
 */
class HttpPluginRegistryClientTest {

    private MockWebServer mockServer;
    private HttpPluginRegistryClient client;
    private RestTemplate restTemplate;
    private static final String INSTANCE_ID = "test-instance-1";

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        restTemplate = new RestTemplate();

        String serverUrl = mockServer.url("/").toString();
        // Remove trailing slash
        serverUrl = serverUrl.substring(0, serverUrl.length() - 1);

        client = new HttpPluginRegistryClient(
            restTemplate,
            List.of(serverUrl),
            INSTANCE_ID
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Nested
    @DisplayName("Plugin Registration Tests")
    class PluginRegistrationTests {

        @Test
        @DisplayName("Should register plugin locally")
        void shouldRegisterPluginLocally() {
            // When
            client.registerPlugin("test.plugin", "Test Plugin", "1.0.0", PluginState.INSTALLED);

            // Then
            List<PluginRegistryEntry> entries = client.getLocalEntries();
            assertEquals(1, entries.size());
            assertEquals("test.plugin", entries.get(0).getPluginKey());
            assertEquals("Test Plugin", entries.get(0).getName());
            assertEquals("1.0.0", entries.get(0).getVersion());
            assertEquals(PluginState.INSTALLED, entries.get(0).getState());
        }

        @Test
        @DisplayName("Should broadcast registration to peers")
        void shouldBroadcastRegistrationToPeers() throws InterruptedException {
            // Given
            mockServer.enqueue(new MockResponse().setResponseCode(200));

            // When
            client.registerPlugin("test.plugin", "Test Plugin", "1.0.0", PluginState.INSTALLED);

            // Then
            RecordedRequest request = mockServer.takeRequest();
            assertEquals("POST", request.getMethod());
            assertTrue(request.getPath().contains("/api/v1/plugins/registry/events"));

            String body = request.getBody().readUtf8();
            assertTrue(body.contains("\"eventType\":\"INSTALLED\""));
            assertTrue(body.contains("\"pluginKey\":\"test.plugin\""));
        }
    }

    @Nested
    @DisplayName("Plugin State Update Tests")
    class PluginStateUpdateTests {

        @Test
        @DisplayName("Should update plugin state locally")
        void shouldUpdatePluginStateLocally() {
            // Given
            client.registerPlugin("test.plugin", "Test Plugin", "1.0.0", PluginState.INSTALLED);
            mockServer.enqueue(new MockResponse().setResponseCode(200)); // For registration broadcast
            mockServer.enqueue(new MockResponse().setResponseCode(200)); // For state update broadcast

            // When
            client.updatePluginState("test.plugin", PluginState.ACTIVE);

            // Then
            List<PluginRegistryEntry> entries = client.getLocalEntries();
            assertEquals(PluginState.ACTIVE, entries.get(0).getState());
        }

        @Test
        @DisplayName("Should broadcast ENABLED event when activating")
        void shouldBroadcastEnabledEventWhenActivating() throws InterruptedException {
            // Given
            mockServer.enqueue(new MockResponse().setResponseCode(200)); // For registration
            client.registerPlugin("test.plugin", "Test Plugin", "1.0.0", PluginState.RESOLVED);
            mockServer.takeRequest(); // Consume registration request

            mockServer.enqueue(new MockResponse().setResponseCode(200)); // For state update

            // When
            client.updatePluginState("test.plugin", PluginState.ACTIVE);

            // Then
            RecordedRequest request = mockServer.takeRequest();
            String body = request.getBody().readUtf8();
            assertTrue(body.contains("\"eventType\":\"ENABLED\""));
        }

        @Test
        @DisplayName("Should broadcast DISABLED event when deactivating")
        void shouldBroadcastDisabledEventWhenDeactivating() throws InterruptedException {
            // Given
            mockServer.enqueue(new MockResponse().setResponseCode(200)); // For registration
            client.registerPlugin("test.plugin", "Test Plugin", "1.0.0", PluginState.ACTIVE);
            mockServer.takeRequest(); // Consume registration request

            mockServer.enqueue(new MockResponse().setResponseCode(200)); // For state update

            // When
            client.updatePluginState("test.plugin", PluginState.RESOLVED);

            // Then
            RecordedRequest request = mockServer.takeRequest();
            String body = request.getBody().readUtf8();
            assertTrue(body.contains("\"eventType\":\"DISABLED\""));
        }

        @Test
        @DisplayName("Should not update non-existent plugin")
        void shouldNotUpdateNonExistentPlugin() {
            // When
            client.updatePluginState("non.existent", PluginState.ACTIVE);

            // Then - no requests should be made
            assertEquals(0, mockServer.getRequestCount());
        }
    }

    @Nested
    @DisplayName("Plugin Unregistration Tests")
    class PluginUnregistrationTests {

        @Test
        @DisplayName("Should unregister plugin locally")
        void shouldUnregisterPluginLocally() {
            // Given
            mockServer.enqueue(new MockResponse().setResponseCode(200)); // For registration
            client.registerPlugin("test.plugin", "Test Plugin", "1.0.0", PluginState.INSTALLED);

            mockServer.enqueue(new MockResponse().setResponseCode(200)); // For unregistration

            // When
            client.unregisterPlugin("test.plugin");

            // Then
            List<PluginRegistryEntry> entries = client.getLocalEntries();
            assertTrue(entries.isEmpty());
        }

        @Test
        @DisplayName("Should broadcast UNINSTALLED event")
        void shouldBroadcastUninstalledEvent() throws InterruptedException {
            // Given
            mockServer.enqueue(new MockResponse().setResponseCode(200)); // For registration
            client.registerPlugin("test.plugin", "Test Plugin", "1.0.0", PluginState.INSTALLED);
            mockServer.takeRequest(); // Consume registration request

            mockServer.enqueue(new MockResponse().setResponseCode(200)); // For unregistration

            // When
            client.unregisterPlugin("test.plugin");

            // Then
            RecordedRequest request = mockServer.takeRequest();
            String body = request.getBody().readUtf8();
            assertTrue(body.contains("\"eventType\":\"UNINSTALLED\""));
        }
    }

    @Nested
    @DisplayName("Peer Synchronization Tests")
    class PeerSynchronizationTests {

        @Test
        @DisplayName("Should synchronize with peers")
        void shouldSynchronizeWithPeers() {
            // Given
            String responseJson = """
                {
                    "data": [
                        {
                            "pluginKey": "remote.plugin",
                            "name": "Remote Plugin",
                            "version": "2.0.0",
                            "state": "ACTIVE",
                            "lastModifiedBy": "other-instance",
                            "lastModified": "2024-01-01T00:00:00Z"
                        }
                    ]
                }
                """;
            mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            Map<String, PluginRegistryEntry> result = client.synchronizeWithPeers();

            // Then
            assertEquals(1, result.size());
            assertTrue(result.containsKey("remote.plugin"));
            assertEquals("Remote Plugin", result.get("remote.plugin").getName());
            assertEquals(PluginState.ACTIVE, result.get("remote.plugin").getState());
        }

        @Test
        @DisplayName("Should merge local and remote plugins")
        void shouldMergeLocalAndRemotePlugins() {
            // Given - register local plugin first
            mockServer.enqueue(new MockResponse().setResponseCode(200)); // For registration broadcast
            client.registerPlugin("local.plugin", "Local Plugin", "1.0.0", PluginState.INSTALLED);

            String responseJson = """
                {
                    "data": [
                        {
                            "pluginKey": "remote.plugin",
                            "name": "Remote Plugin",
                            "version": "1.0.0",
                            "state": "ACTIVE",
                            "lastModifiedBy": "other-instance",
                            "lastModified": "2024-01-01T00:00:00Z"
                        }
                    ]
                }
                """;
            mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            Map<String, PluginRegistryEntry> result = client.synchronizeWithPeers();

            // Then
            assertEquals(2, result.size());
            assertTrue(result.containsKey("local.plugin"));
            assertTrue(result.containsKey("remote.plugin"));
        }

        @Test
        @DisplayName("Should keep more recent entry on conflict")
        void shouldKeepMoreRecentEntryOnConflict() {
            // Given - register local plugin with recent timestamp
            mockServer.enqueue(new MockResponse().setResponseCode(200)); // For registration broadcast
            client.registerPlugin("conflict.plugin", "Local Version", "1.0.0", PluginState.INSTALLED);

            // Remote has older timestamp
            String responseJson = """
                {
                    "data": [
                        {
                            "pluginKey": "conflict.plugin",
                            "name": "Remote Version",
                            "version": "1.0.0",
                            "state": "ACTIVE",
                            "lastModifiedBy": "other-instance",
                            "lastModified": "2020-01-01T00:00:00Z"
                        }
                    ]
                }
                """;
            mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            Map<String, PluginRegistryEntry> result = client.synchronizeWithPeers();

            // Then - local version should be kept (more recent)
            assertEquals("Local Version", result.get("conflict.plugin").getName());
        }

        @Test
        @DisplayName("Should handle peer failure gracefully")
        void shouldHandlePeerFailureGracefully() {
            // Given
            mockServer.enqueue(new MockResponse().setResponseCode(500));

            // When
            Map<String, PluginRegistryEntry> result = client.synchronizeWithPeers();

            // Then - should return empty (only local cache which is empty)
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Event Reception Tests")
    class EventReceptionTests {

        @Test
        @DisplayName("Should ignore events from same instance")
        void shouldIgnoreEventsFromSameInstance() {
            // Given
            PluginRegistryEntry entry = new PluginRegistryEntry(
                "test.plugin", "Test Plugin", "1.0.0",
                PluginState.INSTALLED, INSTANCE_ID, Instant.now()
            );

            // When
            client.receivePluginEvent("INSTALLED", entry);

            // Then - no plugins should be added
            assertTrue(client.getLocalEntries().isEmpty());
        }

        @Test
        @DisplayName("Should process INSTALLED event from other instance")
        void shouldProcessInstalledEventFromOtherInstance() {
            // Given
            PluginRegistryEntry entry = new PluginRegistryEntry(
                "test.plugin", "Test Plugin", "1.0.0",
                PluginState.INSTALLED, "other-instance", Instant.now()
            );

            // When
            client.receivePluginEvent("INSTALLED", entry);

            // Then
            List<PluginRegistryEntry> entries = client.getLocalEntries();
            assertEquals(1, entries.size());
            assertEquals("test.plugin", entries.get(0).getPluginKey());
        }

        @Test
        @DisplayName("Should process ENABLED event from other instance")
        void shouldProcessEnabledEventFromOtherInstance() {
            // Given - first install
            PluginRegistryEntry installEntry = new PluginRegistryEntry(
                "test.plugin", "Test Plugin", "1.0.0",
                PluginState.INSTALLED, "other-instance", Instant.now().minusSeconds(10)
            );
            client.receivePluginEvent("INSTALLED", installEntry);

            // Then enable
            PluginRegistryEntry enableEntry = new PluginRegistryEntry(
                "test.plugin", "Test Plugin", "1.0.0",
                PluginState.ACTIVE, "other-instance", Instant.now()
            );

            // When
            client.receivePluginEvent("ENABLED", enableEntry);

            // Then
            List<PluginRegistryEntry> entries = client.getLocalEntries();
            assertEquals(PluginState.ACTIVE, entries.get(0).getState());
        }

        @Test
        @DisplayName("Should process UNINSTALLED event from other instance")
        void shouldProcessUninstalledEventFromOtherInstance() {
            // Given - first install
            PluginRegistryEntry installEntry = new PluginRegistryEntry(
                "test.plugin", "Test Plugin", "1.0.0",
                PluginState.INSTALLED, "other-instance", Instant.now()
            );
            client.receivePluginEvent("INSTALLED", installEntry);

            PluginRegistryEntry uninstallEntry = new PluginRegistryEntry(
                "test.plugin", "Test Plugin", "1.0.0",
                PluginState.UNINSTALLED, "other-instance", Instant.now()
            );

            // When
            client.receivePluginEvent("UNINSTALLED", uninstallEntry);

            // Then
            assertTrue(client.getLocalEntries().isEmpty());
        }
    }

    @Nested
    @DisplayName("PluginRegistryEntry Tests")
    class PluginRegistryEntryTests {

        @Test
        @DisplayName("Should create entry with constructor")
        void shouldCreateEntryWithConstructor() {
            // Given
            Instant now = Instant.now();

            // When
            PluginRegistryEntry entry = new PluginRegistryEntry(
                "test.plugin", "Test Plugin", "1.0.0",
                PluginState.ACTIVE, INSTANCE_ID, now
            );

            // Then
            assertEquals("test.plugin", entry.getPluginKey());
            assertEquals("Test Plugin", entry.getName());
            assertEquals("1.0.0", entry.getVersion());
            assertEquals(PluginState.ACTIVE, entry.getState());
            assertEquals(INSTANCE_ID, entry.getLastModifiedBy());
            assertEquals(now, entry.getLastModified());
        }

        @Test
        @DisplayName("Should convert to map")
        void shouldConvertToMap() {
            // Given
            Instant now = Instant.now();
            PluginRegistryEntry entry = new PluginRegistryEntry(
                "test.plugin", "Test Plugin", "1.0.0",
                PluginState.ACTIVE, INSTANCE_ID, now
            );

            // When
            Map<String, Object> map = entry.toMap();

            // Then
            assertEquals("test.plugin", map.get("pluginKey"));
            assertEquals("Test Plugin", map.get("name"));
            assertEquals("1.0.0", map.get("version"));
            assertEquals("ACTIVE", map.get("state"));
            assertEquals(INSTANCE_ID, map.get("lastModifiedBy"));
            assertEquals(now.toString(), map.get("lastModified"));
        }

        @Test
        @DisplayName("Should handle null state in toMap")
        void shouldHandleNullStateInToMap() {
            // Given
            PluginRegistryEntry entry = new PluginRegistryEntry();
            entry.setPluginKey("test.plugin");

            // When
            Map<String, Object> map = entry.toMap();

            // Then
            assertEquals("INSTALLED", map.get("state"));
        }
    }
}
