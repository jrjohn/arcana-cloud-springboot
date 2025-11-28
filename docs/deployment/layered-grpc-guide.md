# Layered gRPC Deployment Guide

This guide covers deploying Arcana Cloud as separate microservices communicating via gRPC.

## Overview

In layered gRPC mode, the application uses Protocol Buffers and gRPC for high-performance inter-service communication:
- **Controller Layer**: REST API endpoints, gRPC client
- **Service Layer**: gRPC server + client, business logic
- **Repository Layer**: gRPC server, data access

## Architecture

```
┌─────────────────┐     gRPC      ┌─────────────────┐     gRPC      ┌─────────────────┐
│  Controller     │ ──────────►  │    Service      │ ──────────►  │   Repository    │
│  (8080/9090)    │   :9090      │  (8081/9091)    │   :9091      │  (8082/9092)    │
└─────────────────┘              └─────────────────┘              └─────────────────┘
     REST API                        gRPC Server                     gRPC Server
     gRPC Client                     gRPC Client                     Data Access
```

## Benefits of gRPC Mode

| Feature | gRPC | REST |
|---------|------|------|
| Serialization | Binary (Protobuf) | Text (JSON) |
| Performance | ~10x faster | Baseline |
| Streaming | Bi-directional | Limited |
| Type Safety | Strong (proto) | Weak |
| Code Generation | Automatic | Manual |
| Circuit Breaker | Resilience4j | Built-in |

## Prerequisites

- Java 25 or higher
- MySQL 8.0+ (for Repository layer)
- Redis 7.0+
- Protocol Buffers compiler (bundled with Gradle)

## Protocol Buffer Definitions

The `.proto` files are located in `src/main/proto/`:

```protobuf
// user_service.proto
syntax = "proto3";

package com.arcana.cloud.grpc;

service UserService {
  rpc CreateUser (CreateUserRequest) returns (UserResponse);
  rpc GetUser (GetUserRequest) returns (UserResponse);
  rpc UpdateUser (UpdateUserRequest) returns (UserResponse);
  rpc DeleteUser (DeleteUserRequest) returns (Empty);
  rpc ListUsers (ListUsersRequest) returns (ListUsersResponse);
}
```

## Configuration

### Controller Layer (REST: 8080, gRPC Client)

```properties
# application-controller-grpc.properties
spring.application.name=arcana-controller
server.port=8080

deployment.mode=layered
deployment.layer=controller
communication.protocol=grpc

# gRPC Service URL (Service Layer)
service.grpc.url=localhost:9091

# gRPC Client Settings
grpc.client.tls.enabled=false
grpc.client.keepalive.time=30
grpc.client.keepalive.timeout=10
grpc.client.deadline-ms=30000
grpc.client.retry.max-attempts=3

# Circuit Breaker (enabled automatically for gRPC mode)
circuit-breaker.failure-rate-threshold=50
circuit-breaker.wait-duration-in-open-state-ms=30000

# JWT
jwt.secret=your-256-bit-secret-key
jwt.expiration=3600000

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### Service Layer (gRPC Server: 9091, gRPC Client)

```properties
# application-service-grpc.properties
spring.application.name=arcana-service
server.port=8081

deployment.mode=layered
deployment.layer=service
communication.protocol=grpc

# gRPC Server Port
spring.grpc.server.port=9091

# Repository Layer gRPC URL
repository.grpc.url=localhost:9092

# Circuit Breaker
circuit-breaker.failure-rate-threshold=50

# JWT
jwt.secret=your-256-bit-secret-key

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### Repository Layer (gRPC Server: 9092)

```properties
# application-repository-grpc.properties
spring.application.name=arcana-repository
server.port=8082

deployment.mode=layered
deployment.layer=repository
communication.protocol=grpc

# gRPC Server Port
spring.grpc.server.port=9092

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
# Generate gRPC code from proto files
./gradlew generateProto

# Build all
./gradlew clean build -x test

# Verify generated code
ls -la build/generated/source/proto/main/
```

## Run

### Start Each Layer

```bash
# Terminal 1: Repository Layer (gRPC Server: 9092)
java -jar build/libs/arcana-cloud-java-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=repository-grpc \
  --deployment.layer=repository \
  --communication.protocol=grpc \
  --spring.grpc.server.port=9092 \
  --server.port=8082

# Terminal 2: Service Layer (gRPC Server: 9091, Client to Repository)
java -jar build/libs/arcana-cloud-java-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=service-grpc \
  --deployment.layer=service \
  --communication.protocol=grpc \
  --spring.grpc.server.port=9091 \
  --repository.grpc.url=localhost:9092 \
  --server.port=8081

# Terminal 3: Controller Layer (REST: 8080, gRPC Client to Service)
java -jar build/libs/arcana-cloud-java-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=controller-grpc \
  --deployment.layer=controller \
  --communication.protocol=grpc \
  --service.grpc.url=localhost:9091 \
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
      - SPRING_PROFILES_ACTIVE=controller-grpc
      - DEPLOYMENT_MODE=layered
      - DEPLOYMENT_LAYER=controller
      - COMMUNICATION_PROTOCOL=grpc
      - SERVICE_GRPC_URL=service:9091
      - SPRING_DATA_REDIS_HOST=redis
      - JWT_SECRET=${JWT_SECRET}
      - CIRCUIT_BREAKER_FAILURE_RATE_THRESHOLD=50
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
      - "9091:9091"
    environment:
      - SPRING_PROFILES_ACTIVE=service-grpc
      - SERVER_PORT=8081
      - SPRING_GRPC_SERVER_PORT=9091
      - DEPLOYMENT_MODE=layered
      - DEPLOYMENT_LAYER=service
      - COMMUNICATION_PROTOCOL=grpc
      - REPOSITORY_GRPC_URL=repository:9092
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
      - "9092:9092"
    environment:
      - SPRING_PROFILES_ACTIVE=repository-grpc
      - SERVER_PORT=8082
      - SPRING_GRPC_SERVER_PORT=9092
      - DEPLOYMENT_MODE=layered
      - DEPLOYMENT_LAYER=repository
      - COMMUNICATION_PROTOCOL=grpc
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

## Circuit Breaker

### Configuration

The Circuit Breaker is automatically enabled in gRPC mode:

```properties
# Circuit Breaker Settings
circuit-breaker.failure-rate-threshold=50      # Open after 50% failures
circuit-breaker.slow-call-rate-threshold=80    # Open after 80% slow calls
circuit-breaker.slow-call-duration-threshold-ms=5000
circuit-breaker.wait-duration-in-open-state-ms=30000
circuit-breaker.permitted-calls-in-half-open-state=5
circuit-breaker.sliding-window-size=10
circuit-breaker.minimum-number-of-calls=5
```

### Circuit States

| State | Behavior |
|-------|----------|
| CLOSED | Normal operation |
| OPEN | Fail fast, return fallback |
| HALF_OPEN | Test recovery |

### Monitoring Circuit Breaker

```bash
# Check circuit breaker metrics
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state

# View detailed state
curl http://localhost:8080/actuator/circuitbreakers
```

## gRPC Testing

### Using grpcurl

Install grpcurl:
```bash
# macOS
brew install grpcurl

# Linux
go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest
```

Test gRPC endpoints:
```bash
# List services
grpcurl -plaintext localhost:9091 list

# Describe service
grpcurl -plaintext localhost:9091 describe com.arcana.cloud.grpc.UserService

# Call method
grpcurl -plaintext -d '{"userId": 1}' localhost:9091 com.arcana.cloud.grpc.UserService/GetUser
```

### Using BloomRPC

BloomRPC provides a GUI for gRPC testing:
1. Download from https://github.com/bloomrpc/bloomrpc
2. Import proto files from `src/main/proto/`
3. Connect to `localhost:9091`

## TLS Configuration

### Enable TLS

```properties
# application.properties
grpc.client.tls.enabled=true
grpc.client.tls.trust-cert-path=/path/to/ca.crt
grpc.client.tls.client-cert-path=/path/to/client.crt
grpc.client.tls.client-key-path=/path/to/client.key
```

### Generate Certificates

```bash
# Generate CA
openssl genrsa -out ca.key 4096
openssl req -new -x509 -days 365 -key ca.key -out ca.crt

# Generate Server Certificate
openssl genrsa -out server.key 4096
openssl req -new -key server.key -out server.csr
openssl x509 -req -days 365 -in server.csr -CA ca.crt -CAkey ca.key -out server.crt

# Generate Client Certificate
openssl genrsa -out client.key 4096
openssl req -new -key client.key -out client.csr
openssl x509 -req -days 365 -in client.csr -CA ca.crt -CAkey ca.key -out client.crt
```

## Performance Tuning

### gRPC Channel Settings

```properties
# Keepalive
grpc.client.keepalive.time=30           # Seconds between pings
grpc.client.keepalive.timeout=10        # Wait for ping response

# Message Size
grpc.client.max-inbound-message-size=16777216  # 16MB

# Deadline
grpc.client.deadline-ms=30000           # Request timeout
```

### Connection Pooling

gRPC uses HTTP/2 multiplexing, so fewer connections are needed:

```properties
# Recommended: 1-2 channels per service
# Each channel can handle multiple concurrent requests
```

## Troubleshooting

### Common Issues

**gRPC Connection Refused**
```
StatusRuntimeException: UNAVAILABLE: io exception
```
- Verify gRPC server is running on expected port
- Check firewall rules for gRPC ports (9091, 9092)
- Ensure correct `service.grpc.url` configuration

**Circuit Breaker Open**
```
CallNotPermittedException: CircuitBreaker 'userService' is OPEN
```
- Check downstream service health
- Wait for `wait-duration-in-open-state-ms` to elapse
- Monitor `/actuator/circuitbreakers` for state

**Proto Serialization Error**
```
InvalidProtocolBufferException
```
- Regenerate proto classes: `./gradlew generateProto`
- Ensure proto definitions match between client and server
- Check proto file versions

**Deadline Exceeded**
```
StatusRuntimeException: DEADLINE_EXCEEDED
```
- Increase `grpc.client.deadline-ms`
- Check for slow database queries
- Monitor service latency

## Monitoring

### Metrics

gRPC metrics available at `/actuator/prometheus`:

```
grpc_server_calls_total
grpc_server_calls_duration_seconds
grpc_client_calls_total
resilience4j_circuitbreaker_state
```

### Logging gRPC Calls

```properties
# Enable gRPC debug logging
logging.level.io.grpc=DEBUG
logging.level.com.arcana.cloud.service.client=DEBUG
```

## Migration from HTTP to gRPC

1. Deploy Repository layer with gRPC server
2. Deploy Service layer with gRPC server + client
3. Deploy Controller layer with gRPC client
4. Update configuration: `communication.protocol=grpc`
5. Verify circuit breaker is functioning
6. Remove HTTP endpoints (optional)
