# Testing Documentation

This document provides comprehensive documentation for the test suite of the Arcana Cloud Spring Boot application, including unit tests and integration tests for the plugin system across all deployment modes.

## Table of Contents

- [Overview](#overview)
- [Test Architecture](#test-architecture)
- [Unit Tests](#unit-tests)
- [Integration Tests](#integration-tests)
- [Running Tests](#running-tests)
- [Test Coverage](#test-coverage)
- [Deployment Mode Testing](#deployment-mode-testing)

## Overview

The test suite is designed to validate the plugin system functionality across all 5 supported deployment modes:

| Mode | Description | Communication |
|------|-------------|---------------|
| **Monolithic** | Single JVM deployment | Direct method calls |
| **Layered HTTP** | Multi-tier with HTTP | REST API |
| **Layered gRPC** | Multi-tier with gRPC | Protocol Buffers |
| **K8s HTTP** | Kubernetes with HTTP | REST API + Service Discovery |
| **K8s gRPC** | Kubernetes with gRPC | Protocol Buffers + Service Discovery |

## Test Architecture

```
src/test/java/com/arcana/cloud/
├── controller/
│   ├── PluginControllerTest.java          # REST API unit tests
│   └── PluginProxyControllerTest.java     # Proxy controller unit tests
└── integration/plugin/
    ├── PluginMonolithicModeTest.java      # Monolithic mode integration
    ├── PluginLayeredHttpModeTest.java     # Layered HTTP integration
    ├── PluginLayeredGrpcModeTest.java     # Layered gRPC integration
    ├── PluginK8sHttpModeTest.java         # K8s HTTP integration
    └── PluginK8sGrpcModeTest.java         # K8s gRPC integration

arcana-plugin-runtime/src/test/java/com/arcana/cloud/plugin/
├── runtime/
│   ├── PluginManagerTest.java             # Plugin manager unit tests
│   ├── ExtensionRegistryTest.java         # Extension registry tests
│   └── DistributedPluginRegistryTest.java # Distributed registry tests
└── http/
    ├── HttpPluginRegistryClientTest.java  # HTTP client tests
    └── PluginHttpClientTest.java          # Plugin HTTP client tests
```

## Unit Tests

### Plugin Runtime Module (`arcana-plugin-runtime`)

#### PluginManagerTest

Tests the core plugin management functionality:

| Test Class | Description |
|------------|-------------|
| `InitializationTests` | Plugin system initialization behavior |
| `PluginStateTests` | Plugin state transitions and validation |
| `LifecycleListenerTests` | Lifecycle event listener management |
| `ReadinessListenerTests` | Readiness notification handling |
| `PluginAccessorTests` | Plugin retrieval and querying |
| `WaitForInitializationTests` | Initialization timeout handling |
| `ShutdownTests` | Graceful shutdown behavior |

**Key Test Scenarios:**
- Initialization when plugin system is disabled
- State transitions (INSTALLED → ACTIVE → RESOLVED → UNINSTALLED)
- Exception handling for uninitialized operations
- Listener registration and removal
- Extension point management

#### ExtensionRegistryTest

Tests the extension point registration and discovery:

| Test Class | Description |
|------------|-------------|
| `RegistrationTests` | Extension point registration |
| `RetrievalTests` | Extension retrieval by type |
| `ClearTests` | Registry cleanup operations |

**Key Test Scenarios:**
- Registering extensions for specific plugins
- Retrieving extensions by interface type
- Handling non-existent plugins
- Clearing all extensions

#### DistributedPluginRegistryTest

Tests Redis-based distributed plugin state management:

| Test Class | Description |
|------------|-------------|
| `RegistrationTests` | Plugin registration in distributed registry |
| `StateManagementTests` | State synchronization across nodes |
| `HeartbeatTests` | Plugin heartbeat and health monitoring |
| `CleanupTests` | Stale plugin cleanup |

**Key Test Scenarios:**
- Plugin registration with metadata
- State updates and retrieval
- Heartbeat tracking with TTL
- Cleanup of unresponsive plugins

#### HttpPluginRegistryClientTest

Tests HTTP-based plugin registry communication:

| Test Class | Description |
|------------|-------------|
| `RegistrationTests` | HTTP registration requests |
| `StateTests` | State query via HTTP |
| `ErrorHandlingTests` | Network error handling |

**Key Test Scenarios:**
- Successful plugin registration via HTTP
- State retrieval with proper headers
- Graceful handling of connection failures
- Retry logic for transient errors

#### PluginHttpClientTest

Tests plugin-to-plugin HTTP communication:

| Test Class | Description |
|------------|-------------|
| `InvocationTests` | Plugin endpoint invocation |
| `TimeoutTests` | Request timeout handling |
| `ErrorTests` | Error response handling |

**Key Test Scenarios:**
- Invoking plugin REST endpoints
- Handling slow responses with timeouts
- Processing error responses (4xx, 5xx)
- Request/response serialization

### Controller Tests (`src/test`)

#### PluginControllerTest

Tests the plugin management REST API:

| Test Class | Description |
|------------|-------------|
| `ListPluginsTests` | GET /api/v1/plugins |
| `GetPluginTests` | GET /api/v1/plugins/{key} |
| `InstallPluginTests` | POST /api/v1/plugins/install |
| `EnablePluginTests` | POST /api/v1/plugins/{key}/enable |
| `DisablePluginTests` | POST /api/v1/plugins/{key}/disable |
| `UninstallPluginTests` | DELETE /api/v1/plugins/{key} |
| `HealthCheckTests` | GET /api/v1/plugins/health |
| `ReadinessCheckTests` | GET /api/v1/plugins/health/ready |
| `LivenessCheckTests` | GET /api/v1/plugins/health/live |
| `PluginWorkflowTests` | Complete lifecycle workflow |

**Key Test Scenarios:**
- CRUD operations for plugins
- File upload validation (JAR files only)
- Plugin state transitions
- Health endpoint responses
- Complete plugin lifecycle workflow

#### PluginProxyControllerTest

Tests the plugin proxy functionality for layered deployments:

| Test Class | Description |
|------------|-------------|
| `GetProxyTests` | GET request proxying |
| `PostProxyTests` | POST request proxying |
| `PutProxyTests` | PUT request proxying |
| `PatchProxyTests` | PATCH request proxying |
| `DeleteProxyTests` | DELETE request proxying |
| `HeaderHandlingTests` | Header forwarding and filtering |
| `UrlPathExtractionTests` | URL path manipulation |
| `ResponseHandlingTests` | Response status and header handling |

**Key Test Scenarios:**
- Proxying all HTTP methods
- Query string preservation
- Header forwarding (excluding hop-by-hop)
- X-Forwarded-* header injection
- Error handling (BAD_GATEWAY on failures)

## Integration Tests

### Monolithic Mode (`PluginMonolithicModeTest`)

Tests plugin system in single-JVM deployment:

| Test Class | Description |
|------------|-------------|
| `InstallationTests` | Plugin installation via REST |
| `LifecycleTests` | Enable/disable/uninstall flow |
| `HealthCheckTests` | Health endpoint validation |
| `ErrorHandlingTests` | Error response validation |
| `WorkflowTests` | Complete lifecycle workflow |
| `MultiplePluginTests` | Managing multiple plugins |

**Configuration:**
- Profile: `test`
- Direct method calls between layers
- Local plugin storage
- No distributed state

### Layered HTTP Mode (`PluginLayeredHttpModeTest`)

Tests plugin system with HTTP-based layer communication:

| Test Class | Description |
|------------|-------------|
| `LayerCommunicationTests` | HTTP calls between layers |
| `PluginOperationsTests` | Plugin CRUD via HTTP |
| `HealthPropagationTests` | Health status propagation |
| `ErrorPropagationTests` | Error handling across layers |

**Configuration:**
- Uses MockWebServer for service layer simulation
- Tests X-Forwarded headers
- Validates request/response transformation

### Layered gRPC Mode (`PluginLayeredGrpcModeTest`)

Tests plugin system with gRPC-based layer communication:

| Test Class | Description |
|------------|-------------|
| `GrpcCommunicationTests` | gRPC service calls |
| `PluginOperationsTests` | Plugin CRUD via gRPC |
| `StreamingTests` | Bidirectional streaming |
| `ErrorHandlingTests` | gRPC error propagation |

**Configuration:**
- Uses gRPC test utilities
- Protocol Buffer serialization
- Deadline/timeout handling

### Kubernetes HTTP Mode (`PluginK8sHttpModeTest`)

Tests plugin system in Kubernetes with HTTP:

| Test Class | Description |
|------------|-------------|
| `ServiceDiscoveryTests` | K8s service discovery |
| `LoadBalancingTests` | Request distribution |
| `HealthProbeTests` | Liveness/readiness probes |
| `PluginOperationsTests` | Plugin management |
| `FailoverTests` | Pod failure handling |

**Configuration:**
- Simulates Kubernetes DNS resolution
- Tests pod-to-pod communication
- Validates health probe endpoints

### Kubernetes gRPC Mode (`PluginK8sGrpcModeTest`)

Tests plugin system in Kubernetes with gRPC:

| Test Class | Description |
|------------|-------------|
| `GrpcServiceDiscoveryTests` | gRPC service resolution |
| `GrpcLoadBalancingTests` | Client-side load balancing |
| `GrpcHealthCheckTests` | gRPC health protocol |
| `PluginOperationsTests` | Plugin management via gRPC |

**Configuration:**
- gRPC health checking protocol
- Name resolver for K8s services
- Connection pooling

## Running Tests

### All Tests

```bash
./gradlew test
```

### Unit Tests Only

```bash
# Main module unit tests
./gradlew :test --tests "com.arcana.cloud.controller.*"

# Plugin runtime module tests
./gradlew :arcana-plugin-runtime:test
```

### Integration Tests Only

```bash
./gradlew :test --tests "com.arcana.cloud.integration.*"
```

### Specific Deployment Mode Tests

```bash
# Monolithic mode
./gradlew :test --tests "*MonolithicModeTest"

# Layered HTTP mode
./gradlew :test --tests "*LayeredHttpModeTest"

# Layered gRPC mode
./gradlew :test --tests "*LayeredGrpcModeTest"

# Kubernetes HTTP mode
./gradlew :test --tests "*K8sHttpModeTest"

# Kubernetes gRPC mode
./gradlew :test --tests "*K8sGrpcModeTest"
```

### With Test Reports

```bash
./gradlew test jacocoTestReport
```

Reports are generated at:
- Test results: `build/reports/tests/test/index.html`
- Coverage: `build/reports/jacoco/test/html/index.html`

## Test Coverage

### Coverage Goals

| Module | Line Coverage | Branch Coverage |
|--------|--------------|-----------------|
| `arcana-plugin-api` | 80% | 70% |
| `arcana-plugin-runtime` | 75% | 65% |
| Main application | 70% | 60% |

### Generating Coverage Reports

```bash
./gradlew test jacocoTestReport
```

### Viewing Coverage

Open `build/reports/jacoco/test/html/index.html` in a browser.

## Deployment Mode Testing

### Test Profile Configuration

Each deployment mode uses specific test profiles:

| Mode | Profile | Properties |
|------|---------|------------|
| Monolithic | `test` | Default test configuration |
| Layered HTTP | `test,layered-http` | HTTP service URLs |
| Layered gRPC | `test,layered-grpc` | gRPC channel config |
| K8s HTTP | `test,k8s-http` | K8s service names |
| K8s gRPC | `test,k8s-grpc` | K8s gRPC config |

### Mock Infrastructure

Tests use the following mock infrastructure:

- **MockMvc**: Spring MVC test support
- **MockWebServer**: OkHttp mock HTTP server
- **Testcontainers**: Docker-based Redis for distributed tests
- **H2 Database**: In-memory database for JPA tests
- **Mockito**: Mocking framework for unit tests

### Test Annotations

Common test annotations used:

```java
@SpringBootTest                    // Full application context
@AutoConfigureMockMvc              // MockMvc auto-configuration
@ActiveProfiles("test")            // Test profile activation
@ExtendWith(MockitoExtension.class) // Mockito support
@Nested                            // Nested test classes
@DisplayName("...")                // Readable test names
@Order(n)                          // Test execution order
@TempDir                           // Temporary directory injection
```

## Test Data Management

### Test Fixtures

Test data is managed through:

1. **Direct Registration**: `pluginController.registerPlugin(...)`
2. **Mock Files**: `MockMultipartFile` for JAR uploads
3. **JSON Fixtures**: Embedded JSON for API responses

### State Reset

Each test class resets state in `@BeforeEach`:

```java
@BeforeEach
void setUp() {
    pluginController.setPluginsInitialized(false);
    // Additional cleanup...
}
```

## Troubleshooting

### Common Issues

1. **Context Loading Failures**
   - Ensure `@ActiveProfiles("test")` is set
   - Check application-test.yml exists

2. **Mock Server Connection Issues**
   - Verify MockWebServer is started in `@BeforeEach`
   - Check port availability

3. **gRPC Test Failures**
   - Ensure protobuf compilation completed
   - Check gRPC dependencies are available

4. **Database Test Failures**
   - H2 should be in test runtime dependencies
   - Check JPA entity mappings

### Debug Mode

Run tests with debug output:

```bash
./gradlew test --info --stacktrace
```

### Specific Test Debugging

```bash
./gradlew test --tests "PluginManagerTest.InitializationTests" --debug
```
