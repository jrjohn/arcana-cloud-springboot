package com.arcana.cloud.plugin.version;

import com.arcana.cloud.plugin.api.Plugin;
import com.arcana.cloud.plugin.api.PluginContext;
import com.arcana.cloud.plugin.api.PluginDescriptor;
import com.arcana.cloud.plugin.lifecycle.PluginState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for version annotations: {@link Since}, {@link ApiVersion},
 * {@link DeprecatedSince}, and {@link RequiresApiVersion}.
 */
@DisplayName("Version Annotations")
class VersionAnnotationsTest {

    @Nested
    @DisplayName("@Since Annotation")
    class SinceAnnotationTest {

        @Since("1.0.0")
        static class SinceAnnotatedClass {}

        @Since(value = "1.2.0", description = "Added for feature X")
        static class SinceWithDescriptionClass {}

        @Test
        @DisplayName("should be present on annotated class")
        void shouldBePresentOnAnnotatedClass() {
            assertTrue(SinceAnnotatedClass.class.isAnnotationPresent(Since.class));
        }

        @Test
        @DisplayName("should have correct version value")
        void shouldHaveCorrectVersionValue() {
            Since since = SinceAnnotatedClass.class.getAnnotation(Since.class);
            assertEquals("1.0.0", since.value());
        }

        @Test
        @DisplayName("should have empty description by default")
        void shouldHaveEmptyDescriptionByDefault() {
            Since since = SinceAnnotatedClass.class.getAnnotation(Since.class);
            assertEquals("", since.description());
        }

        @Test
        @DisplayName("should support custom description")
        void shouldSupportCustomDescription() {
            Since since = SinceWithDescriptionClass.class.getAnnotation(Since.class);
            assertEquals("1.2.0", since.value());
            assertEquals("Added for feature X", since.description());
        }

        @Test
        @DisplayName("should be present on Plugin interface")
        void shouldBePresentOnPluginInterface() {
            assertTrue(Plugin.class.isAnnotationPresent(Since.class));
            Since since = Plugin.class.getAnnotation(Since.class);
            assertEquals("1.0.0", since.value());
        }
    }

    @Nested
    @DisplayName("@ApiVersion Annotation")
    class ApiVersionAnnotationTest {

        @ApiVersion(version = "1.0.0", stability = ApiVersion.Stability.STABLE)
        static class StableApiClass {}

        @ApiVersion(version = "0.9.0", stability = ApiVersion.Stability.EXPERIMENTAL)
        static class ExperimentalApiClass {}

        @ApiVersion(version = "1.1.0", stability = ApiVersion.Stability.EVOLVING)
        static class EvolvingApiClass {}

        @ApiVersion(version = "1.0.0", stability = ApiVersion.Stability.INTERNAL)
        static class InternalApiClass {}

        @ApiVersion(version = "0.5.0", stability = ApiVersion.Stability.DEPRECATED)
        static class DeprecatedApiClass {}

        @Test
        @DisplayName("should be present on annotated class")
        void shouldBePresentOnAnnotatedClass() {
            assertTrue(StableApiClass.class.isAnnotationPresent(ApiVersion.class));
        }

        @Test
        @DisplayName("should have correct version value")
        void shouldHaveCorrectVersionValue() {
            ApiVersion apiVersion = StableApiClass.class.getAnnotation(ApiVersion.class);
            assertEquals("1.0.0", apiVersion.version());
        }

        @Test
        @DisplayName("should support STABLE stability level")
        void shouldSupportStableStabilityLevel() {
            ApiVersion apiVersion = StableApiClass.class.getAnnotation(ApiVersion.class);
            assertEquals(ApiVersion.Stability.STABLE, apiVersion.stability());
        }

        @Test
        @DisplayName("should support EXPERIMENTAL stability level")
        void shouldSupportExperimentalStabilityLevel() {
            ApiVersion apiVersion = ExperimentalApiClass.class.getAnnotation(ApiVersion.class);
            assertEquals(ApiVersion.Stability.EXPERIMENTAL, apiVersion.stability());
        }

        @Test
        @DisplayName("should support EVOLVING stability level")
        void shouldSupportEvolvingStabilityLevel() {
            ApiVersion apiVersion = EvolvingApiClass.class.getAnnotation(ApiVersion.class);
            assertEquals(ApiVersion.Stability.EVOLVING, apiVersion.stability());
        }

        @Test
        @DisplayName("should support INTERNAL stability level")
        void shouldSupportInternalStabilityLevel() {
            ApiVersion apiVersion = InternalApiClass.class.getAnnotation(ApiVersion.class);
            assertEquals(ApiVersion.Stability.INTERNAL, apiVersion.stability());
        }

        @Test
        @DisplayName("should support DEPRECATED stability level")
        void shouldSupportDeprecatedStabilityLevel() {
            ApiVersion apiVersion = DeprecatedApiClass.class.getAnnotation(ApiVersion.class);
            assertEquals(ApiVersion.Stability.DEPRECATED, apiVersion.stability());
        }

        @Test
        @DisplayName("should default to STABLE stability level")
        void shouldDefaultToStableStabilityLevel() {
            // Check default value from annotation definition
            try {
                Method stabilityMethod = ApiVersion.class.getMethod("stability");
                ApiVersion.Stability defaultValue = (ApiVersion.Stability) stabilityMethod.getDefaultValue();
                assertEquals(ApiVersion.Stability.STABLE, defaultValue);
            } catch (NoSuchMethodException e) {
                fail("stability() method should exist on ApiVersion annotation");
            }
        }

        @Test
        @DisplayName("should be present on Plugin interface")
        void shouldBePresentOnPluginInterface() {
            assertTrue(Plugin.class.isAnnotationPresent(ApiVersion.class));
            ApiVersion apiVersion = Plugin.class.getAnnotation(ApiVersion.class);
            assertEquals("1.0.0", apiVersion.version());
            assertEquals(ApiVersion.Stability.STABLE, apiVersion.stability());
        }

        @Test
        @DisplayName("should be present on PluginApiVersion class")
        void shouldBePresentOnPluginApiVersionClass() {
            assertTrue(PluginApiVersion.class.isAnnotationPresent(ApiVersion.class));
        }
    }

    @Nested
    @DisplayName("@DeprecatedSince Annotation")
    class DeprecatedSinceAnnotationTest {

        @DeprecatedSince(
                version = "1.5.0",
                replacement = "newMethod()",
                removalVersion = "2.0.0",
                reason = "Performance improvements"
        )
        @Deprecated
        static void deprecatedMethod() {}

        @DeprecatedSince(version = "1.3.0")
        @Deprecated
        static void minimalDeprecatedMethod() {}

        @Test
        @DisplayName("should be present on annotated method")
        void shouldBePresentOnAnnotatedMethod() throws NoSuchMethodException {
            Method method = VersionAnnotationsTest.DeprecatedSinceAnnotationTest.class
                    .getDeclaredMethod("deprecatedMethod");
            assertTrue(method.isAnnotationPresent(DeprecatedSince.class));
        }

        @Test
        @DisplayName("should have correct version value")
        void shouldHaveCorrectVersionValue() throws NoSuchMethodException {
            Method method = VersionAnnotationsTest.DeprecatedSinceAnnotationTest.class
                    .getDeclaredMethod("deprecatedMethod");
            DeprecatedSince annotation = method.getAnnotation(DeprecatedSince.class);
            assertEquals("1.5.0", annotation.version());
        }

        @Test
        @DisplayName("should have replacement information")
        void shouldHaveReplacementInformation() throws NoSuchMethodException {
            Method method = VersionAnnotationsTest.DeprecatedSinceAnnotationTest.class
                    .getDeclaredMethod("deprecatedMethod");
            DeprecatedSince annotation = method.getAnnotation(DeprecatedSince.class);
            assertEquals("newMethod()", annotation.replacement());
        }

        @Test
        @DisplayName("should have removal version")
        void shouldHaveRemovalVersion() throws NoSuchMethodException {
            Method method = VersionAnnotationsTest.DeprecatedSinceAnnotationTest.class
                    .getDeclaredMethod("deprecatedMethod");
            DeprecatedSince annotation = method.getAnnotation(DeprecatedSince.class);
            assertEquals("2.0.0", annotation.removalVersion());
        }

        @Test
        @DisplayName("should have deprecation reason")
        void shouldHaveDeprecationReason() throws NoSuchMethodException {
            Method method = VersionAnnotationsTest.DeprecatedSinceAnnotationTest.class
                    .getDeclaredMethod("deprecatedMethod");
            DeprecatedSince annotation = method.getAnnotation(DeprecatedSince.class);
            assertEquals("Performance improvements", annotation.reason());
        }

        @Test
        @DisplayName("should have empty defaults for optional fields")
        void shouldHaveEmptyDefaultsForOptionalFields() throws NoSuchMethodException {
            Method method = VersionAnnotationsTest.DeprecatedSinceAnnotationTest.class
                    .getDeclaredMethod("minimalDeprecatedMethod");
            DeprecatedSince annotation = method.getAnnotation(DeprecatedSince.class);
            assertEquals("1.3.0", annotation.version());
            assertEquals("", annotation.replacement());
            assertEquals("", annotation.removalVersion());
            assertEquals("", annotation.reason());
        }
    }

    @Nested
    @DisplayName("@RequiresApiVersion Annotation")
    class RequiresApiVersionAnnotationTest {

        @RequiresApiVersion(minimum = "1.0.0", tested = "1.2.0")
        static class PluginWithVersionRequirement implements Plugin {
            @Override
            public void onInstall(PluginContext context) {}

            @Override
            public void onEnable() {}

            @Override
            public void onDisable() {}

            @Override
            public PluginDescriptor getDescriptor() { return null; }

            @Override
            public PluginState getState() { return null; }
        }

        @RequiresApiVersion(minimum = "1.0.0", tested = "1.5.0", maximum = "2.0.0")
        static class PluginWithMaxVersion implements Plugin {
            @Override
            public void onInstall(PluginContext context) {}

            @Override
            public void onEnable() {}

            @Override
            public void onDisable() {}

            @Override
            public PluginDescriptor getDescriptor() { return null; }

            @Override
            public PluginState getState() { return null; }
        }

        @RequiresApiVersion(minimum = "1.0.0")
        static class PluginWithMinimumOnly implements Plugin {
            @Override
            public void onInstall(PluginContext context) {}

            @Override
            public void onEnable() {}

            @Override
            public void onDisable() {}

            @Override
            public PluginDescriptor getDescriptor() { return null; }

            @Override
            public PluginState getState() { return null; }
        }

        @Test
        @DisplayName("should be present on annotated plugin class")
        void shouldBePresentOnAnnotatedPluginClass() {
            assertTrue(PluginWithVersionRequirement.class.isAnnotationPresent(RequiresApiVersion.class));
        }

        @Test
        @DisplayName("should have minimum version requirement")
        void shouldHaveMinimumVersionRequirement() {
            RequiresApiVersion annotation = PluginWithVersionRequirement.class.getAnnotation(RequiresApiVersion.class);
            assertEquals("1.0.0", annotation.minimum());
        }

        @Test
        @DisplayName("should have tested version")
        void shouldHaveTestedVersion() {
            RequiresApiVersion annotation = PluginWithVersionRequirement.class.getAnnotation(RequiresApiVersion.class);
            assertEquals("1.2.0", annotation.tested());
        }

        @Test
        @DisplayName("should support maximum version constraint")
        void shouldSupportMaximumVersionConstraint() {
            RequiresApiVersion annotation = PluginWithMaxVersion.class.getAnnotation(RequiresApiVersion.class);
            assertEquals("1.0.0", annotation.minimum());
            assertEquals("1.5.0", annotation.tested());
            assertEquals("2.0.0", annotation.maximum());
        }

        @Test
        @DisplayName("should have empty tested version by default")
        void shouldHaveEmptyTestedVersionByDefault() {
            RequiresApiVersion annotation = PluginWithMinimumOnly.class.getAnnotation(RequiresApiVersion.class);
            assertEquals("1.0.0", annotation.minimum());
            assertEquals("", annotation.tested());
        }

        @Test
        @DisplayName("should have empty maximum version by default")
        void shouldHaveEmptyMaximumVersionByDefault() {
            RequiresApiVersion annotation = PluginWithVersionRequirement.class.getAnnotation(RequiresApiVersion.class);
            assertEquals("", annotation.maximum());
        }

        @Test
        @DisplayName("should be retained at runtime")
        void shouldBeRetainedAtRuntime() {
            Annotation[] annotations = PluginWithVersionRequirement.class.getAnnotations();
            boolean found = false;
            for (Annotation annotation : annotations) {
                if (annotation instanceof RequiresApiVersion) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "RequiresApiVersion annotation should be retained at runtime");
        }
    }

    @Nested
    @DisplayName("Annotation Stability Enum")
    class StabilityEnumTest {

        @Test
        @DisplayName("should have all expected stability levels")
        void shouldHaveAllExpectedStabilityLevels() {
            ApiVersion.Stability[] values = ApiVersion.Stability.values();
            assertEquals(5, values.length);
        }

        @Test
        @DisplayName("should include STABLE level")
        void shouldIncludeStableLevel() {
            assertNotNull(ApiVersion.Stability.valueOf("STABLE"));
        }

        @Test
        @DisplayName("should include EVOLVING level")
        void shouldIncludeEvolvingLevel() {
            assertNotNull(ApiVersion.Stability.valueOf("EVOLVING"));
        }

        @Test
        @DisplayName("should include EXPERIMENTAL level")
        void shouldIncludeExperimentalLevel() {
            assertNotNull(ApiVersion.Stability.valueOf("EXPERIMENTAL"));
        }

        @Test
        @DisplayName("should include INTERNAL level")
        void shouldIncludeInternalLevel() {
            assertNotNull(ApiVersion.Stability.valueOf("INTERNAL"));
        }

        @Test
        @DisplayName("should include DEPRECATED level")
        void shouldIncludeDeprecatedLevel() {
            assertNotNull(ApiVersion.Stability.valueOf("DEPRECATED"));
        }
    }
}
