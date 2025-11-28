# Kubernetes Deployment Guide

This guide covers deploying Arcana Cloud on Kubernetes with full orchestration support.

## Overview

Kubernetes deployment enables:
- Auto-scaling based on load
- Self-healing with health checks
- Rolling updates with zero downtime
- Service discovery via DNS
- ConfigMaps and Secrets management
- Horizontal Pod Autoscaling (HPA)

## Architecture

```
                    ┌─────────────────────────────────────────────────────────┐
                    │                    Kubernetes Cluster                    │
                    │                                                          │
                    │   ┌─────────────┐     ┌─────────────┐                   │
                    │   │   Ingress   │────►│  Controller │ (3 replicas)     │
                    │   │   (nginx)   │     │   Service   │                   │
                    │   └─────────────┘     └──────┬──────┘                   │
                    │                              │ gRPC                      │
                    │                              ▼                           │
                    │                        ┌─────────────┐                   │
                    │                        │   Service   │ (2 replicas)     │
                    │                        │   Layer     │                   │
                    │                        └──────┬──────┘                   │
                    │                              │ gRPC                      │
                    │                              ▼                           │
                    │                        ┌─────────────┐                   │
                    │                        │ Repository  │ (2 replicas)     │
                    │                        │   Layer     │                   │
                    │                        └──────┬──────┘                   │
                    │                              │                           │
                    │   ┌──────────────────────────┼──────────────────────┐   │
                    │   │                          │                       │   │
                    │   ▼                          ▼                       ▼   │
                    │ MySQL                      Redis              ConfigMap  │
                    │ (StatefulSet)           (StatefulSet)         Secrets    │
                    │                                                          │
                    └─────────────────────────────────────────────────────────┘
```

## Prerequisites

- Kubernetes 1.28+
- kubectl configured
- Helm 3.x (optional)
- Container registry access
- Persistent Volume provisioner

## Container Images

### Build Images

```bash
# Build application JAR
./gradlew clean bootJar

# Build Docker image
docker build -t arcana-cloud:1.0.0 .

# Tag for registry
docker tag arcana-cloud:1.0.0 your-registry/arcana-cloud:1.0.0

# Push to registry
docker push your-registry/arcana-cloud:1.0.0
```

### Multi-stage Dockerfile

```dockerfile
# Build stage
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src src
RUN ./gradlew bootJar -x test

# Runtime stage
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /app/build/libs/arcana-cloud-java-1.0.0-SNAPSHOT.jar app.jar

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
EXPOSE 8080 9090

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

## Kubernetes Manifests

### Namespace

```yaml
# k8s/namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: arcana
  labels:
    name: arcana
```

### ConfigMap

```yaml
# k8s/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: arcana-config
  namespace: arcana
data:
  DEPLOYMENT_MODE: "layered"
  COMMUNICATION_PROTOCOL: "grpc"
  SPRING_DATA_REDIS_HOST: "redis"
  SPRING_DATA_REDIS_PORT: "6379"
  CIRCUIT_BREAKER_FAILURE_RATE_THRESHOLD: "50"
  CIRCUIT_BREAKER_WAIT_DURATION_IN_OPEN_STATE_MS: "30000"
  LOGGING_LEVEL_ROOT: "INFO"
  LOGGING_LEVEL_COM_ARCANA_CLOUD: "DEBUG"
```

### Secrets

```yaml
# k8s/secrets.yaml
apiVersion: v1
kind: Secret
metadata:
  name: arcana-secrets
  namespace: arcana
type: Opaque
stringData:
  JWT_SECRET: "your-256-bit-secret-key-change-in-production"
  MYSQL_PASSWORD: "arcana_pass"
  MYSQL_ROOT_PASSWORD: "root_pass"
```

### Controller Deployment

```yaml
# k8s/controller-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: arcana-controller
  namespace: arcana
  labels:
    app: arcana
    layer: controller
spec:
  replicas: 3
  selector:
    matchLabels:
      app: arcana
      layer: controller
  template:
    metadata:
      labels:
        app: arcana
        layer: controller
    spec:
      containers:
      - name: controller
        image: your-registry/arcana-cloud:1.0.0
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: DEPLOYMENT_LAYER
          value: "controller"
        - name: SERVICE_GRPC_URL
          value: "arcana-service:9091"
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: arcana-secrets
              key: JWT_SECRET
        envFrom:
        - configMapRef:
            name: arcana-config
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
          failureThreshold: 3
---
apiVersion: v1
kind: Service
metadata:
  name: arcana-controller
  namespace: arcana
spec:
  selector:
    app: arcana
    layer: controller
  ports:
  - port: 8080
    targetPort: 8080
    name: http
  type: ClusterIP
```

### Service Layer Deployment

```yaml
# k8s/service-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: arcana-service
  namespace: arcana
  labels:
    app: arcana
    layer: service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: arcana
      layer: service
  template:
    metadata:
      labels:
        app: arcana
        layer: service
    spec:
      containers:
      - name: service
        image: your-registry/arcana-cloud:1.0.0
        ports:
        - containerPort: 8081
          name: http
        - containerPort: 9091
          name: grpc
        env:
        - name: DEPLOYMENT_LAYER
          value: "service"
        - name: SERVER_PORT
          value: "8081"
        - name: SPRING_GRPC_SERVER_PORT
          value: "9091"
        - name: REPOSITORY_GRPC_URL
          value: "arcana-repository:9092"
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: arcana-secrets
              key: JWT_SECRET
        envFrom:
        - configMapRef:
            name: arcana-config
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: arcana-service
  namespace: arcana
spec:
  selector:
    app: arcana
    layer: service
  ports:
  - port: 8081
    targetPort: 8081
    name: http
  - port: 9091
    targetPort: 9091
    name: grpc
  type: ClusterIP
```

### Repository Layer Deployment

```yaml
# k8s/repository-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: arcana-repository
  namespace: arcana
  labels:
    app: arcana
    layer: repository
spec:
  replicas: 2
  selector:
    matchLabels:
      app: arcana
      layer: repository
  template:
    metadata:
      labels:
        app: arcana
        layer: repository
    spec:
      containers:
      - name: repository
        image: your-registry/arcana-cloud:1.0.0
        ports:
        - containerPort: 8082
          name: http
        - containerPort: 9092
          name: grpc
        env:
        - name: DEPLOYMENT_LAYER
          value: "repository"
        - name: SERVER_PORT
          value: "8082"
        - name: SPRING_GRPC_SERVER_PORT
          value: "9092"
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:mysql://mysql:3306/arcana_cloud"
        - name: SPRING_DATASOURCE_USERNAME
          value: "arcana"
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: arcana-secrets
              key: MYSQL_PASSWORD
        envFrom:
        - configMapRef:
            name: arcana-config
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8082
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8082
          initialDelaySeconds: 30
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: arcana-repository
  namespace: arcana
spec:
  selector:
    app: arcana
    layer: repository
  ports:
  - port: 8082
    targetPort: 8082
    name: http
  - port: 9092
    targetPort: 9092
    name: grpc
  type: ClusterIP
```

### MySQL StatefulSet

```yaml
# k8s/mysql.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: mysql
  namespace: arcana
spec:
  serviceName: mysql
  replicas: 1
  selector:
    matchLabels:
      app: mysql
  template:
    metadata:
      labels:
        app: mysql
    spec:
      containers:
      - name: mysql
        image: mysql:8.0
        ports:
        - containerPort: 3306
        env:
        - name: MYSQL_DATABASE
          value: arcana_cloud
        - name: MYSQL_USER
          value: arcana
        - name: MYSQL_PASSWORD
          valueFrom:
            secretKeyRef:
              name: arcana-secrets
              key: MYSQL_PASSWORD
        - name: MYSQL_ROOT_PASSWORD
          valueFrom:
            secretKeyRef:
              name: arcana-secrets
              key: MYSQL_ROOT_PASSWORD
        volumeMounts:
        - name: mysql-data
          mountPath: /var/lib/mysql
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
  volumeClaimTemplates:
  - metadata:
      name: mysql-data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 10Gi
---
apiVersion: v1
kind: Service
metadata:
  name: mysql
  namespace: arcana
spec:
  selector:
    app: mysql
  ports:
  - port: 3306
  clusterIP: None
```

### Redis StatefulSet

```yaml
# k8s/redis.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: redis
  namespace: arcana
spec:
  serviceName: redis
  replicas: 1
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        ports:
        - containerPort: 6379
        volumeMounts:
        - name: redis-data
          mountPath: /data
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
          limits:
            memory: "256Mi"
            cpu: "200m"
  volumeClaimTemplates:
  - metadata:
      name: redis-data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 1Gi
---
apiVersion: v1
kind: Service
metadata:
  name: redis
  namespace: arcana
spec:
  selector:
    app: redis
  ports:
  - port: 6379
  clusterIP: None
```

### Ingress

```yaml
# k8s/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: arcana-ingress
  namespace: arcana
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - api.arcana.example.com
    secretName: arcana-tls
  rules:
  - host: api.arcana.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: arcana-controller
            port:
              number: 8080
```

### Horizontal Pod Autoscaler

```yaml
# k8s/hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: arcana-controller-hpa
  namespace: arcana
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: arcana-controller
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: arcana-service-hpa
  namespace: arcana
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: arcana-service
  minReplicas: 2
  maxReplicas: 5
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

## Deployment Commands

### Apply All Manifests

```bash
# Create namespace
kubectl apply -f k8s/namespace.yaml

# Apply configurations
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml

# Deploy infrastructure
kubectl apply -f k8s/mysql.yaml
kubectl apply -f k8s/redis.yaml

# Wait for infrastructure
kubectl wait --for=condition=ready pod -l app=mysql -n arcana --timeout=120s
kubectl wait --for=condition=ready pod -l app=redis -n arcana --timeout=60s

# Deploy application layers
kubectl apply -f k8s/repository-deployment.yaml
kubectl apply -f k8s/service-deployment.yaml
kubectl apply -f k8s/controller-deployment.yaml

# Apply autoscaling and ingress
kubectl apply -f k8s/hpa.yaml
kubectl apply -f k8s/ingress.yaml
```

### Verify Deployment

```bash
# Check pods
kubectl get pods -n arcana

# Check services
kubectl get svc -n arcana

# Check HPA
kubectl get hpa -n arcana

# View logs
kubectl logs -f deployment/arcana-controller -n arcana

# Describe pod for troubleshooting
kubectl describe pod -l app=arcana,layer=controller -n arcana
```

## Helm Chart (Optional)

For complex deployments, use the Helm chart in `helm/arcana-cloud/`:

```bash
# Install
helm install arcana ./helm/arcana-cloud -n arcana --create-namespace

# Upgrade
helm upgrade arcana ./helm/arcana-cloud -n arcana

# Uninstall
helm uninstall arcana -n arcana
```

## Monitoring

### Prometheus ServiceMonitor

```yaml
# k8s/servicemonitor.yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: arcana-monitor
  namespace: arcana
spec:
  selector:
    matchLabels:
      app: arcana
  endpoints:
  - port: http
    path: /actuator/prometheus
    interval: 30s
```

### Grafana Dashboard

Import the pre-built dashboard from `config/grafana/arcana-k8s-dashboard.json`.

## Troubleshooting

### Common Issues

**Pods not starting**
```bash
kubectl describe pod <pod-name> -n arcana
kubectl logs <pod-name> -n arcana --previous
```

**Database connection issues**
```bash
# Test MySQL connectivity
kubectl exec -it mysql-0 -n arcana -- mysql -u arcana -p
```

**gRPC connectivity issues**
```bash
# Test service discovery
kubectl exec -it deployment/arcana-controller -n arcana -- nslookup arcana-service
```

**Resource constraints**
```bash
kubectl top pods -n arcana
kubectl describe node
```

## Rolling Updates

```bash
# Update image
kubectl set image deployment/arcana-controller \
  controller=your-registry/arcana-cloud:1.1.0 -n arcana

# Watch rollout
kubectl rollout status deployment/arcana-controller -n arcana

# Rollback if needed
kubectl rollout undo deployment/arcana-controller -n arcana
```
