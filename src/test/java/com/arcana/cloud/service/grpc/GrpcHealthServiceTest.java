package com.arcana.cloud.service.grpc;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GrpcHealthServiceTest {

    private GrpcHealthService grpcHealthService;

    @BeforeEach
    void setUp() {
        grpcHealthService = new GrpcHealthService();
    }

    @Test
    void testConstructor_DefaultStatuses() {
        // Service should have default statuses set
        HealthCheckRequest request = HealthCheckRequest.newBuilder()
            .setService("")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<HealthCheckResponse> responseObserver = mock(StreamObserver.class);

        // By default plugins are not ready, so overall health is NOT_SERVING
        grpcHealthService.setPluginsReady(true);
        grpcHealthService.check(request, responseObserver);

        ArgumentCaptor<HealthCheckResponse> captor = ArgumentCaptor.forClass(HealthCheckResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertEquals(HealthCheckResponse.ServingStatus.SERVING, captor.getValue().getStatus());
    }

    @Test
    void testCheck_OverallHealthWhenPluginsNotReady() {
        HealthCheckRequest request = HealthCheckRequest.newBuilder()
            .setService("")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<HealthCheckResponse> responseObserver = mock(StreamObserver.class);

        // Plugins not ready by default
        grpcHealthService.check(request, responseObserver);

        ArgumentCaptor<HealthCheckResponse> captor = ArgumentCaptor.forClass(HealthCheckResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertEquals(HealthCheckResponse.ServingStatus.NOT_SERVING, captor.getValue().getStatus());
    }

    @Test
    void testCheck_KnownService_AuthService() {
        HealthCheckRequest request = HealthCheckRequest.newBuilder()
            .setService("arcana.cloud.AuthService")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<HealthCheckResponse> responseObserver = mock(StreamObserver.class);

        grpcHealthService.check(request, responseObserver);

        ArgumentCaptor<HealthCheckResponse> captor = ArgumentCaptor.forClass(HealthCheckResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertEquals(HealthCheckResponse.ServingStatus.SERVING, captor.getValue().getStatus());
    }

    @Test
    void testCheck_KnownService_UserService() {
        HealthCheckRequest request = HealthCheckRequest.newBuilder()
            .setService("arcana.cloud.UserService")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<HealthCheckResponse> responseObserver = mock(StreamObserver.class);

        grpcHealthService.check(request, responseObserver);

        ArgumentCaptor<HealthCheckResponse> captor = ArgumentCaptor.forClass(HealthCheckResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertEquals(HealthCheckResponse.ServingStatus.SERVING, captor.getValue().getStatus());
    }

    @Test
    void testCheck_KnownService_HealthService() {
        HealthCheckRequest request = HealthCheckRequest.newBuilder()
            .setService("grpc.health.v1.Health")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<HealthCheckResponse> responseObserver = mock(StreamObserver.class);

        grpcHealthService.check(request, responseObserver);

        ArgumentCaptor<HealthCheckResponse> captor = ArgumentCaptor.forClass(HealthCheckResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertEquals(HealthCheckResponse.ServingStatus.SERVING, captor.getValue().getStatus());
    }

    @Test
    void testCheck_UnknownService() {
        HealthCheckRequest request = HealthCheckRequest.newBuilder()
            .setService("unknown.Service")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<HealthCheckResponse> responseObserver = mock(StreamObserver.class);

        grpcHealthService.check(request, responseObserver);

        ArgumentCaptor<HealthCheckResponse> captor = ArgumentCaptor.forClass(HealthCheckResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertEquals(HealthCheckResponse.ServingStatus.SERVICE_UNKNOWN, captor.getValue().getStatus());
    }

    @Test
    void testWatch_ReturnsCurrentStatus() {
        HealthCheckRequest request = HealthCheckRequest.newBuilder()
            .setService("arcana.cloud.AuthService")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<HealthCheckResponse> responseObserver = mock(StreamObserver.class);

        grpcHealthService.watch(request, responseObserver);

        ArgumentCaptor<HealthCheckResponse> captor = ArgumentCaptor.forClass(HealthCheckResponse.class);
        verify(responseObserver).onNext(captor.capture());
        // watch does not call onCompleted
        verify(responseObserver, never()).onCompleted();

        assertEquals(HealthCheckResponse.ServingStatus.SERVING, captor.getValue().getStatus());
    }

    @Test
    void testWatch_UnknownService() {
        HealthCheckRequest request = HealthCheckRequest.newBuilder()
            .setService("unknown.Service")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<HealthCheckResponse> responseObserver = mock(StreamObserver.class);

        grpcHealthService.watch(request, responseObserver);

        ArgumentCaptor<HealthCheckResponse> captor = ArgumentCaptor.forClass(HealthCheckResponse.class);
        verify(responseObserver).onNext(captor.capture());

        assertEquals(HealthCheckResponse.ServingStatus.SERVICE_UNKNOWN, captor.getValue().getStatus());
    }

    @Test
    void testSetStatus() {
        grpcHealthService.setStatus("arcana.cloud.AuthService", HealthCheckResponse.ServingStatus.NOT_SERVING);

        HealthCheckRequest request = HealthCheckRequest.newBuilder()
            .setService("arcana.cloud.AuthService")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<HealthCheckResponse> responseObserver = mock(StreamObserver.class);

        grpcHealthService.check(request, responseObserver);

        ArgumentCaptor<HealthCheckResponse> captor = ArgumentCaptor.forClass(HealthCheckResponse.class);
        verify(responseObserver).onNext(captor.capture());

        assertEquals(HealthCheckResponse.ServingStatus.NOT_SERVING, captor.getValue().getStatus());
    }

    @Test
    void testSetServing() {
        grpcHealthService.setStatus("myService", HealthCheckResponse.ServingStatus.NOT_SERVING);
        grpcHealthService.setServing("myService");

        HealthCheckRequest request = HealthCheckRequest.newBuilder()
            .setService("myService")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<HealthCheckResponse> responseObserver = mock(StreamObserver.class);

        grpcHealthService.check(request, responseObserver);

        ArgumentCaptor<HealthCheckResponse> captor = ArgumentCaptor.forClass(HealthCheckResponse.class);
        verify(responseObserver).onNext(captor.capture());

        assertEquals(HealthCheckResponse.ServingStatus.SERVING, captor.getValue().getStatus());
    }

    @Test
    void testSetNotServing() {
        grpcHealthService.setNotServing("arcana.cloud.UserService");

        HealthCheckRequest request = HealthCheckRequest.newBuilder()
            .setService("arcana.cloud.UserService")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<HealthCheckResponse> responseObserver = mock(StreamObserver.class);

        grpcHealthService.check(request, responseObserver);

        ArgumentCaptor<HealthCheckResponse> captor = ArgumentCaptor.forClass(HealthCheckResponse.class);
        verify(responseObserver).onNext(captor.capture());

        assertEquals(HealthCheckResponse.ServingStatus.NOT_SERVING, captor.getValue().getStatus());
    }

    @Test
    void testSetPluginsReady_True() {
        assertFalse(grpcHealthService.isPluginsReady());

        grpcHealthService.setPluginsReady(true);

        assertTrue(grpcHealthService.isPluginsReady());

        // Check overall health should be SERVING now
        HealthCheckRequest request = HealthCheckRequest.newBuilder()
            .setService("")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<HealthCheckResponse> responseObserver = mock(StreamObserver.class);

        grpcHealthService.check(request, responseObserver);

        ArgumentCaptor<HealthCheckResponse> captor = ArgumentCaptor.forClass(HealthCheckResponse.class);
        verify(responseObserver).onNext(captor.capture());

        assertEquals(HealthCheckResponse.ServingStatus.SERVING, captor.getValue().getStatus());
    }

    @Test
    void testSetPluginsReady_False() {
        grpcHealthService.setPluginsReady(true);
        assertTrue(grpcHealthService.isPluginsReady());

        grpcHealthService.setPluginsReady(false);
        assertFalse(grpcHealthService.isPluginsReady());

        // Plugin system status should be NOT_SERVING
        HealthCheckRequest request = HealthCheckRequest.newBuilder()
            .setService("arcana.cloud.PluginSystem")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<HealthCheckResponse> responseObserver = mock(StreamObserver.class);

        grpcHealthService.check(request, responseObserver);

        ArgumentCaptor<HealthCheckResponse> captor = ArgumentCaptor.forClass(HealthCheckResponse.class);
        verify(responseObserver).onNext(captor.capture());

        assertEquals(HealthCheckResponse.ServingStatus.NOT_SERVING, captor.getValue().getStatus());
    }

    @Test
    void testClearStatuses() {
        grpcHealthService.setServing("custom.Service");
        grpcHealthService.setPluginsReady(true);

        grpcHealthService.clearStatuses();

        assertFalse(grpcHealthService.isPluginsReady());

        // custom.Service should be SERVICE_UNKNOWN after clear
        HealthCheckRequest request = HealthCheckRequest.newBuilder()
            .setService("custom.Service")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<HealthCheckResponse> responseObserver = mock(StreamObserver.class);

        grpcHealthService.check(request, responseObserver);

        ArgumentCaptor<HealthCheckResponse> captor = ArgumentCaptor.forClass(HealthCheckResponse.class);
        verify(responseObserver).onNext(captor.capture());

        assertEquals(HealthCheckResponse.ServingStatus.SERVICE_UNKNOWN, captor.getValue().getStatus());
    }

    @Test
    void testClearStatuses_OverallHealthStillWorks() {
        grpcHealthService.setPluginsReady(true);
        grpcHealthService.clearStatuses();

        HealthCheckRequest request = HealthCheckRequest.newBuilder()
            .setService("")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<HealthCheckResponse> responseObserver = mock(StreamObserver.class);

        // After clear, plugins not ready → NOT_SERVING for overall
        grpcHealthService.check(request, responseObserver);

        ArgumentCaptor<HealthCheckResponse> captor = ArgumentCaptor.forClass(HealthCheckResponse.class);
        verify(responseObserver).onNext(captor.capture());

        assertEquals(HealthCheckResponse.ServingStatus.NOT_SERVING, captor.getValue().getStatus());
    }

    @Test
    void testIsPluginsReady_InitiallyFalse() {
        assertFalse(grpcHealthService.isPluginsReady());
    }
}
