# Layered HTTP Deployment Guide

This guide covers deploying Arcana Cloud as separate microservices communicating via REST/HTTP.

## Overview

In layered HTTP mode, the application is split into separate deployable units:
- **Controller Layer**: Handles HTTP requests, authentication, API endpoints
- **Service Layer**: Business logic, validation, orchestration
- **Repository Layer**: Data access, caching, database operations

Communication between layers uses REST APIs.

## Architecture

```
┌─────────────────┐     HTTP      ┌─────────────────┐     HTTP      ┌─────────────────┐
│  Controller     │ ──────────►  │    Service      │ ──────────►  │   Repository    │
│  Layer (8080)   │              │  Layer (8081)   │              │  Layer (8082)   │
└─────────────────┘              └─────────────────┘              └─────────────────┘
```

## Prerequisites

- Java 25 or higher
- MySQL 8.0+ (for Repository layer)
- Redis 7.0+ (shared or per-layer)
- Load balancer (for production)

## Configuration

### Controller Layer (Port 8080)

```properties
# application-controller.properties
spring.application.name=arcana-controller
server.port=8080

deployment.mode=layered
deployment.layer=controller
communication.protocol=rest

# Service layer URL
service.rest.url=http://localhost:8081

# JWT (must match across layers)
jwt.secret=your-256-bit-secret-key
jwt.expiration=3600000

# Redis (for session/cache)
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Disable direct database access
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
```

### Service Layer (Port 8081)

```properties
# application-service.properties
spring.application.name=arcana-service
server.port=8081

deployment.mode=layered
deployment.layer=service
communication.protocol=rest

# Repository layer URL
repository.rest.url=http://localhost:8082

# JWT (for validation)
jwt.secret=your-256-bit-secret-key

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### Repository Layer (Port 8082)

```properties
# application-repository.properties
spring.application.name=arcana-repository
server.port=8082

deployment.mode=layered
deployment.layer=repository
communication.protocol=rest

# Database
spring.datasource.url=jdbc:mysql://localhost:3306/arcana_cloud
spring.datasource.username=arcana
spring.datasource.password=your_secure_password

# JPA
spring.jpa.hibernate.ddl-auto=none

# Flyway
spring.flyway.enabled=true

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

## Build

```bash
# Build all modules
./gradlew clean build -x test

# Create deployable JARs
./gradlew bootJar
```

## Run

### Start Each Layer

```bash
# Terminal 1: Repository Layer
java -jar build/libs/arcana-cloud-java-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=repository \
  --server.port=8082

# Terminal 2: Service Layer
java -jar build/libs/arcana-cloud-java-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=service \
  --server.port=8081

# Terminal 3: Controller Layer
java -jar build/libs/arcana-cloud-java-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=controller \
  --server.port=8080
```

### Docker Compose

```yaml
version: '3.8'

services:
  controller:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=controller
      - DEPLOYMENT_MODE=layered
      - DEPLOYMENT_LAYER=controller
      - COMMUNICATION_PROTOCOL=rest
      - SERVICE_REST_URL=http://service:8081
      - SPRING_DATA_REDIS_HOST=redis
      - JWT_SECRET=${JWT_SECRET}
    depends_on:
      - service
      - redis
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  service:
    build: .
    ports:
      - "8081:8081"
    environment:
      - SPRING_PROFILES_ACTIVE=service
      - SERVER_PORT=8081
      - DEPLOYMENT_MODE=layered
      - DEPLOYMENT_LAYER=service
      - COMMUNICATION_PROTOCOL=rest
      - REPOSITORY_REST_URL=http://repository:8082
      - SPRING_DATA_REDIS_HOST=redis
      - JWT_SECRET=${JWT_SECRET}
    depends_on:
      - repository
      - redis
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  repository:
    build: .
    ports:
      - "8082:8082"
    environment:
      - SPRING_PROFILES_ACTIVE=repository
      - SERVER_PORT=8082
      - DEPLOYMENT_MODE=layered
      - DEPLOYMENT_LAYER=repository
      - COMMUNICATION_PROTOCOL=rest
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/arcana_cloud
      - SPRING_DATASOURCE_USERNAME=arcana
      - SPRING_DATASOURCE_PASSWORD=arcana_pass
      - SPRING_DATA_REDIS_HOST=redis
    depends_on:
      - mysql
      - redis
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8082/actuator/health"]
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

  redis:
    image: redis:7-alpine
    volumes:
      - redis_data:/data

volumes:
  mysql_data:
  redis_data:
```

## Scaling

### Horizontal Scaling

Each layer can be scaled independently:

```yaml
# docker-compose.override.yml
services:
  controller:
    deploy:
      replicas: 3

  service:
    deploy:
      replicas: 2

  repository:
    deploy:
      replicas: 2
```

### Load Balancing

Use nginx or HAProxy for load balancing:

```nginx
# nginx.conf
upstream controller_servers {
    server controller1:8080;
    server controller2:8080;
    server controller3:8080;
}

upstream service_servers {
    server service1:8081;
    server service2:8081;
}

server {
    listen 80;

    location / {
        proxy_pass http://controller_servers;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## Service Discovery

### Using Eureka (Spring Cloud)

Add to each layer:

```properties
# application.properties
eureka.client.service-url.defaultZone=http://eureka:8761/eureka/
eureka.instance.prefer-ip-address=true
```

### Using Consul

```properties
spring.cloud.consul.host=localhost
spring.cloud.consul.port=8500
spring.cloud.consul.discovery.enabled=true
```

## Health Checks

### Layer-Specific Endpoints

| Layer | Endpoint | Port |
|-------|----------|------|
| Controller | `/actuator/health` | 8080 |
| Service | `/actuator/health` | 8081 |
| Repository | `/actuator/health` | 8082 |

### Aggregate Health Check Script

```bash
#!/bin/bash
# check-health.sh

CONTROLLER_HEALTH=$(curl -s http://localhost:8080/actuator/health | jq -r '.status')
SERVICE_HEALTH=$(curl -s http://localhost:8081/actuator/health | jq -r '.status')
REPOSITORY_HEALTH=$(curl -s http://localhost:8082/actuator/health | jq -r '.status')

echo "Controller: $CONTROLLER_HEALTH"
echo "Service: $SERVICE_HEALTH"
echo "Repository: $REPOSITORY_HEALTH"

if [ "$CONTROLLER_HEALTH" = "UP" ] && [ "$SERVICE_HEALTH" = "UP" ] && [ "$REPOSITORY_HEALTH" = "UP" ]; then
    echo "All layers healthy"
    exit 0
else
    echo "One or more layers unhealthy"
    exit 1
fi
```

## Retry and Timeout Configuration

### RestTemplate/WebClient Settings

```properties
# HTTP client timeouts
spring.http.client.connect-timeout=5000
spring.http.client.read-timeout=30000

# Retry configuration
spring.retry.max-attempts=3
spring.retry.backoff.initial-interval=1000
spring.retry.backoff.multiplier=2
spring.retry.backoff.max-interval=10000
```

## Logging

### Distributed Tracing

Enable correlation IDs across layers:

```properties
# application.properties
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} [%thread] [%X{traceId}] %-5level %logger{36} - %msg%n
```

### Centralized Logging (ELK Stack)

```yaml
# docker-compose additions
  elasticsearch:
    image: elasticsearch:8.11.0
    environment:
      - discovery.type=single-node

  logstash:
    image: logstash:8.11.0
    volumes:
      - ./logstash.conf:/usr/share/logstash/pipeline/logstash.conf

  kibana:
    image: kibana:8.11.0
    ports:
      - "5601:5601"
```

## Troubleshooting

### Common Issues

**Service Communication Failure**
```
Connection refused to service layer
```
- Verify service layer is running: `curl http://localhost:8081/actuator/health`
- Check network connectivity between containers
- Verify service URLs in configuration

**Inconsistent JWT Validation**
```
Invalid JWT signature
```
- Ensure `jwt.secret` is identical across all layers
- Check JWT expiration times

**Database Migrations Not Running**
```
Flyway migration error
```
- Only Repository layer should run migrations
- Set `spring.flyway.enabled=false` on other layers

**High Latency Between Layers**
- Enable connection pooling
- Consider switching to gRPC mode
- Add caching layer

## Performance Considerations

- **Latency**: Each HTTP call adds ~1-5ms overhead
- **Throughput**: Connection pooling is essential
- **Caching**: Use Redis to reduce inter-layer calls
- **Consider gRPC**: For high-throughput scenarios, use gRPC mode

## Security

- Use HTTPS between layers in production
- Implement mutual TLS for service-to-service auth
- Use network policies to restrict layer communication
- Don't expose internal layer ports publicly
