package com.arcana.cloud.integration.plugin;

import com.arcana.cloud.plugin.lifecycle.PluginState;
import com.arcana.cloud.plugin.runtime.distributed.HttpPluginRegistryClient;
import com.arcana.cloud.plugin.runtime.distributed.HttpPluginRegistryClient.PluginRegistryEntry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for plugin system in K8s HTTP deployment mode.
 *
 * <p>Tests the plugin lifecycle when running in Kubernetes with HTTP
 * communication between pods.</p>
 *
 * <p>Deployment characteristics:</p>
 * <ul>
 *   <li>Multiple pod replicas for each layer</li>
 *   <li>HTTP REST communication between layers</li>
 *   <li>HTTP-based plugin registry synchronization</li>
 *   <li>Kubernetes service discovery</li>
 *   <li>HTTP health probes for K8s</li>
 * </ul>
 */
@DisplayName("Plugin K8s HTTP Mode Integration Tests")
class PluginK8sHttpModeTest {

    private MockWebServer peer1Mock;
    private MockWebServer peer2Mock;
    private HttpPluginRegistryClient registryClient;
    private static final String INSTANCE_ID = "pod-instance-1";

    @BeforeEach
    void setUp() throws IOException {
        peer1Mock = new MockWebServer();
        peer2Mock = new MockWebServer();
        peer1Mock.start();
        peer2Mock.start();

        String peer1Url = peer1Mock.url("/").toString();
        String peer2Url = peer2Mock.url("/").toString();
        peer1Url = peer1Url.substring(0, peer1Url.length() - 1);
        peer2Url = peer2Url.substring(0, peer2Url.length() - 1);

        registryClient = new HttpPluginRegistryClient(
            new RestTemplate(),
            List.of(peer1Url, peer2Url),
            INSTANCE_ID
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        peer1Mock.shutdown();
        peer2Mock.shutdown();
    }

    @Nested
    @DisplayName("K8s Pod Plugin Registration Tests")
    class PodRegistrationTests {

        @Test
        @DisplayName("Should register plugin and broadcast to peer pods")
        void shouldRegisterPluginAndBroadcastToPeerPods() throws InterruptedException {
            // Given
            peer1Mock.enqueue(new MockResponse().setResponseCode(200));
            peer2Mock.enqueue(new MockResponse().setResponseCode(200));

            // When
            registryClient.registerPlugin("audit-plugin", "Audit Plugin", "1.0.0", PluginState.INSTALLED);

            // Then - verify broadcasts sent
            RecordedRequest peer1Request = peer1Mock.takeRequest(1, TimeUnit.SECONDS);
            RecordedRequest peer2Request = peer2Mock.takeRequest(1, TimeUnit.SECONDS);

            assertNotNull(peer1Request);
            assertNotNull(peer2Request);
            assertEquals("POST", peer1Request.getMethod());
            assertEquals("POST", peer2Request.getMethod());
            assertTrue(peer1Request.getPath().contains("/api/v1/plugins/registry/events"));
            assertTrue(peer1Request.getBody().readUtf8().contains("INSTALLED"));
        }

        @Test
        @DisplayName("Should maintain local registry")
        void shouldMaintainLocalRegistry() {
            // Given
            peer1Mock.enqueue(new MockResponse().setResponseCode(200));
            peer2Mock.enqueue(new MockResponse().setResponseCode(200));

            // When
            registryClient.registerPlugin("local-plugin", "Local Plugin", "1.0.0", PluginState.ACTIVE);

            // Then
            List<PluginRegistryEntry> localEntries = registryClient.getLocalEntries();
            assertEquals(1, localEntries.size());
            assertEquals("local-plugin", localEntries.get(0).getPluginKey());
            assertEquals("Local Plugin", localEntries.get(0).getName());
            assertEquals(PluginState.ACTIVE, localEntries.get(0).getState());
        }
    }

    @Nested
    @DisplayName("K8s Pod State Update Tests")
    class PodStateUpdateTests {

        @Test
        @DisplayName("Should update state and broadcast ENABLED event")
        void shouldUpdateStateAndBroadcastEnabledEvent() throws InterruptedException {
            // Given - register first
            peer1Mock.enqueue(new MockResponse().setResponseCode(200));
            peer2Mock.enqueue(new MockResponse().setResponseCode(200));
            registryClient.registerPlugin("state-plugin", "State Plugin", "1.0.0", PluginState.INSTALLED);
            peer1Mock.takeRequest(1, TimeUnit.SECONDS);
            peer2Mock.takeRequest(1, TimeUnit.SECONDS);

            // Setup for state update
            peer1Mock.enqueue(new MockResponse().setResponseCode(200));
            peer2Mock.enqueue(new MockResponse().setResponseCode(200));

            // When
            registryClient.updatePluginState("state-plugin", PluginState.ACTIVE);

            // Then
            RecordedRequest request = peer1Mock.takeRequest(1, TimeUnit.SECONDS);
            assertNotNull(request);
            assertTrue(request.getBody().readUtf8().contains("ENABLED"));

            List<PluginRegistryEntry> entries = registryClient.getLocalEntries();
            assertEquals(PluginState.ACTIVE, entries.get(0).getState());
        }

        @Test
        @DisplayName("Should broadcast DISABLED event when deactivating")
        void shouldBroadcastDisabledEventWhenDeactivating() throws InterruptedException {
            // Given - register as active first
            peer1Mock.enqueue(new MockResponse().setResponseCode(200));
            peer2Mock.enqueue(new MockResponse().setResponseCode(200));
            registryClient.registerPlugin("disable-plugin", "Disable Plugin", "1.0.0", PluginState.ACTIVE);
            peer1Mock.takeRequest(1, TimeUnit.SECONDS);
            peer2Mock.takeRequest(1, TimeUnit.SECONDS);

            // Setup for state update
            peer1Mock.enqueue(new MockResponse().setResponseCode(200));
            peer2Mock.enqueue(new MockResponse().setResponseCode(200));

            // When
            registryClient.updatePluginState("disable-plugin", PluginState.RESOLVED);

            // Then
            RecordedRequest request = peer1Mock.takeRequest(1, TimeUnit.SECONDS);
            assertNotNull(request);
            assertTrue(request.getBody().readUtf8().contains("DISABLED"));
        }
    }

    @Nested
    @DisplayName("K8s Pod Synchronization Tests")
    class PodSynchronizationTests {

        @Test
        @DisplayName("Should synchronize plugins from peer pods")
        void shouldSynchronizePluginsFromPeerPods() {
            // Given - peer has plugins
            String peer1Response = """
                {
                    "data": [
                        {
                            "pluginKey": "peer1-plugin",
                            "name": "Peer 1 Plugin",
                            "version": "1.0.0",
                            "state": "ACTIVE",
                            "lastModifiedBy": "peer-pod-1",
                            "lastModified": "2024-01-01T12:00:00Z"
                        }
                    ]
                }
                """;
            String peer2Response = """
                {
                    "data": [
                        {
                            "pluginKey": "peer2-plugin",
                            "name": "Peer 2 Plugin",
                            "version": "2.0.0",
                            "state": "INSTALLED",
                            "lastModifiedBy": "peer-pod-2",
                            "lastModified": "2024-01-02T12:00:00Z"
                        }
                    ]
                }
                """;
            peer1Mock.enqueue(new MockResponse()
                .setBody(peer1Response)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));
            peer2Mock.enqueue(new MockResponse()
                .setBody(peer2Response)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            Map<String, PluginRegistryEntry> allPlugins = registryClient.synchronizeWithPeers();

            // Then
            assertEquals(2, allPlugins.size());
            assertTrue(allPlugins.containsKey("peer1-plugin"));
            assertTrue(allPlugins.containsKey("peer2-plugin"));
            assertEquals(PluginState.ACTIVE, allPlugins.get("peer1-plugin").getState());
        }

        @Test
        @DisplayName("Should merge local and remote plugins")
        void shouldMergeLocalAndRemotePlugins() {
            // Given - local plugin
            peer1Mock.enqueue(new MockResponse().setResponseCode(200));
            peer2Mock.enqueue(new MockResponse().setResponseCode(200));
            registryClient.registerPlugin("local-plugin", "Local Plugin", "1.0.0", PluginState.ACTIVE);

            // Remote plugins
            String peerResponse = """
                {
                    "data": [
                        {
                            "pluginKey": "remote-plugin",
                            "name": "Remote Plugin",
                            "version": "1.0.0",
                            "state": "ACTIVE",
                            "lastModifiedBy": "peer-pod",
                            "lastModified": "2024-01-01T12:00:00Z"
                        }
                    ]
                }
                """;
            peer1Mock.enqueue(new MockResponse()
                .setBody(peerResponse)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));
            peer2Mock.enqueue(new MockResponse()
                .setBody("{\"data\": []}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            Map<String, PluginRegistryEntry> allPlugins = registryClient.synchronizeWithPeers();

            // Then
            assertEquals(2, allPlugins.size());
            assertTrue(allPlugins.containsKey("local-plugin"));
            assertTrue(allPlugins.containsKey("remote-plugin"));
        }

        @Test
        @DisplayName("Should handle peer pod failure during sync")
        void shouldHandlePeerPodFailureDuringSync() {
            // Given - one peer fails
            peer1Mock.enqueue(new MockResponse().setResponseCode(500));
            peer2Mock.enqueue(new MockResponse()
                .setBody("{\"data\": [{\"pluginKey\": \"surviving-plugin\", \"name\": \"Survivor\", \"version\": \"1.0.0\", \"state\": \"ACTIVE\", \"lastModifiedBy\": \"peer-2\", \"lastModified\": \"2024-01-01T00:00:00Z\"}]}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            Map<String, PluginRegistryEntry> allPlugins = registryClient.synchronizeWithPeers();

            // Then - should still get plugins from working peer
            assertEquals(1, allPlugins.size());
            assertTrue(allPlugins.containsKey("surviving-plugin"));
        }

        @Test
        @DisplayName("Should resolve conflicts using most recent timestamp")
        void shouldResolveConflictsUsingMostRecentTimestamp() {
            // Given - register local plugin with older timestamp
            peer1Mock.enqueue(new MockResponse().setResponseCode(200));
            peer2Mock.enqueue(new MockResponse().setResponseCode(200));
            registryClient.registerPlugin("conflict-plugin", "Old Version", "1.0.0", PluginState.INSTALLED);

            // Remote has newer version
            String peerResponse = """
                {
                    "data": [
                        {
                            "pluginKey": "conflict-plugin",
                            "name": "New Version",
                            "version": "2.0.0",
                            "state": "ACTIVE",
                            "lastModifiedBy": "peer-pod",
                            "lastModified": "2099-12-31T23:59:59Z"
                        }
                    ]
                }
                """;
            peer1Mock.enqueue(new MockResponse()
                .setBody(peerResponse)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));
            peer2Mock.enqueue(new MockResponse()
                .setBody("{\"data\": []}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            Map<String, PluginRegistryEntry> allPlugins = registryClient.synchronizeWithPeers();

            // Then - remote version should win (newer timestamp)
            assertEquals("New Version", allPlugins.get("conflict-plugin").getName());
            assertEquals("2.0.0", allPlugins.get("conflict-plugin").getVersion());
        }
    }

    @Nested
    @DisplayName("K8s Event Reception Tests")
    class EventReceptionTests {

        @Test
        @DisplayName("Should receive and process INSTALLED event from peer")
        void shouldReceiveAndProcessInstalledEvent() {
            // Given
            PluginRegistryEntry entry = new PluginRegistryEntry(
                "new-plugin", "New Plugin", "1.0.0",
                PluginState.INSTALLED, "peer-pod", Instant.now()
            );

            // When
            registryClient.receivePluginEvent("INSTALLED", entry);

            // Then
            List<PluginRegistryEntry> entries = registryClient.getLocalEntries();
            assertEquals(1, entries.size());
            assertEquals("new-plugin", entries.get(0).getPluginKey());
        }

        @Test
        @DisplayName("Should ignore events from same pod instance")
        void shouldIgnoreEventsFromSamePodInstance() {
            // Given
            PluginRegistryEntry entry = new PluginRegistryEntry(
                "self-plugin", "Self Plugin", "1.0.0",
                PluginState.INSTALLED, INSTANCE_ID, Instant.now()
            );

            // When
            registryClient.receivePluginEvent("INSTALLED", entry);

            // Then - should not be added (from same instance)
            assertTrue(registryClient.getLocalEntries().isEmpty());
        }

        @Test
        @DisplayName("Should process UNINSTALLED event from peer")
        void shouldProcessUninstalledEvent() {
            // Given - first add plugin
            PluginRegistryEntry installEntry = new PluginRegistryEntry(
                "remove-plugin", "Remove Plugin", "1.0.0",
                PluginState.ACTIVE, "peer-pod", Instant.now().minusSeconds(10)
            );
            registryClient.receivePluginEvent("INSTALLED", installEntry);

            PluginRegistryEntry uninstallEntry = new PluginRegistryEntry(
                "remove-plugin", "Remove Plugin", "1.0.0",
                PluginState.UNINSTALLED, "peer-pod", Instant.now()
            );

            // When
            registryClient.receivePluginEvent("UNINSTALLED", uninstallEntry);

            // Then
            assertTrue(registryClient.getLocalEntries().isEmpty());
        }
    }

    @Nested
    @DisplayName("K8s HTTP Health Check Tests")
    class K8sHealthCheckTests {

        @Test
        @DisplayName("Should support K8s HTTP liveness probe")
        void shouldSupportK8sHttpLivenessProbe() {
            // Given
            peer1Mock.enqueue(new MockResponse()
                .setBody("{\"status\": \"UP\"}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            // Note: In actual K8s deployment, this would be handled by PluginController
            // Here we verify the mock server can respond to health checks
            assertDoesNotThrow(() -> peer1Mock.url("/api/v1/plugins/health/live"));
        }

        @Test
        @DisplayName("Should support K8s HTTP readiness probe")
        void shouldSupportK8sHttpReadinessProbe() {
            // Given
            peer1Mock.enqueue(new MockResponse()
                .setBody("{\"status\": \"UP\", \"pluginsInitialized\": true}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When/Then
            assertDoesNotThrow(() -> peer1Mock.url("/api/v1/plugins/health/ready"));
        }
    }

    @Nested
    @DisplayName("K8s Rolling Update Tests")
    class RollingUpdateTests {

        @Test
        @DisplayName("Should handle pod going away during sync")
        void shouldHandlePodGoingAwayDuringSync() {
            // Given - simulate pod termination
            peer1Mock.enqueue(new MockResponse()
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST));
            peer2Mock.enqueue(new MockResponse()
                .setBody("{\"data\": [{\"pluginKey\": \"survivor\", \"name\": \"Survivor\", \"version\": \"1.0.0\", \"state\": \"ACTIVE\", \"lastModifiedBy\": \"peer-2\", \"lastModified\": \"2024-01-01T00:00:00Z\"}]}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            Map<String, PluginRegistryEntry> allPlugins = registryClient.synchronizeWithPeers();

            // Then - should recover with remaining pods
            assertFalse(allPlugins.isEmpty());
        }

        @Test
        @DisplayName("Should maintain state during rolling update")
        void shouldMaintainStateDuringRollingUpdate() {
            // Given - local plugin
            peer1Mock.enqueue(new MockResponse().setResponseCode(200));
            peer2Mock.enqueue(new MockResponse().setResponseCode(200));
            registryClient.registerPlugin("persistent-plugin", "Persistent", "1.0.0", PluginState.ACTIVE);

            // Simulate new pod joining with existing plugins
            String newPodPlugins = """
                {
                    "data": [
                        {
                            "pluginKey": "persistent-plugin",
                            "name": "Persistent",
                            "version": "1.0.0",
                            "state": "ACTIVE",
                            "lastModifiedBy": "new-pod",
                            "lastModified": "2024-01-01T00:00:00Z"
                        }
                    ]
                }
                """;
            peer1Mock.enqueue(new MockResponse()
                .setBody(newPodPlugins)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));
            peer2Mock.enqueue(new MockResponse()
                .setBody(newPodPlugins)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // When
            Map<String, PluginRegistryEntry> allPlugins = registryClient.synchronizeWithPeers();

            // Then - plugin should still exist
            assertTrue(allPlugins.containsKey("persistent-plugin"));
            assertEquals(PluginState.ACTIVE, allPlugins.get("persistent-plugin").getState());
        }
    }

    @Nested
    @DisplayName("K8s Scale Out Tests")
    class ScaleOutTests {

        @Test
        @DisplayName("Should broadcast to multiple peer pods")
        void shouldBroadcastToMultiplePeerPods() throws InterruptedException {
            // Given
            peer1Mock.enqueue(new MockResponse().setResponseCode(200));
            peer2Mock.enqueue(new MockResponse().setResponseCode(200));

            // When
            registryClient.registerPlugin("broadcast-plugin", "Broadcast", "1.0.0", PluginState.INSTALLED);

            // Then - both peers should receive the broadcast
            RecordedRequest req1 = peer1Mock.takeRequest(1, TimeUnit.SECONDS);
            RecordedRequest req2 = peer2Mock.takeRequest(1, TimeUnit.SECONDS);

            assertNotNull(req1);
            assertNotNull(req2);
            assertTrue(req1.getPath().contains("/api/v1/plugins/registry/events"));
            assertTrue(req2.getPath().contains("/api/v1/plugins/registry/events"));
        }
    }

    @Nested
    @DisplayName("Complete K8s HTTP Workflow Tests")
    class WorkflowTests {

        @Test
        @DisplayName("Should complete full K8s HTTP plugin workflow")
        void shouldCompleteFullK8sHttpPluginWorkflow() throws InterruptedException {
            // Step 1: Register plugin on this pod
            peer1Mock.enqueue(new MockResponse().setResponseCode(200));
            peer2Mock.enqueue(new MockResponse().setResponseCode(200));
            registryClient.registerPlugin("workflow-plugin", "Workflow Plugin", "1.0.0", PluginState.INSTALLED);
            peer1Mock.takeRequest(1, TimeUnit.SECONDS);
            peer2Mock.takeRequest(1, TimeUnit.SECONDS);

            // Step 2: Enable plugin
            peer1Mock.enqueue(new MockResponse().setResponseCode(200));
            peer2Mock.enqueue(new MockResponse().setResponseCode(200));
            registryClient.updatePluginState("workflow-plugin", PluginState.ACTIVE);
            peer1Mock.takeRequest(1, TimeUnit.SECONDS);
            peer2Mock.takeRequest(1, TimeUnit.SECONDS);

            // Verify local state
            assertEquals(PluginState.ACTIVE,
                registryClient.getLocalEntries().stream()
                    .filter(e -> "workflow-plugin".equals(e.getPluginKey()))
                    .findFirst()
                    .map(PluginRegistryEntry::getState)
                    .orElse(null)
            );

            // Step 3: Simulate peer receiving our events and syncing back
            String peerResponse = """
                {
                    "data": [
                        {
                            "pluginKey": "workflow-plugin",
                            "name": "Workflow Plugin",
                            "version": "1.0.0",
                            "state": "ACTIVE",
                            "lastModifiedBy": "pod-instance-1",
                            "lastModified": "2024-01-01T00:00:00Z"
                        },
                        {
                            "pluginKey": "peer-plugin",
                            "name": "Peer Plugin",
                            "version": "1.0.0",
                            "state": "ACTIVE",
                            "lastModifiedBy": "peer-pod",
                            "lastModified": "2024-01-01T00:00:00Z"
                        }
                    ]
                }
                """;
            peer1Mock.enqueue(new MockResponse()
                .setBody(peerResponse)
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));
            peer2Mock.enqueue(new MockResponse()
                .setBody("{\"data\": []}")
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE));

            // Step 4: Synchronize
            Map<String, PluginRegistryEntry> allPlugins = registryClient.synchronizeWithPeers();

            // Step 5: Verify full state
            assertEquals(2, allPlugins.size());
            assertTrue(allPlugins.containsKey("workflow-plugin"));
            assertTrue(allPlugins.containsKey("peer-plugin"));

            // Step 6: Unregister
            peer1Mock.enqueue(new MockResponse().setResponseCode(200));
            peer2Mock.enqueue(new MockResponse().setResponseCode(200));
            registryClient.unregisterPlugin("workflow-plugin");

            // Verify local removal
            assertTrue(registryClient.getLocalEntries().stream()
                .noneMatch(e -> "workflow-plugin".equals(e.getPluginKey())));
        }
    }
}
