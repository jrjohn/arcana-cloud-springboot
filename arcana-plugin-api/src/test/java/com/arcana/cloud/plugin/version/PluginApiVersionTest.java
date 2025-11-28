package com.arcana.cloud.plugin.version;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PluginApiVersion}.
 */
@DisplayName("PluginApiVersion")
class PluginApiVersionTest {

    @Nested
    @DisplayName("Version Constants")
    class VersionConstants {

        @Test
        @DisplayName("should have valid VERSION constant")
        void shouldHaveValidVersionConstant() {
            assertNotNull(PluginApiVersion.VERSION);
            assertFalse(PluginApiVersion.VERSION.isEmpty());
            assertTrue(PluginApiVersion.VERSION.matches("\\d+\\.\\d+\\.\\d+"));
        }

        @Test
        @DisplayName("should have consistent version parts")
        void shouldHaveConsistentVersionParts() {
            String expectedVersion = PluginApiVersion.MAJOR + "." +
                    PluginApiVersion.MINOR + "." +
                    PluginApiVersion.PATCH;
            assertEquals(expectedVersion, PluginApiVersion.VERSION);
        }

        @Test
        @DisplayName("should have valid MINIMUM_SUPPORTED constant")
        void shouldHaveValidMinimumSupportedConstant() {
            assertNotNull(PluginApiVersion.MINIMUM_SUPPORTED);
            assertFalse(PluginApiVersion.MINIMUM_SUPPORTED.isEmpty());
            assertTrue(PluginApiVersion.MINIMUM_SUPPORTED.matches("\\d+\\.\\d+\\.\\d+"));
        }

        @Test
        @DisplayName("should have non-negative version numbers")
        void shouldHaveNonNegativeVersionNumbers() {
            assertTrue(PluginApiVersion.MAJOR >= 0);
            assertTrue(PluginApiVersion.MINOR >= 0);
            assertTrue(PluginApiVersion.PATCH >= 0);
        }
    }

    @Nested
    @DisplayName("parseVersion()")
    class ParseVersion {

        @Test
        @DisplayName("should parse standard version format")
        void shouldParseStandardVersionFormat() {
            int[] result = PluginApiVersion.parseVersion("1.2.3");

            assertArrayEquals(new int[]{1, 2, 3}, result);
        }

        @Test
        @DisplayName("should parse version with zeros")
        void shouldParseVersionWithZeros() {
            int[] result = PluginApiVersion.parseVersion("0.0.0");

            assertArrayEquals(new int[]{0, 0, 0}, result);
        }

        @Test
        @DisplayName("should parse version with large numbers")
        void shouldParseVersionWithLargeNumbers() {
            int[] result = PluginApiVersion.parseVersion("10.20.30");

            assertArrayEquals(new int[]{10, 20, 30}, result);
        }

        @Test
        @DisplayName("should parse version with only major")
        void shouldParseVersionWithOnlyMajor() {
            int[] result = PluginApiVersion.parseVersion("5");

            assertArrayEquals(new int[]{5, 0, 0}, result);
        }

        @Test
        @DisplayName("should parse version with major and minor only")
        void shouldParseVersionWithMajorAndMinorOnly() {
            int[] result = PluginApiVersion.parseVersion("2.5");

            assertArrayEquals(new int[]{2, 5, 0}, result);
        }

        @Test
        @DisplayName("should parse version with pre-release suffix")
        void shouldParseVersionWithPreReleaseSuffix() {
            int[] result = PluginApiVersion.parseVersion("1.2.3-SNAPSHOT");

            assertArrayEquals(new int[]{1, 2, 3}, result);
        }

        @Test
        @DisplayName("should parse version with beta suffix")
        void shouldParseVersionWithBetaSuffix() {
            int[] result = PluginApiVersion.parseVersion("2.0.0-beta.1");

            assertArrayEquals(new int[]{2, 0, 0}, result);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should throw exception for null or empty version")
        void shouldThrowExceptionForNullOrEmptyVersion(String version) {
            assertThrows(IllegalArgumentException.class, () ->
                    PluginApiVersion.parseVersion(version));
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "1.x.0", "a.b.c", "1.2.three"})
        @DisplayName("should throw exception for invalid version format")
        void shouldThrowExceptionForInvalidVersionFormat(String version) {
            assertThrows(IllegalArgumentException.class, () ->
                    PluginApiVersion.parseVersion(version));
        }
    }

    @Nested
    @DisplayName("compare()")
    class Compare {

        @ParameterizedTest
        @CsvSource({
                "1.0.0, 1.0.0, 0",
                "2.0.0, 2.0.0, 0",
                "1.5.3, 1.5.3, 0"
        })
        @DisplayName("should return zero for equal versions")
        void shouldReturnZeroForEqualVersions(String v1, String v2, int expected) {
            assertEquals(expected, PluginApiVersion.compare(v1, v2));
        }

        @ParameterizedTest
        @CsvSource({
                "2.0.0, 1.0.0",
                "1.1.0, 1.0.0",
                "1.0.1, 1.0.0",
                "10.0.0, 9.9.9"
        })
        @DisplayName("should return positive when first version is greater")
        void shouldReturnPositiveWhenFirstVersionIsGreater(String v1, String v2) {
            assertTrue(PluginApiVersion.compare(v1, v2) > 0);
        }

        @ParameterizedTest
        @CsvSource({
                "1.0.0, 2.0.0",
                "1.0.0, 1.1.0",
                "1.0.0, 1.0.1",
                "9.9.9, 10.0.0"
        })
        @DisplayName("should return negative when first version is smaller")
        void shouldReturnNegativeWhenFirstVersionIsSmaller(String v1, String v2) {
            assertTrue(PluginApiVersion.compare(v1, v2) < 0);
        }

        @Test
        @DisplayName("should handle major version difference")
        void shouldHandleMajorVersionDifference() {
            assertTrue(PluginApiVersion.compare("3.0.0", "2.9.9") > 0);
            assertTrue(PluginApiVersion.compare("1.9.9", "2.0.0") < 0);
        }

        @Test
        @DisplayName("should handle minor version difference")
        void shouldHandleMinorVersionDifference() {
            assertTrue(PluginApiVersion.compare("1.5.0", "1.4.9") > 0);
            assertTrue(PluginApiVersion.compare("1.4.9", "1.5.0") < 0);
        }

        @Test
        @DisplayName("should handle patch version difference")
        void shouldHandlePatchVersionDifference() {
            assertTrue(PluginApiVersion.compare("1.0.5", "1.0.4") > 0);
            assertTrue(PluginApiVersion.compare("1.0.4", "1.0.5") < 0);
        }
    }

    @Nested
    @DisplayName("isAtLeast()")
    class IsAtLeast {

        @ParameterizedTest
        @CsvSource({
                "1.0.0, 1.0.0, true",
                "1.1.0, 1.0.0, true",
                "2.0.0, 1.0.0, true",
                "1.0.1, 1.0.0, true",
                "0.9.0, 1.0.0, false",
                "1.0.0, 1.0.1, false",
                "1.0.0, 1.1.0, false"
        })
        @DisplayName("should correctly determine if version is at least required")
        void shouldCorrectlyDetermineIfVersionIsAtLeastRequired(String v1, String v2, boolean expected) {
            assertEquals(expected, PluginApiVersion.isAtLeast(v1, v2));
        }
    }

    @Nested
    @DisplayName("isCompatible()")
    class IsCompatible {

        @Test
        @DisplayName("should be compatible with same major version and lower minor")
        void shouldBeCompatibleWithSameMajorVersionAndLowerMinor() {
            // Current version is 1.0.0, so 1.0.0 should be compatible
            assertTrue(PluginApiVersion.isCompatible("1.0.0"));
        }

        @Test
        @DisplayName("should not be compatible with different major version")
        void shouldNotBeCompatibleWithDifferentMajorVersion() {
            // Version 2.x.x is not compatible with current 1.x.x
            assertFalse(PluginApiVersion.isCompatible("2.0.0"));
            assertFalse(PluginApiVersion.isCompatible("0.1.0"));
        }

        @Test
        @DisplayName("should not be compatible with higher minor version")
        void shouldNotBeCompatibleWithHigherMinorVersion() {
            // If plugin requires 1.5.0 but current is 1.0.0, not compatible
            if (PluginApiVersion.MINOR < 5) {
                assertFalse(PluginApiVersion.isCompatible("1.5.0"));
            }
        }
    }

    @Nested
    @DisplayName("supportsPlugin()")
    class SupportsPlugin {

        @Test
        @DisplayName("should support plugin with exact minimum version")
        void shouldSupportPluginWithExactMinimumVersion() {
            assertTrue(PluginApiVersion.supportsPlugin(PluginApiVersion.MINIMUM_SUPPORTED));
        }

        @Test
        @DisplayName("should support plugin with current version requirement")
        void shouldSupportPluginWithCurrentVersionRequirement() {
            assertTrue(PluginApiVersion.supportsPlugin(PluginApiVersion.VERSION));
        }

        @Test
        @DisplayName("should not support plugin requiring future major version")
        void shouldNotSupportPluginRequiringFutureMajorVersion() {
            String futureVersion = (PluginApiVersion.MAJOR + 1) + ".0.0";
            assertFalse(PluginApiVersion.supportsPlugin(futureVersion));
        }
    }

    @Nested
    @DisplayName("getVersionInfo()")
    class GetVersionInfo {

        @Test
        @DisplayName("should return formatted version info")
        void shouldReturnFormattedVersionInfo() {
            String info = PluginApiVersion.getVersionInfo();

            assertNotNull(info);
            assertTrue(info.contains(PluginApiVersion.VERSION));
            assertTrue(info.contains(PluginApiVersion.MINIMUM_SUPPORTED));
            assertTrue(info.contains("Arcana Plugin API"));
        }

        @Test
        @DisplayName("should contain version number in expected format")
        void shouldContainVersionNumberInExpectedFormat() {
            String info = PluginApiVersion.getVersionInfo();

            assertTrue(info.matches(".*v\\d+\\.\\d+\\.\\d+.*"));
        }
    }
}
