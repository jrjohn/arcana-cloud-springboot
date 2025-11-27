package com.arcana.cloud.plugin.runtime.distributed;

import com.arcana.cloud.plugin.api.PluginDescriptor;
import com.arcana.cloud.plugin.lifecycle.PluginState;
import com.arcana.cloud.plugin.runtime.distributed.DistributedPluginRegistry.PluginEvent;
import com.arcana.cloud.plugin.runtime.distributed.DistributedPluginRegistry.PluginEventType;
import com.arcana.cloud.plugin.runtime.distributed.DistributedPluginRegistry.PluginRegistryEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DistributedPluginRegistry.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DistributedPluginRegistryTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, String, PluginRegistryEntry> hashOps;

    @Mock
    private ValueOperations<String, Object> valueOps;

    private DistributedPluginRegistry registry;
    private static final String INSTANCE_ID = "test-instance-1";

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        when(redisTemplate.<String, PluginRegistryEntry>opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        registry = new DistributedPluginRegistry(redisTemplate, INSTANCE_ID);
    }

    @Nested
    @DisplayName("Initialization Tests")
    class InitializationTests {

        @Test
        @DisplayName("Should initialize successfully")
        void shouldInitializeSuccessfully() {
            // Given
            when(hashOps.entries(anyString())).thenReturn(new HashMap<>());

            // When
            registry.initialize();

            // Then
            verify(valueOps).set(contains(INSTANCE_ID), anyString(), any(Duration.class));
            verify(hashOps).entries(eq("arcana:plugins"));
        }

        @Test
        @DisplayName("Should shutdown successfully")
        void shouldShutdownSuccessfully() {
            // Given
            when(hashOps.entries(anyString())).thenReturn(new HashMap<>());
            registry.initialize();

            // When
            registry.shutdown();

            // Then
            verify(redisTemplate).delete(contains(INSTANCE_ID));
        }
    }

    @Nested
    @DisplayName("Plugin Registration Tests")
    class PluginRegistrationTests {

        @Test
        @DisplayName("Should register plugin in Redis")
        void shouldRegisterPluginInRedis() {
            // Given
            PluginDescriptor descriptor = mock(PluginDescriptor.class);
            when(descriptor.getName()).thenReturn("Test Plugin");
            when(descriptor.getVersion()).thenReturn("1.0.0");

            // When
            registry.registerPlugin("test.plugin", descriptor, PluginState.INSTALLED);

            // Then
            ArgumentCaptor<PluginRegistryEntry> captor = ArgumentCaptor.forClass(PluginRegistryEntry.class);
            verify(hashOps).put(eq("arcana:plugins"), eq("test.plugin"), captor.capture());

            PluginRegistryEntry entry = captor.getValue();
            assertEquals("test.plugin", entry.getPluginKey());
            assertEquals("Test Plugin", entry.getName());
            assertEquals("1.0.0", entry.getVersion());
            assertEquals(PluginState.INSTALLED, entry.getState());
            assertEquals(INSTANCE_ID, entry.getLastModifiedBy());
        }

        @Test
        @DisplayName("Should publish INSTALLED event on registration")
        void shouldPublishInstalledEventOnRegistration() {
            // Given
            PluginDescriptor descriptor = mock(PluginDescriptor.class);
            when(descriptor.getName()).thenReturn("Test Plugin");
            when(descriptor.getVersion()).thenReturn("1.0.0");

            // When
            registry.registerPlugin("test.plugin", descriptor, PluginState.INSTALLED);

            // Then
            ArgumentCaptor<PluginEvent> captor = ArgumentCaptor.forClass(PluginEvent.class);
            verify(redisTemplate).convertAndSend(eq("arcana:plugin:events"), captor.capture());

            PluginEvent event = captor.getValue();
            assertEquals(PluginEventType.INSTALLED, event.getType());
            assertEquals("test.plugin", event.getPluginKey());
            assertEquals(INSTANCE_ID, event.getSourceInstance());
        }
    }

    @Nested
    @DisplayName("Plugin State Update Tests")
    class PluginStateUpdateTests {

        @Test
        @DisplayName("Should update plugin state in Redis")
        void shouldUpdatePluginStateInRedis() {
            // Given
            PluginRegistryEntry existing = new PluginRegistryEntry(
                "test.plugin", "Test Plugin", "1.0.0",
                PluginState.INSTALLED, "other-instance", Instant.now().minusSeconds(60)
            );
            when(hashOps.get(eq("arcana:plugins"), eq("test.plugin"))).thenReturn(existing);

            // When
            registry.updatePluginState("test.plugin", PluginState.ACTIVE);

            // Then
            ArgumentCaptor<PluginRegistryEntry> captor = ArgumentCaptor.forClass(PluginRegistryEntry.class);
            verify(hashOps).put(eq("arcana:plugins"), eq("test.plugin"), captor.capture());

            PluginRegistryEntry updated = captor.getValue();
            assertEquals(PluginState.ACTIVE, updated.getState());
            assertEquals(INSTANCE_ID, updated.getLastModifiedBy());
        }

        @Test
        @DisplayName("Should publish ENABLED event when activating")
        void shouldPublishEnabledEventWhenActivating() {
            // Given
            PluginRegistryEntry existing = new PluginRegistryEntry(
                "test.plugin", "Test Plugin", "1.0.0",
                PluginState.RESOLVED, "other-instance", Instant.now()
            );
            when(hashOps.get(eq("arcana:plugins"), eq("test.plugin"))).thenReturn(existing);

            // When
            registry.updatePluginState("test.plugin", PluginState.ACTIVE);

            // Then
            ArgumentCaptor<PluginEvent> captor = ArgumentCaptor.forClass(PluginEvent.class);
            verify(redisTemplate).convertAndSend(eq("arcana:plugin:events"), captor.capture());

            PluginEvent event = captor.getValue();
            assertEquals(PluginEventType.ENABLED, event.getType());
        }

        @Test
        @DisplayName("Should publish DISABLED event when deactivating")
        void shouldPublishDisabledEventWhenDeactivating() {
            // Given
            PluginRegistryEntry existing = new PluginRegistryEntry(
                "test.plugin", "Test Plugin", "1.0.0",
                PluginState.ACTIVE, "other-instance", Instant.now()
            );
            when(hashOps.get(eq("arcana:plugins"), eq("test.plugin"))).thenReturn(existing);

            // When
            registry.updatePluginState("test.plugin", PluginState.RESOLVED);

            // Then
            ArgumentCaptor<PluginEvent> captor = ArgumentCaptor.forClass(PluginEvent.class);
            verify(redisTemplate).convertAndSend(eq("arcana:plugin:events"), captor.capture());

            PluginEvent event = captor.getValue();
            assertEquals(PluginEventType.DISABLED, event.getType());
        }

        @Test
        @DisplayName("Should not update non-existent plugin")
        void shouldNotUpdateNonExistentPlugin() {
            // Given
            when(hashOps.get(eq("arcana:plugins"), eq("non.existent"))).thenReturn(null);

            // When
            registry.updatePluginState("non.existent", PluginState.ACTIVE);

            // Then
            verify(hashOps, never()).put(anyString(), eq("non.existent"), any());
            verify(redisTemplate, never()).convertAndSend(anyString(), any(PluginEvent.class));
        }
    }

    @Nested
    @DisplayName("Plugin Unregistration Tests")
    class PluginUnregistrationTests {

        @Test
        @DisplayName("Should unregister plugin from Redis")
        void shouldUnregisterPluginFromRedis() {
            // When
            registry.unregisterPlugin("test.plugin");

            // Then
            verify(hashOps).delete(eq("arcana:plugins"), eq("test.plugin"));
        }

        @Test
        @DisplayName("Should publish UNINSTALLED event on unregistration")
        void shouldPublishUninstalledEventOnUnregistration() {
            // When
            registry.unregisterPlugin("test.plugin");

            // Then
            ArgumentCaptor<PluginEvent> captor = ArgumentCaptor.forClass(PluginEvent.class);
            verify(redisTemplate).convertAndSend(eq("arcana:plugin:events"), captor.capture());

            PluginEvent event = captor.getValue();
            assertEquals(PluginEventType.UNINSTALLED, event.getType());
            assertEquals("test.plugin", event.getPluginKey());
            assertEquals(PluginState.UNINSTALLED, event.getState());
        }
    }

    @Nested
    @DisplayName("Plugin Retrieval Tests")
    class PluginRetrievalTests {

        @Test
        @DisplayName("Should get all plugins from Redis")
        void shouldGetAllPluginsFromRedis() {
            // Given
            Map<String, PluginRegistryEntry> entries = new HashMap<>();
            entries.put("plugin1", new PluginRegistryEntry(
                "plugin1", "Plugin 1", "1.0.0", PluginState.ACTIVE, INSTANCE_ID, Instant.now()
            ));
            entries.put("plugin2", new PluginRegistryEntry(
                "plugin2", "Plugin 2", "2.0.0", PluginState.INSTALLED, INSTANCE_ID, Instant.now()
            ));
            when(hashOps.entries(eq("arcana:plugins"))).thenReturn(entries);

            // When
            Map<String, PluginRegistryEntry> result = registry.getAllPlugins();

            // Then
            assertEquals(2, result.size());
            assertTrue(result.containsKey("plugin1"));
            assertTrue(result.containsKey("plugin2"));
        }

        @Test
        @DisplayName("Should get single plugin from Redis")
        void shouldGetSinglePluginFromRedis() {
            // Given
            PluginRegistryEntry entry = new PluginRegistryEntry(
                "test.plugin", "Test Plugin", "1.0.0", PluginState.ACTIVE, INSTANCE_ID, Instant.now()
            );
            when(hashOps.get(eq("arcana:plugins"), eq("test.plugin"))).thenReturn(entry);

            // When
            PluginRegistryEntry result = registry.getPlugin("test.plugin");

            // Then
            assertNotNull(result);
            assertEquals("test.plugin", result.getPluginKey());
            assertEquals("Test Plugin", result.getName());
        }

        @Test
        @DisplayName("Should return null for non-existent plugin")
        void shouldReturnNullForNonExistentPlugin() {
            // Given
            when(hashOps.get(eq("arcana:plugins"), eq("non.existent"))).thenReturn(null);

            // When
            PluginRegistryEntry result = registry.getPlugin("non.existent");

            // Then
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Missing Plugins Detection Tests")
    class MissingPluginsTests {

        @Test
        @DisplayName("Should detect missing plugins")
        void shouldDetectMissingPlugins() {
            // Given
            Map<String, PluginRegistryEntry> entries = new HashMap<>();
            entries.put("plugin1", new PluginRegistryEntry(
                "plugin1", "Plugin 1", "1.0.0", PluginState.ACTIVE, INSTANCE_ID, Instant.now()
            ));
            entries.put("plugin2", new PluginRegistryEntry(
                "plugin2", "Plugin 2", "1.0.0", PluginState.ACTIVE, INSTANCE_ID, Instant.now()
            ));
            entries.put("plugin3", new PluginRegistryEntry(
                "plugin3", "Plugin 3", "1.0.0", PluginState.ACTIVE, INSTANCE_ID, Instant.now()
            ));
            when(hashOps.entries(eq("arcana:plugins"))).thenReturn(entries);

            Set<String> installed = Set.of("plugin1");

            // When
            Set<String> missing = registry.getMissingPlugins(installed);

            // Then
            assertEquals(2, missing.size());
            assertTrue(missing.contains("plugin2"));
            assertTrue(missing.contains("plugin3"));
            assertFalse(missing.contains("plugin1"));
        }

        @Test
        @DisplayName("Should return empty set when all plugins installed")
        void shouldReturnEmptySetWhenAllInstalled() {
            // Given
            Map<String, PluginRegistryEntry> entries = new HashMap<>();
            entries.put("plugin1", new PluginRegistryEntry(
                "plugin1", "Plugin 1", "1.0.0", PluginState.ACTIVE, INSTANCE_ID, Instant.now()
            ));
            when(hashOps.entries(eq("arcana:plugins"))).thenReturn(entries);

            Set<String> installed = Set.of("plugin1");

            // When
            Set<String> missing = registry.getMissingPlugins(installed);

            // Then
            assertTrue(missing.isEmpty());
        }
    }

    @Nested
    @DisplayName("Leadership Tests")
    class LeadershipTests {

        @Test
        @DisplayName("Should acquire leadership when available")
        void shouldAcquireLeadershipWhenAvailable() {
            // Given
            when(valueOps.setIfAbsent(eq("arcana:plugin:leader"), eq(INSTANCE_ID), any(Duration.class)))
                .thenReturn(true);

            // When
            boolean acquired = registry.tryAcquireLeadership();

            // Then
            assertTrue(acquired);
            assertTrue(registry.isLeader());
        }

        @Test
        @DisplayName("Should not acquire leadership when already held")
        void shouldNotAcquireLeadershipWhenHeld() {
            // Given
            when(valueOps.setIfAbsent(eq("arcana:plugin:leader"), eq(INSTANCE_ID), any(Duration.class)))
                .thenReturn(false);
            when(valueOps.get(eq("arcana:plugin:leader"))).thenReturn("other-instance");

            // When
            boolean acquired = registry.tryAcquireLeadership();

            // Then
            assertFalse(acquired);
            assertFalse(registry.isLeader());
        }

        @Test
        @DisplayName("Should renew leadership when already leader")
        void shouldRenewLeadershipWhenAlreadyLeader() {
            // Given
            when(valueOps.setIfAbsent(eq("arcana:plugin:leader"), eq(INSTANCE_ID), any(Duration.class)))
                .thenReturn(false);
            when(valueOps.get(eq("arcana:plugin:leader"))).thenReturn(INSTANCE_ID);

            // When
            boolean acquired = registry.tryAcquireLeadership();

            // Then
            assertTrue(acquired);
            verify(redisTemplate).expire(eq("arcana:plugin:leader"), any(Duration.class));
        }

        @Test
        @DisplayName("Should release leadership")
        void shouldReleaseLeadership() {
            // Given - first acquire leadership
            when(valueOps.setIfAbsent(eq("arcana:plugin:leader"), eq(INSTANCE_ID), any(Duration.class)))
                .thenReturn(true);
            registry.tryAcquireLeadership();

            when(valueOps.get(eq("arcana:plugin:leader"))).thenReturn(INSTANCE_ID);

            // When
            registry.releaseLeadership();

            // Then
            verify(redisTemplate).delete(eq("arcana:plugin:leader"));
            assertFalse(registry.isLeader());
        }
    }

    @Nested
    @DisplayName("Event Handling Tests")
    class EventHandlingTests {

        @Test
        @DisplayName("Should add event listener")
        void shouldAddEventListener() {
            // Given
            AtomicReference<PluginEvent> receivedEvent = new AtomicReference<>();
            Consumer<PluginEvent> listener = receivedEvent::set;

            // When
            registry.addEventListener(listener);

            // Then - listener added successfully
            registry.removeEventListener(listener);
        }

        @Test
        @DisplayName("Should notify event listeners")
        void shouldNotifyEventListeners() {
            // Given
            AtomicReference<PluginEvent> receivedEvent = new AtomicReference<>();
            Consumer<PluginEvent> listener = receivedEvent::set;
            registry.addEventListener(listener);

            PluginEvent event = new PluginEvent(
                PluginEventType.INSTALLED, "test.plugin", "other-instance", PluginState.INSTALLED
            );

            when(hashOps.get(eq("arcana:plugins"), eq("test.plugin"))).thenReturn(
                new PluginRegistryEntry("test.plugin", "Test", "1.0.0", PluginState.INSTALLED, "other-instance", Instant.now())
            );

            // When
            registry.handleEvent(event);

            // Then
            assertNotNull(receivedEvent.get());
            assertEquals("test.plugin", receivedEvent.get().getPluginKey());
        }

        @Test
        @DisplayName("Should ignore events from same instance")
        void shouldIgnoreEventsFromSameInstance() {
            // Given
            AtomicReference<PluginEvent> receivedEvent = new AtomicReference<>();
            Consumer<PluginEvent> listener = receivedEvent::set;
            registry.addEventListener(listener);

            PluginEvent event = new PluginEvent(
                PluginEventType.INSTALLED, "test.plugin", INSTANCE_ID, PluginState.INSTALLED
            );

            // When
            registry.handleEvent(event);

            // Then
            assertNull(receivedEvent.get());
        }

        @Test
        @DisplayName("Should handle UNINSTALLED event by removing from local cache")
        void shouldHandleUninstalledEvent() {
            // Given
            PluginEvent event = new PluginEvent(
                PluginEventType.UNINSTALLED, "test.plugin", "other-instance", PluginState.UNINSTALLED
            );

            // When
            registry.handleEvent(event);

            // Then - no exception, event handled
        }
    }

    @Nested
    @DisplayName("PluginRegistryEntry Tests")
    class PluginRegistryEntryTests {

        @Test
        @DisplayName("Should create entry with all fields")
        void shouldCreateEntryWithAllFields() {
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
        @DisplayName("Should support setters")
        void shouldSupportSetters() {
            // Given
            PluginRegistryEntry entry = new PluginRegistryEntry();

            // When
            entry.setPluginKey("test.plugin");
            entry.setName("Test Plugin");
            entry.setVersion("2.0.0");
            entry.setState(PluginState.RESOLVED);
            entry.setLastModifiedBy("new-instance");
            Instant now = Instant.now();
            entry.setLastModified(now);

            // Then
            assertEquals("test.plugin", entry.getPluginKey());
            assertEquals("Test Plugin", entry.getName());
            assertEquals("2.0.0", entry.getVersion());
            assertEquals(PluginState.RESOLVED, entry.getState());
            assertEquals("new-instance", entry.getLastModifiedBy());
            assertEquals(now, entry.getLastModified());
        }
    }

    @Nested
    @DisplayName("PluginEvent Tests")
    class PluginEventTests {

        @Test
        @DisplayName("Should create event with all fields")
        void shouldCreateEventWithAllFields() {
            // When
            PluginEvent event = new PluginEvent(
                PluginEventType.ENABLED, "test.plugin", INSTANCE_ID, PluginState.ACTIVE
            );

            // Then
            assertEquals(PluginEventType.ENABLED, event.getType());
            assertEquals("test.plugin", event.getPluginKey());
            assertEquals(INSTANCE_ID, event.getSourceInstance());
            assertEquals(PluginState.ACTIVE, event.getState());
            assertNotNull(event.getTimestamp());
        }

        @Test
        @DisplayName("Should support setters")
        void shouldSupportSetters() {
            // Given
            PluginEvent event = new PluginEvent();

            // When
            event.setType(PluginEventType.DISABLED);
            event.setPluginKey("test.plugin");
            event.setSourceInstance("new-instance");
            event.setState(PluginState.RESOLVED);
            Instant now = Instant.now();
            event.setTimestamp(now);

            // Then
            assertEquals(PluginEventType.DISABLED, event.getType());
            assertEquals("test.plugin", event.getPluginKey());
            assertEquals("new-instance", event.getSourceInstance());
            assertEquals(PluginState.RESOLVED, event.getState());
            assertEquals(now, event.getTimestamp());
        }
    }
}
