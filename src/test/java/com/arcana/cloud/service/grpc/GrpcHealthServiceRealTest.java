package com.arcana.cloud.service.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Real gRPC integration tests for GrpcHealthService.
 *
 * <p>Uses {@link InProcessServerBuilder} to start a real gRPC health service.
 * Health check requests travel through actual protobuf serialization and
 * gRPC transport — the same wire protocol used by Kubernetes liveness probes.</p>
 *
 * <p>This validates that:</p>
 * <ul>
 *   <li>Health proto definitions compile and serialize correctly</li>
 *   <li>SERVING / NOT_SERVING status is transmitted faithfully over the wire</li>
 *   <li>Per-service health status routing works end-to-end</li>
 * </ul>
 */
@DisplayName("GrpcHealthService — Real In-Process gRPC Protocol Tests")
class GrpcHealthServiceRealTest {

    private GrpcHealthService healthService;
    private Server grpcServer;
    private ManagedChannel channel;
    private HealthGrpc.HealthBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception {
        healthService = new GrpcHealthService();

        String serverName = InProcessServerBuilder.generateName();
        grpcServer = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(healthService)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        stub = HealthGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws Exception {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        grpcServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("overall health: plugins not ready → NOT_SERVING serialized over wire")
    void overallHealth_pluginsNotReady_notServing() {
        // Default: plugins not ready
        HealthCheckResponse resp = stub.check(
                HealthCheckRequest.newBuilder().setService("").build()
        );

        assertEquals(HealthCheckResponse.ServingStatus.NOT_SERVING, resp.getStatus());
    }

    @Test
    @DisplayName("overall health: plugins ready → SERVING serialized over wire")
    void overallHealth_pluginsReady_serving() {
        healthService.setPluginsReady(true);

        HealthCheckResponse resp = stub.check(
                HealthCheckRequest.newBuilder().setService("").build()
        );

        assertEquals(HealthCheckResponse.ServingStatus.SERVING, resp.getStatus());
    }

    @Test
    @DisplayName("per-service health: auth-service → SERVING serialized over wire")
    void authService_serving_realWire() {
        healthService.setPluginsReady(true);

        HealthCheckResponse resp = stub.check(
                HealthCheckRequest.newBuilder().setService("auth-service").build()
        );

        assertEquals(HealthCheckResponse.ServingStatus.SERVING, resp.getStatus());
    }

    @Test
    @DisplayName("per-service health: user-service → SERVING serialized over wire")
    void userService_serving_realWire() {
        healthService.setPluginsReady(true);

        HealthCheckResponse resp = stub.check(
                HealthCheckRequest.newBuilder().setService("user-service").build()
        );

        assertEquals(HealthCheckResponse.ServingStatus.SERVING, resp.getStatus());
    }

    @Test
    @DisplayName("setPluginsReady toggle: SERVING → NOT_SERVING reflected over real wire")
    void togglePluginsReady_statusChangesOverWire() {
        healthService.setPluginsReady(true);
        HealthCheckResponse serving = stub.check(
                HealthCheckRequest.newBuilder().setService("").build()
        );
        assertEquals(HealthCheckResponse.ServingStatus.SERVING, serving.getStatus());

        healthService.setPluginsReady(false);
        HealthCheckResponse notServing = stub.check(
                HealthCheckRequest.newBuilder().setService("").build()
        );
        assertEquals(HealthCheckResponse.ServingStatus.NOT_SERVING, notServing.getStatus());
    }
}
