package com.arcana.cloud.plugin.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExtensionRegistry.
 */
@ExtendWith(MockitoExtension.class)
class ExtensionRegistryTest {

    @Mock
    private BundleContext bundleContext;

    private ExtensionRegistry extensionRegistry;

    @BeforeEach
    void setUp() {
        extensionRegistry = new ExtensionRegistry(bundleContext);
    }

    @Nested
    @DisplayName("Extension Registration Tests")
    class ExtensionRegistrationTests {

        @Test
        @DisplayName("Should register extension successfully")
        void shouldRegisterExtension() {
            // Given
            String pluginKey = "test.plugin";
            TestExtension extension = new TestExtension();

            // When
            extensionRegistry.registerExtension(pluginKey, TestExtension.class, extension);

            // Then - verify by getting extensions
            try {
                when(bundleContext.getServiceReferences(eq(TestExtension.class), any()))
                    .thenReturn(Collections.emptyList());
            } catch (InvalidSyntaxException e) {
                fail("Should not throw exception");
            }

            List<TestExtension> extensions = extensionRegistry.getExtensions(TestExtension.class);
            assertEquals(1, extensions.size());
            assertSame(extension, extensions.get(0));
        }

        @Test
        @DisplayName("Should register multiple extensions of same type")
        void shouldRegisterMultipleExtensions() throws InvalidSyntaxException {
            // Given
            TestExtension ext1 = new TestExtension();
            TestExtension ext2 = new TestExtension();
            when(bundleContext.getServiceReferences(eq(TestExtension.class), any()))
                .thenReturn(Collections.emptyList());

            // When
            extensionRegistry.registerExtension("plugin1", TestExtension.class, ext1);
            extensionRegistry.registerExtension("plugin2", TestExtension.class, ext2);

            // Then
            List<TestExtension> extensions = extensionRegistry.getExtensions(TestExtension.class);
            assertEquals(2, extensions.size());
            assertTrue(extensions.contains(ext1));
            assertTrue(extensions.contains(ext2));
        }

        @Test
        @DisplayName("Should register multiple extension types from same plugin")
        void shouldRegisterMultipleTypesFromSamePlugin() throws InvalidSyntaxException {
            // Given
            String pluginKey = "test.plugin";
            TestExtension testExt = new TestExtension();
            AnotherExtension anotherExt = new AnotherExtension();
            when(bundleContext.getServiceReferences(eq(TestExtension.class), any()))
                .thenReturn(Collections.emptyList());
            when(bundleContext.getServiceReferences(eq(AnotherExtension.class), any()))
                .thenReturn(Collections.emptyList());

            // When
            extensionRegistry.registerExtension(pluginKey, TestExtension.class, testExt);
            extensionRegistry.registerExtension(pluginKey, AnotherExtension.class, anotherExt);

            // Then
            assertEquals(1, extensionRegistry.getExtensions(TestExtension.class).size());
            assertEquals(1, extensionRegistry.getExtensions(AnotherExtension.class).size());
        }
    }

    @Nested
    @DisplayName("Extension Retrieval Tests")
    class ExtensionRetrievalTests {

        @Test
        @DisplayName("Should get extensions by type")
        void shouldGetExtensionsByType() throws InvalidSyntaxException {
            // Given
            TestExtension extension = new TestExtension();
            extensionRegistry.registerExtension("test.plugin", TestExtension.class, extension);
            when(bundleContext.getServiceReferences(eq(TestExtension.class), any()))
                .thenReturn(Collections.emptyList());

            // When
            List<TestExtension> result = extensionRegistry.getExtensions(TestExtension.class);

            // Then
            assertEquals(1, result.size());
            assertSame(extension, result.get(0));
        }

        @Test
        @DisplayName("Should get extensions by type and plugin key")
        void shouldGetExtensionsByTypeAndPluginKey() throws InvalidSyntaxException {
            // Given
            TestExtension ext1 = new TestExtension();
            TestExtension ext2 = new TestExtension();
            extensionRegistry.registerExtension("plugin1", TestExtension.class, ext1);
            extensionRegistry.registerExtension("plugin2", TestExtension.class, ext2);

            // When
            List<TestExtension> plugin1Extensions = extensionRegistry.getExtensions(TestExtension.class, "plugin1");
            List<TestExtension> plugin2Extensions = extensionRegistry.getExtensions(TestExtension.class, "plugin2");

            // Then
            assertEquals(1, plugin1Extensions.size());
            assertSame(ext1, plugin1Extensions.get(0));
            assertEquals(1, plugin2Extensions.size());
            assertSame(ext2, plugin2Extensions.get(0));
        }

        @Test
        @DisplayName("Should return empty list for non-existent plugin key")
        void shouldReturnEmptyListForNonExistentPluginKey() {
            // When
            List<TestExtension> result = extensionRegistry.getExtensions(TestExtension.class, "non.existent");

            // Then
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should also query OSGi service registry")
        void shouldQueryOSGiServiceRegistry() throws InvalidSyntaxException {
            // Given
            TestExtension osgiService = new TestExtension();
            @SuppressWarnings("unchecked")
            ServiceReference<TestExtension> serviceRef = mock(ServiceReference.class);
            when(bundleContext.getServiceReferences(eq(TestExtension.class), any()))
                .thenReturn(Collections.singletonList(serviceRef));
            when(bundleContext.getService(serviceRef)).thenReturn(osgiService);

            // When
            List<TestExtension> result = extensionRegistry.getExtensions(TestExtension.class);

            // Then
            assertEquals(1, result.size());
            assertSame(osgiService, result.get(0));
        }
    }

    @Nested
    @DisplayName("Extension Unregistration Tests")
    class ExtensionUnregistrationTests {

        @Test
        @DisplayName("Should unregister all extensions from plugin")
        void shouldUnregisterExtensionsFromPlugin() throws InvalidSyntaxException {
            // Given
            String pluginKey = "test.plugin";
            TestExtension ext1 = new TestExtension();
            AnotherExtension ext2 = new AnotherExtension();
            extensionRegistry.registerExtension(pluginKey, TestExtension.class, ext1);
            extensionRegistry.registerExtension(pluginKey, AnotherExtension.class, ext2);
            when(bundleContext.getServiceReferences(any(Class.class), any()))
                .thenReturn(Collections.emptyList());

            // When
            extensionRegistry.unregisterExtensions(pluginKey);

            // Then
            assertTrue(extensionRegistry.getExtensions(TestExtension.class).isEmpty());
            assertTrue(extensionRegistry.getExtensions(AnotherExtension.class).isEmpty());
        }

        @Test
        @DisplayName("Should not affect other plugins when unregistering")
        void shouldNotAffectOtherPluginsWhenUnregistering() throws InvalidSyntaxException {
            // Given
            TestExtension ext1 = new TestExtension();
            TestExtension ext2 = new TestExtension();
            extensionRegistry.registerExtension("plugin1", TestExtension.class, ext1);
            extensionRegistry.registerExtension("plugin2", TestExtension.class, ext2);
            when(bundleContext.getServiceReferences(eq(TestExtension.class), any()))
                .thenReturn(Collections.emptyList());

            // When
            extensionRegistry.unregisterExtensions("plugin1");

            // Then
            List<TestExtension> remaining = extensionRegistry.getExtensions(TestExtension.class);
            assertEquals(1, remaining.size());
            assertSame(ext2, remaining.get(0));
        }

        @Test
        @DisplayName("Should handle unregistering non-existent plugin gracefully")
        void shouldHandleUnregisteringNonExistentPluginGracefully() {
            // When/Then - should not throw
            assertDoesNotThrow(() -> extensionRegistry.unregisterExtensions("non.existent"));
        }
    }

    // Test extension classes
    static class TestExtension {
    }

    static class AnotherExtension {
    }
}
