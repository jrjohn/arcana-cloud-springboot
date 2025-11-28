# Plugin API Versioning Guide

This guide explains the Plugin API versioning strategy and how to write compatible plugins.

## Overview

The Arcana Cloud Plugin API uses semantic versioning (SemVer) with additional annotations to help plugin developers understand API stability and compatibility.

## Version Format

```
MAJOR.MINOR.PATCH

Example: 1.2.3
- MAJOR = 1 (breaking changes)
- MINOR = 2 (new features, backwards compatible)
- PATCH = 3 (bug fixes)
```

## Versioning Annotations

### @Since

Indicates when an API element was introduced:

```java
@Since("1.0.0")
public interface Plugin {
    void onInstall(PluginContext context);

    @Since("1.2.0")
    default void onUpgrade(String previousVersion) {
        // Added in 1.2.0
    }
}
```

### @DeprecatedSince

Provides detailed deprecation information:

```java
@DeprecatedSince(
    version = "1.5.0",
    replacement = "newMethod()",
    removalVersion = "2.0.0",
    reason = "Performance improvements in newMethod()"
)
@Deprecated
void oldMethod();
```

### @ApiVersion

Marks API stability level:

```java
@ApiVersion(version = "1.0.0", stability = ApiVersion.Stability.STABLE)
public interface Plugin { }

@ApiVersion(version = "1.0.0", stability = ApiVersion.Stability.EXPERIMENTAL)
public interface SSRViewExtension { }
```

### @RequiresApiVersion

Declares plugin's API requirements:

```java
@RequiresApiVersion(
    minimum = "1.0.0",
    tested = "1.2.0"
)
public class MyPlugin implements Plugin { }
```

## Stability Levels

| Level | Description | Compatibility Guarantee |
|-------|-------------|------------------------|
| **STABLE** | Production-ready | Backwards compatible within major version |
| **EVOLVING** | Mostly stable | Minor changes possible in minor versions |
| **EXPERIMENTAL** | May change significantly | No compatibility guarantee |
| **INTERNAL** | Platform internal | Do not use in plugins |
| **DEPRECATED** | Will be removed | Use replacement |

## Compatibility Rules

### Major Version (X.0.0)
- Breaking changes allowed
- Plugins may need updates
- Migration guide provided

### Minor Version (0.X.0)
- New features added
- Backwards compatible
- Old plugins continue to work

### Patch Version (0.0.X)
- Bug fixes only
- No API changes
- Transparent updates

## Using PluginApiVersion

```java
import com.arcana.cloud.plugin.version.PluginApiVersion;

public class MyPlugin implements Plugin {

    @Override
    public void onEnable() {
        // Check API version at runtime
        if (PluginApiVersion.isCompatible("1.2.0")) {
            // Use features from 1.2.0
            useNewFeature();
        } else {
            // Fall back to older API
            useLegacyMethod();
        }

        // Get version info
        System.out.println(PluginApiVersion.getVersionInfo());
        // Output: Arcana Plugin API v1.0.0 (min supported: 1.0.0)
    }
}
```

## Version Comparison

```java
// Compare versions
int result = PluginApiVersion.compare("1.2.0", "1.1.0"); // > 0

// Check minimum version
if (PluginApiVersion.isAtLeast("1.2.0", "1.0.0")) {
    // Version is at least 1.0.0
}

// Parse version
int[] parts = PluginApiVersion.parseVersion("1.2.3");
// parts = [1, 2, 3]
```

## Plugin Manifest

Add API version to your plugin's OSGi manifest:

```properties
# MANIFEST.MF
Bundle-Name: My Plugin
Bundle-SymbolicName: com.example.myplugin
Bundle-Version: 1.0.0
Require-Capability: arcana.plugin.api;filter:="(&(version>=1.0.0)(!(version>=2.0.0)))"
Plugin-Api-Version: 1.0.0
```

## Best Practices

### 1. Declare Minimum Version

Always annotate your plugin class:

```java
@RequiresApiVersion(minimum = "1.0.0", tested = "1.2.0")
public class MyPlugin implements Plugin { }
```

### 2. Use Default Methods

For optional features, implement with defaults:

```java
@Override
public void onUpgrade(String previousVersion) {
    if (PluginApiVersion.isAtLeast(previousVersion, "1.1.0")) {
        migrateFrom1_1();
    }
}
```

### 3. Handle Missing Features Gracefully

```java
public void setupFeatures() {
    try {
        // Try new API
        context.getService(NewService.class);
    } catch (NoClassDefFoundError e) {
        // Fall back for older platforms
        useLegacyApproach();
    }
}
```

### 4. Document Version Requirements

```java
/**
 * Requires Plugin API 1.2.0+
 * @since 1.2.0
 */
public void newFeature() { }
```

## API Evolution Policy

### Adding New Features

1. Add to minor version (1.x.0)
2. Annotate with `@Since`
3. Provide default implementation when possible
4. Document in release notes

### Deprecating Features

1. Add `@Deprecated` and `@DeprecatedSince`
2. Provide migration path
3. Support for at least 2 minor versions
4. Document replacement

### Removing Features

1. Only in major versions (x.0.0)
2. Must be deprecated first
3. Provide migration guide
4. Update minimum supported version

## Compatibility Matrix

| Plugin Min Version | Platform 1.0.x | Platform 1.1.x | Platform 1.2.x | Platform 2.0.x |
|-------------------|----------------|----------------|----------------|----------------|
| 1.0.0 | Yes | Yes | Yes | No |
| 1.1.0 | No | Yes | Yes | No |
| 1.2.0 | No | No | Yes | No |
| 2.0.0 | No | No | No | Yes |

## Troubleshooting

### IncompatibleClassChangeError

Plugin was compiled against a different API version:
- Recompile plugin against correct API version
- Check `@RequiresApiVersion` matches platform

### NoSuchMethodError

Using method not available in platform version:
- Check `@Since` annotation on method
- Add version check before calling
- Update minimum version requirement

### ClassNotFoundException

Extension point not available:
- Check stability level (may be EXPERIMENTAL)
- Verify extension is exported from API module
- Update plugin dependencies
