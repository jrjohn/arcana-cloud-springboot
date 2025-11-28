# Arcana Cloud Spring Boot

[![Java](https://img.shields.io/badge/Java-25-ED8B00.svg?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-6DB33F.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![gRPC](https://img.shields.io/badge/gRPC-1.60-00ADD8.svg)](https://grpc.io/)
[![Tests](https://img.shields.io/badge/tests-297%2F297_passing-brightgreen.svg)](docs/test-report/index.html)

Enterprise cloud platform with dual-protocol architecture (gRPC/REST), OSGi plugin system, and Server-Side Rendering.

## Features

- **Dual Protocol** - gRPC (2.5x faster) and HTTP REST
- **Plugin System** - OSGi-based hot-deployable extensions
- **SSR Engine** - React and Angular support via GraalJS
- **5 Deployment Modes** - Monolithic, Layered, Kubernetes

## Quick Start

### Prerequisites

- Java 25
- Gradle 9.2.1+
- Docker & Docker Compose
- MySQL 8.0+ / Redis 7.0+

### Setup

```bash
# Clone and configure
git clone https://github.com/jrjohn/arcana-cloud-springboot.git
cd arcana-cloud-springboot
cp .env.example .env

# Build
./gradlew build

# Run (monolithic mode)
./scripts/start-docker-monolithic.sh
```

### Access Points

| Service | URL |
|---------|-----|
| REST API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| gRPC Server | localhost:9090 |

## Deployment Modes

| Mode | Protocol | Use Case |
|------|----------|----------|
| Monolithic | Internal | Development, small deployments |
| Layered + HTTP | REST | Simple multi-tier architecture |
| Layered + gRPC | gRPC | High-performance multi-tier |
| K8s + HTTP | REST | Cloud-native deployments |
| K8s + gRPC | gRPC + TLS | Production, maximum performance |

## API Endpoints

### Authentication

```
POST /api/v1/auth/register  - Register user
POST /api/v1/auth/login     - Login
POST /api/v1/auth/refresh   - Refresh token
POST /api/v1/auth/logout    - Logout
```

### Plugins

```
GET    /api/v1/plugins           - List plugins
POST   /api/v1/plugins/install   - Install plugin
POST   /api/v1/plugins/{key}/enable  - Enable plugin
DELETE /api/v1/plugins/{key}     - Uninstall plugin
```

## Configuration

Key environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `DEPLOYMENT_MODE` | `monolithic` or `layered` | `monolithic` |
| `COMMUNICATION_PROTOCOL` | `http` or `grpc` | `grpc` |
| `DATABASE_URL` | MySQL connection URL | `jdbc:mysql://localhost:3306/arcana_cloud` |
| `REDIS_HOST` | Redis host | `localhost` |
| `JWT_SECRET` | JWT signing secret | - |

See `application.properties` for full configuration options.

## Testing

```bash
# Run all tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport

# View reports
open build/reports/tests/test/index.html
open build/reports/jacoco/test/html/index.html
```

**Test Results:** 297 tests passing (137 unit + 160 integration)

## Project Structure

```
arcana-cloud-springboot/
├── src/main/java/           # Main application
├── arcana-plugin-api/       # Plugin SDK
├── arcana-plugin-runtime/   # OSGi runtime
├── arcana-ssr-engine/       # SSR engine
├── plugins/                 # Plugin bundles
├── arcana-web/              # React/Angular apps
├── deployment/              # Docker & K8s configs
└── docs/                    # Documentation
```

## Documentation

- [Test Report](docs/test-report/index.html) - HTML test results
- [Coverage Report](docs/jacoco-report/index.html) - Code coverage
- [Testing Guide](docs/TESTING.md) - Testing documentation
- [Plugin Guide](docs/plugin-development-guide.md) - Plugin development

## License

MIT License

---

**Built with:** Spring Boot 4.0 | Java 25 | Gradle 9.2.1 | Apache Felix OSGi | gRPC | GraalJS
