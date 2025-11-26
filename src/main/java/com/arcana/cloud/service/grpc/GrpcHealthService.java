package com.arcana.cloud.service.grpc;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.grpc.server.service.GrpcService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * gRPC Health Check Service implementation.
 *
 * <p>Implements the standard gRPC health checking protocol for Kubernetes probes.
 * This allows K8s to properly check gRPC server health in addition to HTTP endpoints.</p>
 *
 * @see <a href="https://github.com/grpc/grpc/blob/master/doc/health-checking.md">gRPC Health Checking Protocol</a>
 */
@GrpcService
@ConditionalOnExpression("'${deployment.layer:}' == '' or '${deployment.layer:}' == 'service'")
public class GrpcHealthService extends HealthGrpc.HealthImplBase {

    private static final Logger log = LoggerFactory.getLogger(GrpcHealthService.class);

    private final Map<String, HealthCheckResponse.ServingStatus> serviceStatuses;
    private volatile boolean pluginsReady = false;

    public GrpcHealthService() {
        this.serviceStatuses = new ConcurrentHashMap<>();
        // Set default status for empty service (overall server health)
        serviceStatuses.put("", HealthCheckResponse.ServingStatus.SERVING);
        // Set default statuses for known services
        serviceStatuses.put("grpc.health.v1.Health", HealthCheckResponse.ServingStatus.SERVING);
        serviceStatuses.put("arcana.cloud.AuthService", HealthCheckResponse.ServingStatus.SERVING);
        serviceStatuses.put("arcana.cloud.UserService", HealthCheckResponse.ServingStatus.SERVING);
        log.info("gRPC Health Service initialized");
    }

    @Override
    public void check(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
        String serviceName = request.getService();
        log.debug("Health check requested for service: '{}'", serviceName.isEmpty() ? "(overall)" : serviceName);

        HealthCheckResponse.ServingStatus status = serviceStatuses.getOrDefault(
            serviceName,
            HealthCheckResponse.ServingStatus.SERVICE_UNKNOWN
        );

        // If checking overall health, also verify plugins are ready
        if (serviceName.isEmpty() && !pluginsReady) {
            status = HealthCheckResponse.ServingStatus.NOT_SERVING;
        }

        HealthCheckResponse response = HealthCheckResponse.newBuilder()
            .setStatus(status)
            .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void watch(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
        // For simplicity, we'll just return the current status
        // In production, this could be enhanced to stream status changes
        String serviceName = request.getService();
        HealthCheckResponse.ServingStatus status = serviceStatuses.getOrDefault(
            serviceName,
            HealthCheckResponse.ServingStatus.SERVICE_UNKNOWN
        );

        HealthCheckResponse response = HealthCheckResponse.newBuilder()
            .setStatus(status)
            .build();

        responseObserver.onNext(response);
        // Keep the stream open for watching (don't complete immediately)
    }

    /**
     * Sets the serving status for a service.
     *
     * @param serviceName the service name (empty string for overall health)
     * @param status the serving status
     */
    public void setStatus(String serviceName, HealthCheckResponse.ServingStatus status) {
        serviceStatuses.put(serviceName, status);
        log.info("Service '{}' status changed to {}",
            serviceName.isEmpty() ? "(overall)" : serviceName, status);
    }

    /**
     * Marks a service as serving.
     *
     * @param serviceName the service name
     */
    public void setServing(String serviceName) {
        setStatus(serviceName, HealthCheckResponse.ServingStatus.SERVING);
    }

    /**
     * Marks a service as not serving.
     *
     * @param serviceName the service name
     */
    public void setNotServing(String serviceName) {
        setStatus(serviceName, HealthCheckResponse.ServingStatus.NOT_SERVING);
    }

    /**
     * Marks plugins as ready.
     * Called by PluginManager when all plugins are initialized.
     */
    public void setPluginsReady(boolean ready) {
        this.pluginsReady = ready;
        if (ready) {
            setStatus("arcana.cloud.PluginSystem", HealthCheckResponse.ServingStatus.SERVING);
        } else {
            setStatus("arcana.cloud.PluginSystem", HealthCheckResponse.ServingStatus.NOT_SERVING);
        }
        log.info("Plugin system ready status: {}", ready);
    }

    /**
     * Returns whether plugins are ready.
     *
     * @return true if plugins are initialized
     */
    public boolean isPluginsReady() {
        return pluginsReady;
    }

    /**
     * Clears all custom statuses and resets to default.
     */
    public void clearStatuses() {
        serviceStatuses.clear();
        serviceStatuses.put("", HealthCheckResponse.ServingStatus.SERVING);
        pluginsReady = false;
    }
}
