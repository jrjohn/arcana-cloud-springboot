# Monolithic Deployment Guide

This guide covers deploying Arcana Cloud as a single monolithic application.

## Overview

In monolithic mode, all layers (Controller, Service, Repository) run within a single JVM process. This is the simplest deployment option, ideal for:
- Development environments
- Small to medium workloads
- Single-server deployments
- Quick prototyping

## Prerequisites

- Java 25 or higher
- MySQL 8.0+
- Redis 7.0+
- Gradle 9.2.1+

## Configuration

### application.properties

```properties
# Monolithic Mode (default)
deployment.mode=monolithic
# deployment.layer should be empty or not set
communication.protocol=rest

# Database
spring.datasource.url=jdbc:mysql://localhost:3306/arcana_cloud
spring.datasource.username=arcana
spring.datasource.password=your_secure_password

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# JWT (generate a secure 256-bit key)
jwt.secret=your-256-bit-secret-key-must-be-at-least-32-characters
jwt.expiration=3600000
jwt.refresh.expiration=2592000000
```

### Environment Variables (Alternative)

```bash
export SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/arcana_cloud
export SPRING_DATASOURCE_USERNAME=arcana
export SPRING_DATASOURCE_PASSWORD=your_secure_password
export SPRING_DATA_REDIS_HOST=localhost
export JWT_SECRET=your-256-bit-secret-key
```

## Build

```bash
# Clean build
./gradlew clean build -x test

# Build with tests
./gradlew clean build

# Build specific artifact
./gradlew bootJar
```

## Run

### Direct JAR Execution

```bash
java -jar build/libs/arcana-cloud-java-1.0.0-SNAPSHOT.jar
```

### With JVM Options

```bash
java \
  -Xms512m \
  -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -jar build/libs/arcana-cloud-java-1.0.0-SNAPSHOT.jar
```

### Docker Deployment

```dockerfile
FROM eclipse-temurin:25-jre

WORKDIR /app
COPY build/libs/arcana-cloud-java-1.0.0-SNAPSHOT.jar app.jar

ENV JAVA_OPTS="-Xms512m -Xmx2g"

EXPOSE 8080 9090

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

Build and run:

```bash
docker build -t arcana-cloud:monolithic .
docker run -d \
  --name arcana-cloud \
  -p 8080:8080 \
  -p 9090:9090 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/arcana_cloud \
  -e SPRING_DATA_REDIS_HOST=host.docker.internal \
  arcana-cloud:monolithic
```

### Docker Compose

```yaml
version: '3.8'

services:
  app:
    build: .
    ports:
      - "8080:8080"
      - "9090:9090"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/arcana_cloud
      - SPRING_DATASOURCE_USERNAME=arcana
      - SPRING_DATASOURCE_PASSWORD=arcana_pass
      - SPRING_DATA_REDIS_HOST=redis
    depends_on:
      - mysql
      - redis
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  mysql:
    image: mysql:8.0
    environment:
      - MYSQL_DATABASE=arcana_cloud
      - MYSQL_USER=arcana
      - MYSQL_PASSWORD=arcana_pass
      - MYSQL_ROOT_PASSWORD=root_pass
    volumes:
      - mysql_data:/var/lib/mysql
    ports:
      - "3306:3306"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

volumes:
  mysql_data:
  redis_data:
```

## Health Checks

### Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Overall application health |
| `/actuator/health/liveness` | Kubernetes liveness probe |
| `/actuator/health/readiness` | Kubernetes readiness probe |
| `/api/v1/plugins/health` | Plugin system health |

### Verification Commands

```bash
# Overall health
curl http://localhost:8080/actuator/health

# Detailed health
curl http://localhost:8080/actuator/health | jq

# Plugin system
curl http://localhost:8080/api/v1/plugins/readiness
```

## Logging

### Log Configuration

```properties
# application.properties
logging.level.root=INFO
logging.level.com.arcana.cloud=DEBUG
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n

# File logging (optional)
logging.file.name=/var/log/arcana/application.log
logging.file.max-size=100MB
logging.file.max-history=30
```

### Log Levels by Component

| Logger | Recommended Level |
|--------|-------------------|
| `com.arcana.cloud` | DEBUG (dev), INFO (prod) |
| `org.springframework` | WARN |
| `org.hibernate.SQL` | DEBUG (for query logging) |

## Monitoring

### Prometheus Metrics

Metrics available at `/actuator/prometheus`:

```bash
curl http://localhost:8080/actuator/prometheus
```

Key metrics:
- `jvm_memory_used_bytes`
- `http_server_requests_seconds`
- `hikaricp_connections_active`
- `spring_security_authentication_attempts`

### Grafana Dashboard

Import the provided dashboard from `config/grafana/arcana-dashboard.json`.

## Troubleshooting

### Common Issues

**Database Connection Failed**
```
HikariPool-1 - Connection is not available
```
- Verify MySQL is running: `mysql -u arcana -p -h localhost`
- Check connection URL and credentials
- Increase pool size if needed

**Redis Connection Failed**
```
Unable to connect to Redis
```
- Verify Redis is running: `redis-cli ping`
- Check Redis host and port configuration

**Plugin System Not Starting**
```
Plugin system initialization failed
```
- Check plugins directory exists: `plugins/`
- Verify OSGi cache: `rm -rf plugins/.cache/*`
- Check plugin JAR signatures if security is enabled

**Memory Issues**
```
java.lang.OutOfMemoryError: Java heap space
```
- Increase heap: `-Xmx4g`
- Enable heap dumps: `-XX:+HeapDumpOnOutOfMemoryError`

## Performance Tuning

### JVM Options

```bash
# Production JVM settings
java \
  -Xms2g \
  -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UseStringDeduplication \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/arcana/ \
  -jar app.jar
```

### Connection Pool Tuning

```properties
# HikariCP
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
```

## Security Checklist

- [ ] Change default JWT secret
- [ ] Use HTTPS in production
- [ ] Configure CORS appropriately
- [ ] Enable plugin signature verification
- [ ] Secure actuator endpoints
- [ ] Use secrets management (Vault, AWS Secrets Manager)
