# Spring Cloud Config Integration Guide

This guide covers setting up centralized configuration management using Spring Cloud Config.

## Overview

Spring Cloud Config provides server-side and client-side support for externalized configuration in a distributed system. Benefits include:

- Centralized configuration management
- Environment-specific configurations
- Runtime configuration refresh
- Encrypted secrets support
- Git-backed version control

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Configuration Sources                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │
│  │  Git Repo   │  │   Vault     │  │  Native File System     │ │
│  └──────┬──────┘  └──────┬──────┘  └───────────┬─────────────┘ │
│         │                │                      │               │
│         └────────────────┼──────────────────────┘               │
│                          │                                       │
│                          ▼                                       │
│              ┌───────────────────────┐                          │
│              │  Config Server :8888  │                          │
│              └───────────┬───────────┘                          │
│                          │                                       │
│         ┌────────────────┼────────────────┐                     │
│         ▼                ▼                ▼                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │ Controller  │  │   Service   │  │ Repository  │             │
│  │   :8080     │  │   :8081     │  │   :8082     │             │
│  └─────────────┘  └─────────────┘  └─────────────┘             │
└─────────────────────────────────────────────────────────────────┘
```

## Setup Config Server

### Option 1: Standalone Config Server

Create a new Spring Boot application:

```gradle
// build.gradle.kts
plugins {
    java
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.7"
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-config-server")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2024.0.0")
    }
}
```

Main application:

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

Configuration:

```yaml
# application.yml
server:
  port: 8888

spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/your-org/config-repo
          default-label: main
          search-paths: '{application}'
```

### Option 2: Native File System

For development or simple deployments:

```yaml
# application.yml
server:
  port: 8888

spring:
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          search-locations: file:///path/to/config
```

### Option 3: Docker Compose

```yaml
# docker-compose.yml
services:
  config-server:
    image: springcloud/spring-cloud-config-server
    ports:
      - "8888:8888"
    environment:
      - SPRING_CLOUD_CONFIG_SERVER_GIT_URI=https://github.com/your-org/config-repo
    volumes:
      - ./config:/config
```

## Client Configuration

### Enable in Application

The Arcana Cloud application already has Spring Cloud Config client configured. To enable:

```properties
# application.properties
spring.cloud.config.enabled=true
spring.cloud.config.uri=http://localhost:8888
spring.config.import=configserver:
```

### Profile-Based Configuration

The config server looks for files in this order:
1. `{application}-{profile}.yml`
2. `{application}.yml`
3. `application-{profile}.yml`
4. `application.yml`

For Arcana Cloud:
- `arcana-cloud.yml` - Base configuration
- `arcana-cloud-development.yml` - Development overrides
- `arcana-cloud-production.yml` - Production overrides
- `arcana-cloud-controller.yml` - Controller layer specific
- `arcana-cloud-service.yml` - Service layer specific
- `arcana-cloud-repository.yml` - Repository layer specific

### Connecting to Config Server

```bash
# Start with specific profile
java -jar arcana-cloud.jar \
  --spring.cloud.config.enabled=true \
  --spring.cloud.config.uri=http://config-server:8888 \
  --spring.profiles.active=production,controller
```

## Configuration Repository Structure

```
config-repo/
├── arcana-cloud.yml                    # Base config
├── arcana-cloud-development.yml        # Dev environment
├── arcana-cloud-production.yml         # Prod environment
├── arcana-cloud-controller.yml         # Controller layer
├── arcana-cloud-service.yml            # Service layer
├── arcana-cloud-repository.yml         # Repository layer
└── arcana-cloud-k8s.yml               # Kubernetes specific
```

## Runtime Configuration Refresh

### Enable Refresh Endpoint

```properties
management.endpoints.web.exposure.include=refresh,health,info
```

### Trigger Refresh

```bash
# Refresh single instance
curl -X POST http://localhost:8080/actuator/refresh

# Response shows changed properties
["jwt.expiration", "circuit-breaker.failure-rate-threshold"]
```

### Using @RefreshScope

Mark beans that should be recreated on refresh:

```java
@Service
@RefreshScope
public class DynamicConfigService {

    @Value("${circuit-breaker.failure-rate-threshold}")
    private float failureThreshold;

    // This bean will be recreated when config changes
}
```

## Encrypting Secrets

### Setup Encryption

```yaml
# config-server application.yml
encrypt:
  key: ${ENCRYPT_KEY}  # or use keystore
```

### Encrypt Values

```bash
# Encrypt a value
curl -X POST http://localhost:8888/encrypt -d "my-secret-password"
# Returns: AQA...encrypted...

# Use in config files
spring:
  datasource:
    password: '{cipher}AQA...encrypted...'
```

### Using Vault Backend

For production secrets management:

```yaml
spring:
  cloud:
    config:
      server:
        vault:
          host: vault.example.com
          port: 8200
          scheme: https
          authentication: TOKEN
          token: ${VAULT_TOKEN}
```

## Health Monitoring

### Config Server Health

```bash
curl http://localhost:8888/actuator/health
```

### Client Health

```bash
# Check if client is connected to config server
curl http://localhost:8080/actuator/health

# Response includes config server status
{
  "status": "UP",
  "components": {
    "configServer": {
      "status": "UP",
      "details": {
        "propertySources": [
          "arcana-cloud-production.yml",
          "arcana-cloud.yml"
        ]
      }
    }
  }
}
```

## Kubernetes Integration

### ConfigMap for Config Server

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: config-server-config
  namespace: arcana
data:
  application.yml: |
    server:
      port: 8888
    spring:
      cloud:
        config:
          server:
            git:
              uri: ${GIT_REPO_URI}
              username: ${GIT_USERNAME}
              password: ${GIT_PASSWORD}
```

### Config Server Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: config-server
  namespace: arcana
spec:
  replicas: 2
  selector:
    matchLabels:
      app: config-server
  template:
    metadata:
      labels:
        app: config-server
    spec:
      containers:
      - name: config-server
        image: your-registry/config-server:1.0.0
        ports:
        - containerPort: 8888
        env:
        - name: GIT_REPO_URI
          valueFrom:
            secretKeyRef:
              name: config-secrets
              key: git-uri
        - name: ENCRYPT_KEY
          valueFrom:
            secretKeyRef:
              name: config-secrets
              key: encrypt-key
---
apiVersion: v1
kind: Service
metadata:
  name: config-server
  namespace: arcana
spec:
  selector:
    app: config-server
  ports:
  - port: 8888
```

### Client Configuration for K8s

```yaml
# arcana-cloud-k8s.yml
spring:
  cloud:
    config:
      uri: http://config-server:8888
      fail-fast: true
      retry:
        initial-interval: 2000
        max-attempts: 10
```

## Best Practices

### 1. Configuration Organization

```
config-repo/
├── application.yml           # Shared defaults
├── arcana-cloud/
│   ├── arcana-cloud.yml     # App defaults
│   ├── development.yml
│   ├── staging.yml
│   └── production.yml
└── shared/
    ├── database.yml
    └── redis.yml
```

### 2. Secret Management

- Never store plain text secrets in Git
- Use Vault or encrypted values
- Rotate encryption keys regularly
- Use different keys per environment

### 3. High Availability

- Run multiple config server instances
- Use load balancer
- Enable client retry
- Configure fail-fast appropriately

### 4. Version Control

- Tag configurations with release versions
- Use branches for environments
- Implement change review process
- Maintain configuration changelog

## Troubleshooting

### Config Not Loading

```bash
# Check config server response
curl http://localhost:8888/arcana-cloud/production

# Verify client bootstrap
java -jar app.jar --debug 2>&1 | grep -i config
```

### Refresh Not Working

- Verify `@RefreshScope` on beans
- Check actuator endpoint is exposed
- Ensure `spring-boot-starter-actuator` is included

### Encryption Issues

```bash
# Test encryption
curl -X POST http://localhost:8888/encrypt -d "test"

# Test decryption
curl -X POST http://localhost:8888/decrypt -d "{cipher}..."
```

## Migration Path

1. **Phase 1**: Deploy config server with current properties
2. **Phase 2**: Enable clients one service at a time
3. **Phase 3**: Move sensitive configs to Vault
4. **Phase 4**: Enable runtime refresh
5. **Phase 5**: Implement GitOps workflow
