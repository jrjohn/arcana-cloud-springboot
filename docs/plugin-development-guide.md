# Arcana Cloud Plugin Development Guide

## Overview

Arcana Cloud uses an OSGi-based plugin architecture inspired by Atlassian JIRA's plugin system. Plugins are deployed as OSGi bundles and can extend the platform through well-defined extension points.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Arcana Cloud Platform                        │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              Apache Felix OSGi Framework                   │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │  │
│  │  │ Plugin A    │  │ Plugin B    │  │ Plugin C    │       │  │
│  │  │ (Bundle)    │  │ (Bundle)    │  │ (Bundle)    │       │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘       │  │
│  │                                                           │  │
│  │  ┌─────────────────────────────────────────────────────┐ │  │
│  │  │           Spring-OSGi Bridge                        │ │  │
│  │  │  • Export Spring beans to OSGi                      │ │  │
│  │  │  • Import OSGi services to Spring                   │ │  │
│  │  └─────────────────────────────────────────────────────┘ │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              Platform Services (Spring Beans)              │  │
│  │  • UserService  • AuthService  • DataSource  • Cache      │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Quick Start

### 1. Create Plugin Structure

```bash
mkdir -p my-plugin/src/main/java/com/example/myplugin
mkdir -p my-plugin/src/main/resources/META-INF
mkdir -p my-plugin/src/main/resources/db/migration
```

### 2. Create build.gradle.kts

```kotlin
plugins {
    java
}

group = "com.example.plugin"
version = "1.0.0"

dependencies {
    compileOnly(project(":arcana-plugin-api"))
    compileOnly("org.osgi:org.osgi.core:8.0.0")
    compileOnly("org.slf4j:slf4j-api:2.0.9")
}

tasks.jar {
    manifest {
        attributes(
            "Bundle-ManifestVersion" to "2",
            "Bundle-SymbolicName" to "com.example.myplugin",
            "Bundle-Version" to project.version,
            "Bundle-Name" to "My Plugin",
            "Bundle-Activator" to "com.example.myplugin.Activator",
            "Arcana-Plugin-Key" to "com.example.myplugin",
            "Arcana-Plugin-Name" to "My Plugin",
            "Import-Package" to "com.arcana.cloud.plugin.api;version=\"[1.0,2.0)\",org.osgi.framework;version=\"[1.8,2.0)\""
        )
    }
}
```

### 3. Create Plugin Descriptor (arcana-plugin.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<arcana-plugin key="com.example.myplugin"
               name="My Plugin"
               version="1.0.0">

    <plugin-info>
        <description>My awesome plugin</description>
        <vendor name="Example Inc" url="https://example.com"/>
        <min-platform-version>1.0.0</min-platform-version>
    </plugin-info>

    <!-- Your extensions here -->

</arcana-plugin>
```

### 4. Create Bundle Activator

```java
package com.example.myplugin;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

    @Override
    public void start(BundleContext context) throws Exception {
        System.out.println("My Plugin started!");
        // Register services, initialize resources
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        System.out.println("My Plugin stopped!");
        // Cleanup resources
    }
}
```

### 5. Build and Deploy

```bash
./gradlew :plugins:my-plugin:jar
cp plugins/my-plugin/build/libs/my-plugin-1.0.0.jar plugins/
```

## Extension Points

### REST Endpoint Extension

Add custom REST API endpoints:

```java
@RestEndpointExtension(
    key = "my-api",
    path = "/api/v1/plugins/myapi",
    requiresPermission = "USER"
)
@RestController
@RequestMapping("/api/v1/plugins/myapi")
public class MyRestController {

    @GetMapping("/hello")
    public ResponseEntity<String> hello() {
        return ResponseEntity.ok("Hello from plugin!");
    }
}
```

**arcana-plugin.xml:**
```xml
<rest-extension key="my-api"
                path="/api/v1/plugins/myapi"
                class="com.example.myplugin.MyRestController">
    <requires-permission>USER</requires-permission>
</rest-extension>
```

### Service Extension

Add business logic services:

```java
// Service interface
public interface NotificationService {
    void sendNotification(Long userId, String message);
}

// Implementation
@ServiceExtension(
    key = "email-notification",
    serviceInterface = NotificationService.class
)
public class EmailNotificationService implements NotificationService {

    @Override
    public void sendNotification(Long userId, String message) {
        // Send email
    }
}
```

**arcana-plugin.xml:**
```xml
<service-extension key="email-notification"
                   interface="com.example.NotificationService"
                   class="com.example.EmailNotificationService">
    <property name="priority" value="100"/>
</service-extension>
```

### Event Listener Extension

Listen to platform events:

```java
@EventListenerExtension(
    key = "user-listener",
    events = {UserCreatedEvent.class, UserLoginEvent.class}
)
public class UserEventListener {

    public void handle(PluginEvent event) {
        if (event instanceof UserCreatedEvent userEvent) {
            log.info("User created: {}", userEvent.getUsername());
        }
    }
}
```

**arcana-plugin.xml:**
```xml
<event-listener key="user-listener"
                class="com.example.UserEventListener">
    <event>com.arcana.cloud.plugin.event.UserCreatedEvent</event>
    <event>com.arcana.cloud.plugin.event.UserLoginEvent</event>
</event-listener>
```

### Scheduled Job Extension

Run periodic background tasks:

```java
@ScheduledJobExtension(
    key = "cleanup-job",
    cron = "0 0 3 * * ?",  // 3 AM daily
    description = "Cleanup old data"
)
public class CleanupJob {

    public void execute() {
        log.info("Running cleanup...");
        // Cleanup logic
    }
}
```

**arcana-plugin.xml:**
```xml
<scheduled-job key="cleanup-job"
               class="com.example.CleanupJob"
               cron="0 0 3 * * ?">
    <description>Cleanup old data</description>
</scheduled-job>
```

### SSR View Extension

Add server-side rendered pages:

```java
@SSRViewExtension(
    key = "my-dashboard",
    path = "/plugins/myplugin/dashboard",
    framework = SSRFramework.REACT,
    entry = "Dashboard.tsx"
)
public class MyDashboardView implements SSRView {

    @Override
    public Map<String, Object> getInitialProps(SSRContext context) {
        return Map.of(
            "userId", context.getCurrentUserId(),
            "data", myService.getData()
        );
    }
}
```

## Accessing Platform Services

### From OSGi Bundle Activator

```java
@Override
public void start(BundleContext context) throws Exception {
    // Get DataSource
    ServiceReference<DataSource> dsRef = context.getServiceReference(DataSource.class);
    DataSource dataSource = context.getService(dsRef);

    // Get custom services
    ServiceReference<UserService> userRef = context.getServiceReference(UserService.class);
    UserService userService = context.getService(userRef);
}
```

### Using Plugin Context

```java
public class MyPlugin implements Plugin {
    private PluginContext context;

    @Override
    public void onInstall(PluginContext context) {
        this.context = context;
    }

    @Override
    public void onEnable() {
        // Get platform service
        UserService userService = context.requireService(UserService.class);

        // Get configuration
        String apiKey = context.getProperty("api.key", "default");

        // Publish event
        context.publishEvent(new MyCustomEvent());
    }
}
```

## Database Migrations

Plugins can have their own Flyway migrations:

**arcana-plugin.xml:**
```xml
<database-migration>
    <location>db/migration</location>
    <table-prefix>plugin_myplugin_</table-prefix>
</database-migration>
```

**db/migration/V1000__initial_schema.sql:**
```sql
CREATE TABLE IF NOT EXISTS plugin_myplugin_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    value TEXT
);
```

## Web Resources

Include CSS and JavaScript:

**arcana-plugin.xml:**
```xml
<web-resource key="my-resources">
    <resource type="css" location="/web/css/my-plugin.css"/>
    <resource type="javascript" location="/web/js/my-plugin.js"/>
</web-resource>
```

## Plugin Lifecycle

```
UNINSTALLED → INSTALLED → RESOLVED → STARTING → ACTIVE
                              ↑                    ↓
                              └── STOPPING ←───────┘
```

### Lifecycle Callbacks

```java
public class MyPlugin implements Plugin {

    @Override
    public void onInstall(PluginContext context) {
        // Called when plugin is first installed
    }

    @Override
    public void onEnable() {
        // Called when plugin is started/enabled
    }

    @Override
    public void onDisable() {
        // Called when plugin is stopped/disabled
    }

    @Override
    public void onUninstall() {
        // Called when plugin is uninstalled
    }

    @Override
    public void onUpgrade(String previousVersion) {
        // Called when plugin is upgraded
    }
}
```

## Best Practices

### 1. Resource Management
- Always unregister services in `stop()` method
- Close database connections and file handles
- Cancel scheduled tasks

### 2. Error Handling
- Log errors appropriately
- Don't let exceptions crash the plugin framework
- Provide fallback behavior when services unavailable

### 3. Security
- Validate all user input
- Use `requiresPermission` for protected endpoints
- Don't expose sensitive information

### 4. Performance
- Use lazy initialization
- Cache expensive computations
- Avoid blocking operations in event handlers

### 5. Testing
- Unit test your plugin components
- Test plugin lifecycle (start/stop cycles)
- Test with missing optional services

## Example: Complete Plugin

See the `arcana-audit-plugin` for a complete example that demonstrates:
- REST endpoints
- Service extensions
- Event listeners
- Scheduled jobs
- Database migrations
- Web resources
- Proper lifecycle management

## Configuration

Plugin properties in `application.properties`:

```properties
# Enable/disable plugin system
arcana.plugin.enabled=true

# Plugins directory
arcana.plugin.plugins-dir=plugins

# Platform version
arcana.plugin.platform-version=1.0.0

# Hot deployment
arcana.plugin.hot-deploy=true
arcana.plugin.hot-deploy-poll-interval=2000
```

## Troubleshooting

### Plugin Won't Start
1. Check the OSGi console for bundle state
2. Verify Import-Package matches available packages
3. Check for missing dependencies

### Services Not Found
1. Ensure services are registered before lookup
2. Check service interface versions
3. Verify Spring-OSGi bridge is configured

### Migrations Not Running
1. Check migration file naming (V####__)
2. Verify database-migration element in XML
3. Check Flyway logs for errors
