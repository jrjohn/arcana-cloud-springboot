package com.arcana.cloud.plugin.version;

/**
 * Central class containing Plugin API version constants.
 *
 * <p>This class provides the current API version and utility methods
 * for version comparison. Plugin developers can use this to check
 * API compatibility at runtime.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * if (PluginApiVersion.isCompatible("1.2.0")) {
 *     // Use features from 1.2.0
 * } else {
 *     // Fall back to older API
 * }
 * }</pre>
 */
@ApiVersion(version = PluginApiVersion.VERSION, stability = ApiVersion.Stability.STABLE)
public final class PluginApiVersion {

    /**
     * Current Plugin API version.
     * Format: MAJOR.MINOR.PATCH following semantic versioning.
     */
    public static final String VERSION = "1.0.0";

    /**
     * Major version number (breaking changes).
     */
    public static final int MAJOR = 1;

    /**
     * Minor version number (new features, backwards compatible).
     */
    public static final int MINOR = 0;

    /**
     * Patch version number (bug fixes).
     */
    public static final int PATCH = 0;

    /**
     * Minimum supported plugin API version.
     * Plugins requiring older versions may not be compatible.
     */
    public static final String MINIMUM_SUPPORTED = "1.0.0";

    private PluginApiVersion() {
        // Utility class
    }

    /**
     * Checks if the current API version is compatible with the required version.
     * Compatibility follows semantic versioning rules:
     * - Major versions must match
     * - Current minor must be >= required minor
     *
     * @param requiredVersion the version required by the plugin
     * @return true if compatible
     */
    public static boolean isCompatible(String requiredVersion) {
        int[] required = parseVersion(requiredVersion);
        return required[0] == MAJOR && required[1] <= MINOR;
    }

    /**
     * Checks if a plugin's minimum API version is supported.
     *
     * @param pluginMinVersion the minimum version the plugin requires
     * @return true if the plugin's minimum version is supported
     */
    public static boolean supportsPlugin(String pluginMinVersion) {
        int[] required = parseVersion(pluginMinVersion);
        int[] minimum = parseVersion(MINIMUM_SUPPORTED);

        if (required[0] < minimum[0]) return false;
        if (required[0] > MAJOR) return false;
        if (required[0] == minimum[0] && required[1] < minimum[1]) return false;
        return required[0] != MAJOR || required[1] <= MINOR;
    }

    /**
     * Compares two version strings.
     *
     * @param v1 first version
     * @param v2 second version
     * @return negative if v1 < v2, positive if v1 > v2, zero if equal
     */
    public static int compare(String v1, String v2) {
        int[] parsed1 = parseVersion(v1);
        int[] parsed2 = parseVersion(v2);

        for (int i = 0; i < 3; i++) {
            int diff = parsed1[i] - parsed2[i];
            if (diff != 0) return diff;
        }
        return 0;
    }

    /**
     * Checks if version1 is greater than or equal to version2.
     *
     * @param version1 first version to compare
     * @param version2 second version to compare
     * @return true if version1 >= version2
     */
    public static boolean isAtLeast(String version1, String version2) {
        return compare(version1, version2) >= 0;
    }

    /**
     * Parses a version string into major, minor, patch components.
     *
     * @param version the version string (e.g., "1.2.3")
     * @return array of [major, minor, patch]
     * @throws IllegalArgumentException if version format is invalid
     */
    public static int[] parseVersion(String version) {
        if (version == null || version.isEmpty()) {
            throw new IllegalArgumentException("Version cannot be null or empty");
        }

        String[] parts = version.split("\\.");
        int[] result = new int[3];

        try {
            result[0] = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
            result[1] = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            result[2] = parts.length > 2 ? Integer.parseInt(parts[2].split("-")[0]) : 0;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid version format: " + version, e);
        }

        return result;
    }

    /**
     * Returns a formatted version string with the current API version.
     *
     * @return formatted version info
     */
    public static String getVersionInfo() {
        return String.format("Arcana Plugin API v%s (min supported: %s)", VERSION, MINIMUM_SUPPORTED);
    }
}
