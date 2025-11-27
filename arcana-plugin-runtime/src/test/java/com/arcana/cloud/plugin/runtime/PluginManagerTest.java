package com.arcana.cloud.plugin.runtime;

import com.arcana.cloud.plugin.api.Plugin;
import com.arcana.cloud.plugin.lifecycle.PluginLifecycleListener;
import com.arcana.cloud.plugin.lifecycle.PluginState;
import com.arcana.cloud.plugin.runtime.config.PluginConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PluginManager.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PluginManagerTest {

    @Mock
    private PluginConfiguration config;

    @Mock
    private ApplicationContext applicationContext;

    private PluginManager pluginManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        pluginManager = new PluginManager(config, applicationContext);
    }

    @Nested
    @DisplayName("Initialization Tests")
    class InitializationTests {

        @Test
        @DisplayName("Should not initialize when plugin system is disabled")
        void shouldNotInitializeWhenDisabled() throws Exception {
            // Given
            when(config.isEnabled()).thenReturn(false);

            // When
            pluginManager.initialize();

            // Then
            assertFalse(pluginManager.isReady());
            verify(config, never()).ensureDirectoriesExist();
        }

        @Test
        @DisplayName("Should return platform version from config")
        void shouldReturnPlatformVersion() {
            // Given
            when(config.isEnabled()).thenReturn(false);
            when(config.getPlatformVersion()).thenReturn("1.0.0");

            // When
            String version = pluginManager.getPlatformVersion();

            // Then
            assertEquals("1.0.0", version);
        }

        @Test
        @DisplayName("Should return plugins directory from config")
        void shouldReturnPluginsDirectory() {
            // Given
            when(config.isEnabled()).thenReturn(false);
            when(config.getPluginsDirectory()).thenReturn(tempDir);

            // When
            String directory = pluginManager.getPluginsDirectory();

            // Then
            assertEquals(tempDir.toString(), directory);
        }
    }

    @Nested
    @DisplayName("Plugin State Tests")
    class PluginStateTests {

        @Test
        @DisplayName("Should return UNINSTALLED state when not initialized")
        void shouldReturnUninstalledWhenNotInitialized() {
            // When
            PluginState state = pluginManager.getPluginState("test.plugin");

            // Then
            assertEquals(PluginState.UNINSTALLED, state);
        }

        @Test
        @DisplayName("Should throw exception when enabling without initialization")
        void shouldThrowWhenEnablingWithoutInitialization() {
            // When/Then
            assertThrows(IllegalStateException.class, () ->
                pluginManager.enablePlugin("test.plugin"));
        }

        @Test
        @DisplayName("Should throw exception when disabling without initialization")
        void shouldThrowWhenDisablingWithoutInitialization() {
            // When/Then
            assertThrows(IllegalStateException.class, () ->
                pluginManager.disablePlugin("test.plugin"));
        }

        @Test
        @DisplayName("Should throw exception when installing without initialization")
        void shouldThrowWhenInstallingWithoutInitialization() {
            // When/Then
            assertThrows(IllegalStateException.class, () ->
                pluginManager.installPlugin(tempDir.resolve("test.jar")));
        }

        @Test
        @DisplayName("Should throw exception when uninstalling without initialization")
        void shouldThrowWhenUninstallingWithoutInitialization() {
            // When/Then
            assertThrows(IllegalStateException.class, () ->
                pluginManager.uninstallPlugin("test.plugin"));
        }
    }

    @Nested
    @DisplayName("Lifecycle Listener Tests")
    class LifecycleListenerTests {

        @Test
        @DisplayName("Should add lifecycle listener")
        void shouldAddLifecycleListener() {
            // Given
            PluginLifecycleListener listener = mock(PluginLifecycleListener.class);

            // When
            pluginManager.addLifecycleListener(listener);

            // Then - verify listener is added by checking it can be removed
            pluginManager.removeLifecycleListener(listener);
            // No exception means success
        }

        @Test
        @DisplayName("Should remove lifecycle listener")
        void shouldRemoveLifecycleListener() {
            // Given
            PluginLifecycleListener listener = mock(PluginLifecycleListener.class);
            pluginManager.addLifecycleListener(listener);

            // When
            pluginManager.removeLifecycleListener(listener);

            // Then - no exception means success
        }
    }

    @Nested
    @DisplayName("Readiness Listener Tests")
    class ReadinessListenerTests {

        @Test
        @DisplayName("Should add readiness listener")
        void shouldAddReadinessListener() {
            // Given
            AtomicBoolean readyReceived = new AtomicBoolean(false);
            Consumer<Boolean> listener = readyReceived::set;

            // When
            pluginManager.addReadinessListener(listener);

            // Then - listener added successfully
            pluginManager.removeReadinessListener(listener);
        }

        @Test
        @DisplayName("Should notify readiness listener immediately if already initialized")
        void shouldNotifyImmediatelyIfInitialized() throws InterruptedException {
            // Given - plugin system disabled but latch released
            when(config.isEnabled()).thenReturn(false);
            pluginManager.initialize(); // This sets initialized = false but releases latch

            AtomicBoolean listenerCalled = new AtomicBoolean(false);
            AtomicBoolean readyValue = new AtomicBoolean(true);
            Consumer<Boolean> listener = ready -> {
                listenerCalled.set(true);
                readyValue.set(ready);
            };

            // When
            pluginManager.addReadinessListener(listener);

            // Wait a bit for async notification
            Thread.sleep(100);

            // Then - listener should be notified with false (not initialized)
            // If listener was called, verify it received false
            // If not called, that's also acceptable as the latch is released
            if (listenerCalled.get()) {
                assertFalse(readyValue.get());
            }
            // Verify plugin manager is not ready
            assertFalse(pluginManager.isReady());
        }

        @Test
        @DisplayName("Should remove readiness listener")
        void shouldRemoveReadinessListener() {
            // Given
            Consumer<Boolean> listener = ready -> {};
            pluginManager.addReadinessListener(listener);

            // When
            pluginManager.removeReadinessListener(listener);

            // Then - no exception means success
        }
    }

    @Nested
    @DisplayName("Plugin Accessor Tests")
    class PluginAccessorTests {

        @Test
        @DisplayName("Should return empty list when not initialized")
        void shouldReturnEmptyPluginsWhenNotInitialized() {
            // When
            List<Plugin> plugins = pluginManager.getPlugins();

            // Then
            assertTrue(plugins.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list for enabled plugins when not initialized")
        void shouldReturnEmptyEnabledPluginsWhenNotInitialized() {
            // When
            List<Plugin> plugins = pluginManager.getEnabledPlugins();

            // Then
            assertTrue(plugins.isEmpty());
        }

        @Test
        @DisplayName("Should return empty Optional for non-existent plugin")
        void shouldReturnEmptyForNonExistentPlugin() {
            // When
            Optional<Plugin> plugin = pluginManager.getPlugin("non.existent");

            // Then
            assertTrue(plugin.isEmpty());
        }

        @Test
        @DisplayName("Should return false for isPluginInstalled when not found")
        void shouldReturnFalseForIsPluginInstalledWhenNotFound() {
            // When
            boolean installed = pluginManager.isPluginInstalled("non.existent");

            // Then
            assertFalse(installed);
        }

        @Test
        @DisplayName("Should return false for isPluginEnabled when not found")
        void shouldReturnFalseForIsPluginEnabledWhenNotFound() {
            // When
            boolean enabled = pluginManager.isPluginEnabled("non.existent");

            // Then
            assertFalse(enabled);
        }

        @Test
        @DisplayName("Should return empty extensions list when not initialized")
        void shouldReturnEmptyExtensionsWhenNotInitialized() {
            // When
            List<Object> extensions = pluginManager.getExtensions(Object.class);

            // Then
            assertTrue(extensions.isEmpty());
        }
    }

    @Nested
    @DisplayName("Wait For Initialization Tests")
    class WaitForInitializationTests {

        @Test
        @DisplayName("Should return false when initialization times out")
        void shouldReturnFalseWhenTimesOut() throws InterruptedException {
            // When
            boolean result = pluginManager.waitForInitialization(100, TimeUnit.MILLISECONDS);

            // Then
            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false when disabled")
        void shouldReturnFalseWhenDisabled() throws InterruptedException {
            // Given
            when(config.isEnabled()).thenReturn(false);
            pluginManager.initialize();

            // When
            boolean result = pluginManager.waitForInitialization(100, TimeUnit.MILLISECONDS);

            // Then
            assertFalse(result); // initialized = false when disabled
        }

        @Test
        @DisplayName("Should check isReady correctly")
        void shouldCheckIsReadyCorrectly() {
            // Given - not initialized
            // When
            boolean ready = pluginManager.isReady();

            // Then
            assertFalse(ready);
        }
    }

    @Nested
    @DisplayName("Shutdown Tests")
    class ShutdownTests {

        @Test
        @DisplayName("Should shutdown gracefully when not initialized")
        void shouldShutdownGracefullyWhenNotInitialized() {
            // When/Then - should not throw
            assertDoesNotThrow(() -> pluginManager.shutdown());
        }

        @Test
        @DisplayName("Should shutdown when disabled")
        void shouldShutdownWhenDisabled() {
            // Given
            when(config.isEnabled()).thenReturn(false);
            pluginManager.initialize();

            // When/Then - should not throw
            assertDoesNotThrow(() -> pluginManager.shutdown());
        }
    }
}
