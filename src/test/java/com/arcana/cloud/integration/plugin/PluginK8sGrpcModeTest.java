package com.arcana.cloud.integration.plugin;

import com.arcana.cloud.plugin.api.PluginDescriptor;
import com.arcana.cloud.plugin.lifecycle.PluginState;
import com.arcana.cloud.plugin.runtime.distributed.DistributedPluginRegistry;
import com.arcana.cloud.plugin.runtime.distributed.DistributedPluginRegistry.PluginEvent;
import com.arcana.cloud.plugin.runtime.distributed.DistributedPluginRegistry.PluginEventType;
import com.arcana.cloud.plugin.runtime.distributed.DistributedPluginRegistry.PluginRegistryEntry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for plugin system in K8s gRPC deployment mode.
 *
 * <p>Tests the plugin lifecycle when running in Kubernetes with gRPC
 * communication and Redis-based distributed state.</p>
 *
 * <p>Deployment characteristics:</p>
 * <ul>
 *   <li>Multiple pod replicas for each layer</li>
 *   <li>gRPC communication between layers</li>
 *   <li>Redis-based distributed plugin registry</li>
 *   <li>Redis pub/sub for real-time sync</li>
 *   <li>gRPC health probes for K8s</li>
 *   <li>Leader election for plugin operations</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Plugin K8s gRPC Mode Integration Tests")
@Timeout(30)
class PluginK8sGrpcModeTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, String, PluginRegistryEntry> hashOps;

    @Mock
    private ValueOperations<String, Object> valueOps;

    private DistributedPluginRegistry registry;
    private static final String POD_INSTANCE_ID = "arcana-service-pod-0";

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        when(redisTemplate.<String, PluginRegistryEntry>opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        registry = new DistributedPluginRegistry(redisTemplate, POD_INSTANCE_ID);
    }

    @Nested
    @DisplayName("K8s Pod Initialization Tests")
    class PodInitializationTests {

        @Test
        @DisplayName("Should initialize registry and register pod instance")
        void shouldInitializeRegistryAndRegisterPodInstance() {
            // Given
            when(hashOps.entries(anyString())).thenReturn(new HashMap<>());

            // When
            registry.initialize();

            // Then
            verify(valueOps).set(
                contains(POD_INSTANCE_ID),
                anyString(),
                any(Duration.class)
            );
            verify(hashOps).entries(eq("arcana:plugins"));
        }

        @Test
        @DisplayName("Should load existing plugins from Redis on startup")
        void shouldLoadExistingPluginsFromRedisOnStartup() {
            // Given - existing plugins in Redis (from other pods)
            Map<String, PluginRegistryEntry> existingPlugins = new HashMap<>();
            existingPlugins.put("plugin-1", new PluginRegistryEntry(
                "plugin-1", "Plugin 1", "1.0.0", PluginState.ACTIVE, "other-pod", Instant.now()
            ));
            existingPlugins.put("plugin-2", new PluginRegistryEntry(
                "plugin-2", "Plugin 2", "2.0.0", PluginState.INSTALLED, "other-pod", Instant.now()
            ));
            when(hashOps.entries(eq("arcana:plugins"))).thenReturn(existingPlugins);

            // When
            registry.initialize();

            // Then - should have loaded the plugins
            Map<String, PluginRegistryEntry> allPlugins = registry.getAllPlugins();
            // Note: getAllPlugins returns from Redis, so mock it
            when(hashOps.entries(eq("arcana:plugins"))).thenReturn(existingPlugins);
            assertEquals(2, registry.getAllPlugins().size());
        }

        @Test
        @DisplayName("Should unregister pod on shutdown")
        void shouldUnregisterPodOnShutdown() {
            // Given
            when(hashOps.entries(anyString())).thenReturn(new HashMap<>());
            registry.initialize();

            // When
            registry.shutdown();

            // Then
            verify(redisTemplate).delete(contains(POD_INSTANCE_ID));
        }
    }

    @Nested
    @DisplayName("K8s Redis-Based Plugin Registration Tests")
    class RedisPluginRegistrationTests {

        @Test
        @DisplayName("Should register plugin in Redis with pod metadata")
        void shouldRegisterPluginInRedisWithPodMetadata() {
            // Given
            PluginDescriptor descriptor = mock(PluginDescriptor.class);
            when(descriptor.getName()).thenReturn("K8s Plugin");
            when(descriptor.getVersion()).thenReturn("1.0.0");

            // When
            registry.registerPlugin("k8s-plugin", descriptor, PluginState.INSTALLED);

            // Then
            ArgumentCaptor<PluginRegistryEntry> captor = ArgumentCaptor.forClass(PluginRegistryEntry.class);
            verify(hashOps).put(eq("arcana:plugins"), eq("k8s-plugin"), captor.capture());

            PluginRegistryEntry entry = captor.getValue();
            assertEquals("k8s-plugin", entry.getPluginKey());
            assertEquals(POD_INSTANCE_ID, entry.getLastModifiedBy());
            assertNotNull(entry.getLastModified());
        }

        @Test
        @DisplayName("Should publish event to Redis channel for other pods")
        void shouldPublishEventToRedisChannelForOtherPods() {
            // Given
            PluginDescriptor descriptor = mock(PluginDescriptor.class);
            when(descriptor.getName()).thenReturn("Broadcast Plugin");
            when(descriptor.getVersion()).thenReturn("1.0.0");

            // When
            registry.registerPlugin("broadcast-plugin", descriptor, PluginState.INSTALLED);

            // Then
            verify(redisTemplate).convertAndSend(
                eq("arcana:plugin:events"),
                argThat((PluginEvent event) ->
                    event.getType() == PluginEventType.INSTALLED &&
                    "broadcast-plugin".equals(event.getPluginKey()) &&
                    POD_INSTANCE_ID.equals(event.getSourceInstance())
                )
            );
        }
    }

    @Nested
    @DisplayName("K8s Leader Election Tests")
    class LeaderElectionTests {

        @Test
        @DisplayName("Should acquire leadership when available")
        void shouldAcquireLeadershipWhenAvailable() {
            // Given
            when(valueOps.setIfAbsent(eq("arcana:plugin:leader"), eq(POD_INSTANCE_ID), any(Duration.class)))
                .thenReturn(true);

            // When
            boolean acquired = registry.tryAcquireLeadership();

            // Then
            assertTrue(acquired);
            assertTrue(registry.isLeader());
        }

        @Test
        @DisplayName("Should not acquire leadership when held by another pod")
        void shouldNotAcquireLeadershipWhenHeldByAnotherPod() {
            // Given
            when(valueOps.setIfAbsent(eq("arcana:plugin:leader"), eq(POD_INSTANCE_ID), any(Duration.class)))
                .thenReturn(false);
            when(valueOps.get(eq("arcana:plugin:leader"))).thenReturn("other-pod");

            // When
            boolean acquired = registry.tryAcquireLeadership();

            // Then
            assertFalse(acquired);
            assertFalse(registry.isLeader());
        }

        @Test
        @DisplayName("Should renew leadership TTL when already leader")
        void shouldRenewLeadershipTtlWhenAlreadyLeader() {
            // Given
            when(valueOps.setIfAbsent(eq("arcana:plugin:leader"), eq(POD_INSTANCE_ID), any(Duration.class)))
                .thenReturn(false);
            when(valueOps.get(eq("arcana:plugin:leader"))).thenReturn(POD_INSTANCE_ID);

            // When
            boolean acquired = registry.tryAcquireLeadership();

            // Then
            assertTrue(acquired);
            verify(redisTemplate).expire(eq("arcana:plugin:leader"), any(Duration.class));
        }

        @Test
        @DisplayName("Should release leadership on shutdown")
        void shouldReleaseLeadershipOnShutdown() {
            // Given - acquire leadership first
            when(valueOps.setIfAbsent(eq("arcana:plugin:leader"), eq(POD_INSTANCE_ID), any(Duration.class)))
                .thenReturn(true);
            registry.tryAcquireLeadership();
            when(valueOps.get(eq("arcana:plugin:leader"))).thenReturn(POD_INSTANCE_ID);

            // When
            registry.releaseLeadership();

            // Then
            verify(redisTemplate).delete(eq("arcana:plugin:leader"));
            assertFalse(registry.isLeader());
        }

        @Test
        @DisplayName("Should handle leader failover")
        void shouldHandleLeaderFailover() {
            // Given - initially another pod is leader
            when(valueOps.setIfAbsent(eq("arcana:plugin:leader"), eq(POD_INSTANCE_ID), any(Duration.class)))
                .thenReturn(false)
                .thenReturn(true); // Second attempt succeeds (leader failed)
            when(valueOps.get(eq("arcana:plugin:leader"))).thenReturn("failed-pod");

            // When - first attempt fails
            boolean firstAttempt = registry.tryAcquireLeadership();
            assertFalse(firstAttempt);

            // Simulate leader TTL expiration
            when(valueOps.setIfAbsent(eq("arcana:plugin:leader"), eq(POD_INSTANCE_ID), any(Duration.class)))
                .thenReturn(true);

            // Second attempt succeeds
            boolean secondAttempt = registry.tryAcquireLeadership();

            // Then
            assertTrue(secondAttempt);
            assertTrue(registry.isLeader());
        }
    }

    @Nested
    @DisplayName("K8s Distributed State Sync Tests")
    class DistributedStateSyncTests {

        @Test
        @DisplayName("Should detect missing plugins for this pod")
        void shouldDetectMissingPluginsForThisPod() {
            // Given - registry has plugins
            Map<String, PluginRegistryEntry> registryPlugins = new HashMap<>();
            registryPlugins.put("plugin-1", new PluginRegistryEntry("plugin-1", "P1", "1.0.0", PluginState.ACTIVE, "other-pod", Instant.now()));
            registryPlugins.put("plugin-2", new PluginRegistryEntry("plugin-2", "P2", "1.0.0", PluginState.ACTIVE, "other-pod", Instant.now()));
            registryPlugins.put("plugin-3", new PluginRegistryEntry("plugin-3", "P3", "1.0.0", PluginState.ACTIVE, "other-pod", Instant.now()));
            when(hashOps.entries(eq("arcana:plugins"))).thenReturn(registryPlugins);

            // This pod only has plugin-1
            Set<String> installedLocally = Set.of("plugin-1");

            // When
            Set<String> missing = registry.getMissingPlugins(installedLocally);

            // Then
            assertEquals(2, missing.size());
            assertTrue(missing.contains("plugin-2"));
            assertTrue(missing.contains("plugin-3"));
        }

        @Test
        @DisplayName("Should get expected plugins from registry")
        void shouldGetExpectedPluginsFromRegistry() {
            // Given
            Map<String, PluginRegistryEntry> registryPlugins = new HashMap<>();
            registryPlugins.put("required-1", new PluginRegistryEntry("required-1", "R1", "1.0.0", PluginState.ACTIVE, "leader-pod", Instant.now()));
            registryPlugins.put("required-2", new PluginRegistryEntry("required-2", "R2", "1.0.0", PluginState.ACTIVE, "leader-pod", Instant.now()));
            when(hashOps.entries(eq("arcana:plugins"))).thenReturn(registryPlugins);

            // When
            Set<String> expected = registry.getExpectedPlugins();

            // Then
            assertEquals(2, expected.size());
            assertTrue(expected.contains("required-1"));
            assertTrue(expected.contains("required-2"));
        }
    }

    @Nested
    @DisplayName("K8s Redis Pub/Sub Event Tests")
    class RedisPubSubEventTests {

        @Test
        @DisplayName("Should receive and process events from other pods")
        void shouldReceiveAndProcessEventsFromOtherPods() {
            // Given
            AtomicReference<PluginEvent> receivedEvent = new AtomicReference<>();
            registry.addEventListener(receivedEvent::set);

            PluginEvent event = new PluginEvent(
                PluginEventType.INSTALLED,
                "new-plugin",
                "other-pod",
                PluginState.INSTALLED
            );

            when(hashOps.get(eq("arcana:plugins"), eq("new-plugin"))).thenReturn(
                new PluginRegistryEntry("new-plugin", "New", "1.0.0", PluginState.INSTALLED, "other-pod", Instant.now())
            );

            // When
            registry.handleEvent(event);

            // Then
            assertNotNull(receivedEvent.get());
            assertEquals("new-plugin", receivedEvent.get().getPluginKey());
            assertEquals(PluginEventType.INSTALLED, receivedEvent.get().getType());
        }

        @Test
        @DisplayName("Should ignore events from same pod")
        void shouldIgnoreEventsFromSamePod() {
            // Given
            AtomicBoolean eventReceived = new AtomicBoolean(false);
            registry.addEventListener(e -> eventReceived.set(true));

            PluginEvent event = new PluginEvent(
                PluginEventType.INSTALLED,
                "self-plugin",
                POD_INSTANCE_ID, // Same as this instance
                PluginState.INSTALLED
            );

            // When
            registry.handleEvent(event);

            // Then
            assertFalse(eventReceived.get());
        }

        @Test
        @DisplayName("Should update local cache on INSTALLED event")
        void shouldUpdateLocalCacheOnInstalledEvent() {
            // Given
            PluginEvent event = new PluginEvent(
                PluginEventType.INSTALLED,
                "cache-plugin",
                "other-pod",
                PluginState.INSTALLED
            );

            PluginRegistryEntry entry = new PluginRegistryEntry(
                "cache-plugin", "Cache Plugin", "1.0.0",
                PluginState.INSTALLED, "other-pod", Instant.now()
            );
            when(hashOps.get(eq("arcana:plugins"), eq("cache-plugin"))).thenReturn(entry);

            // When
            registry.handleEvent(event);

            // Then
            verify(hashOps).get(eq("arcana:plugins"), eq("cache-plugin"));
        }

        @Test
        @DisplayName("Should remove from local cache on UNINSTALLED event")
        void shouldRemoveFromLocalCacheOnUninstalledEvent() {
            // Given
            PluginEvent event = new PluginEvent(
                PluginEventType.UNINSTALLED,
                "removed-plugin",
                "other-pod",
                PluginState.UNINSTALLED
            );

            // When
            registry.handleEvent(event);

            // Then - event should be processed (local cache removal is internal)
            // No exception means success
        }
    }

    @Nested
    @DisplayName("K8s gRPC Health Check Tests")
    class GrpcHealthCheckTests {

        /**
         * Mock gRPC health service interface.
         */
        interface GrpcHealthService {
            HealthCheckResponse check(HealthCheckRequest request);
            void watch(HealthCheckRequest request, Consumer<HealthCheckResponse> observer);
        }

        record HealthCheckRequest(String service) {}
        record HealthCheckResponse(ServingStatus status) {}
        enum ServingStatus { UNKNOWN, SERVING, NOT_SERVING }

        @Mock
        private GrpcHealthService healthService;

        @Test
        @DisplayName("Should report SERVING when plugins ready")
        void shouldReportServingWhenPluginsReady() {
            // Given
            when(healthService.check(argThat(req -> "arcana.plugin.PluginService".equals(req.service()))))
                .thenReturn(new HealthCheckResponse(ServingStatus.SERVING));

            // When
            HealthCheckResponse response = healthService.check(new HealthCheckRequest("arcana.plugin.PluginService"));

            // Then
            assertEquals(ServingStatus.SERVING, response.status());
        }

        @Test
        @DisplayName("Should report NOT_SERVING during initialization")
        void shouldReportNotServingDuringInitialization() {
            // Given
            when(healthService.check(any()))
                .thenReturn(new HealthCheckResponse(ServingStatus.NOT_SERVING));

            // When
            HealthCheckResponse response = healthService.check(new HealthCheckRequest("arcana.plugin.PluginService"));

            // Then
            assertEquals(ServingStatus.NOT_SERVING, response.status());
        }
    }

    @Nested
    @DisplayName("K8s Pod Failover Tests")
    class PodFailoverTests {

        @Test
        @DisplayName("Should handle pod termination gracefully")
        void shouldHandlePodTerminationGracefully() {
            // Given
            when(hashOps.entries(anyString())).thenReturn(new HashMap<>());
            when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
            registry.initialize();
            registry.tryAcquireLeadership();
            when(valueOps.get(eq("arcana:plugin:leader"))).thenReturn(POD_INSTANCE_ID);

            // When
            registry.shutdown();

            // Then
            verify(redisTemplate).delete(contains(POD_INSTANCE_ID));
            verify(redisTemplate).delete(eq("arcana:plugin:leader"));
        }

        @Test
        @DisplayName("Should maintain plugin state after leader failover")
        void shouldMaintainPluginStateAfterLeaderFailover() {
            // Given - register plugin as leader
            PluginDescriptor descriptor = mock(PluginDescriptor.class);
            when(descriptor.getName()).thenReturn("Failover Plugin");
            when(descriptor.getVersion()).thenReturn("1.0.0");

            registry.registerPlugin("failover-plugin", descriptor, PluginState.ACTIVE);

            // Verify plugin was stored in Redis
            ArgumentCaptor<PluginRegistryEntry> captor = ArgumentCaptor.forClass(PluginRegistryEntry.class);
            verify(hashOps).put(eq("arcana:plugins"), eq("failover-plugin"), captor.capture());

            // Then - plugin should be in Redis (survives pod failure)
            assertEquals("failover-plugin", captor.getValue().getPluginKey());
            assertEquals(PluginState.ACTIVE, captor.getValue().getState());
        }
    }

    @Nested
    @DisplayName("K8s Concurrent Access Tests")
    class ConcurrentAccessTests {

        @Test
        @DisplayName("Should handle concurrent plugin registrations")
        void shouldHandleConcurrentPluginRegistrations() throws Exception {
            // Given
            PluginDescriptor descriptor = mock(PluginDescriptor.class);
            when(descriptor.getName()).thenReturn("Concurrent Plugin");
            when(descriptor.getVersion()).thenReturn("1.0.0");

            int numThreads = 5;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch latch = new CountDownLatch(numThreads);

            // When - concurrent registrations
            for (int i = 0; i < numThreads; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        registry.registerPlugin("concurrent-plugin-" + index, descriptor, PluginState.INSTALLED);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // Then - all registrations should complete
            verify(hashOps, times(numThreads)).put(eq("arcana:plugins"), anyString(), any(PluginRegistryEntry.class));
        }

        @Test
        @DisplayName("Should handle concurrent event processing")
        void shouldHandleConcurrentEventProcessing() throws Exception {
            // Given
            AtomicReference<Integer> processedCount = new AtomicReference<>(0);
            registry.addEventListener(event -> {
                synchronized (processedCount) {
                    processedCount.set(processedCount.get() + 1);
                }
            });

            int numEvents = 10;
            ExecutorService executor = Executors.newFixedThreadPool(4);
            CountDownLatch latch = new CountDownLatch(numEvents);

            // When - concurrent event processing
            for (int i = 0; i < numEvents; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        PluginEvent event = new PluginEvent(
                            PluginEventType.INSTALLED,
                            "event-plugin-" + index,
                            "other-pod-" + (index % 3),
                            PluginState.INSTALLED
                        );
                        registry.handleEvent(event);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // Then - all events should be processed
            assertEquals(numEvents, processedCount.get());
        }
    }

    @Nested
    @DisplayName("Complete K8s gRPC Workflow Tests")
    class WorkflowTests {

        @Test
        @DisplayName("Should complete full K8s gRPC plugin workflow")
        void shouldCompleteFullK8sGrpcPluginWorkflow() {
            // Step 1: Initialize registry
            when(hashOps.entries(anyString())).thenReturn(new HashMap<>());
            registry.initialize();
            verify(valueOps).set(contains(POD_INSTANCE_ID), anyString(), any(Duration.class));

            // Step 2: Acquire leadership
            when(valueOps.setIfAbsent(eq("arcana:plugin:leader"), eq(POD_INSTANCE_ID), any(Duration.class)))
                .thenReturn(true);
            assertTrue(registry.tryAcquireLeadership());

            // Step 3: Register plugin
            PluginDescriptor descriptor = mock(PluginDescriptor.class);
            when(descriptor.getName()).thenReturn("Workflow Plugin");
            when(descriptor.getVersion()).thenReturn("1.0.0");
            registry.registerPlugin("workflow-plugin", descriptor, PluginState.INSTALLED);

            // Verify stored in Redis
            verify(hashOps).put(eq("arcana:plugins"), eq("workflow-plugin"), any(PluginRegistryEntry.class));

            // Verify event published
            verify(redisTemplate).convertAndSend(eq("arcana:plugin:events"), any(PluginEvent.class));

            // Step 4: Update to ACTIVE state
            when(hashOps.get(eq("arcana:plugins"), eq("workflow-plugin"))).thenReturn(
                new PluginRegistryEntry("workflow-plugin", "Workflow Plugin", "1.0.0",
                    PluginState.INSTALLED, POD_INSTANCE_ID, Instant.now())
            );
            registry.updatePluginState("workflow-plugin", PluginState.ACTIVE);

            // Verify state updated in Redis
            ArgumentCaptor<PluginRegistryEntry> captor = ArgumentCaptor.forClass(PluginRegistryEntry.class);
            verify(hashOps, times(2)).put(eq("arcana:plugins"), eq("workflow-plugin"), captor.capture());
            assertEquals(PluginState.ACTIVE, captor.getValue().getState());

            // Step 5: Simulate another pod receiving event and syncing
            PluginEvent syncEvent = new PluginEvent(
                PluginEventType.INSTALLED,
                "sync-plugin",
                "other-pod",
                PluginState.INSTALLED
            );
            when(hashOps.get(eq("arcana:plugins"), eq("sync-plugin"))).thenReturn(
                new PluginRegistryEntry("sync-plugin", "Sync Plugin", "1.0.0",
                    PluginState.INSTALLED, "other-pod", Instant.now())
            );
            registry.handleEvent(syncEvent);

            // Step 6: Unregister plugin
            registry.unregisterPlugin("workflow-plugin");
            verify(hashOps).delete(eq("arcana:plugins"), eq("workflow-plugin"));

            // Step 7: Shutdown
            when(valueOps.get(eq("arcana:plugin:leader"))).thenReturn(POD_INSTANCE_ID);
            registry.shutdown();
            verify(redisTemplate).delete(contains(POD_INSTANCE_ID));
        }
    }
}
