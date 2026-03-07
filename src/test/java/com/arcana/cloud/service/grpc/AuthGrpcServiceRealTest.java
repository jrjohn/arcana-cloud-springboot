package com.arcana.cloud.service.grpc;

import com.arcana.cloud.dto.response.AuthResponse;
import com.arcana.cloud.dto.response.UserResponse;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.exception.UnauthorizedException;
import com.arcana.cloud.grpc.AuthServiceGrpc;
import com.arcana.cloud.grpc.LoginRequest;
import com.arcana.cloud.grpc.LogoutAllRequest;
import com.arcana.cloud.grpc.LogoutRequest;
import com.arcana.cloud.grpc.LogoutResponse;
import com.arcana.cloud.grpc.RefreshTokenRequest;
import com.arcana.cloud.grpc.RegisterRequest;
import com.arcana.cloud.grpc.ValidateTokenRequest;
import com.arcana.cloud.grpc.ValidateTokenResponse;
import com.arcana.cloud.security.JwtTokenProvider;
import com.arcana.cloud.service.AuthService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Real gRPC integration tests for AuthGrpcService.
 *
 * <p>Uses {@link InProcessServerBuilder} to start an actual in-process gRPC server.
 * Requests go through real protobuf serialization → gRPC transport → deserialization,
 * validating the full wire protocol — not just Java object handling.</p>
 *
 * <p>Complements {@link AuthGrpcServiceTest} (unit) with protocol-level validation.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthGrpcService — Real In-Process gRPC Protocol Tests")
class AuthGrpcServiceRealTest {

    @Mock private AuthService authService;
    @Mock private JwtTokenProvider tokenProvider;

    private Server grpcServer;
    private ManagedChannel channel;
    private AuthServiceGrpc.AuthServiceBlockingStub stub;

    private AuthResponse mockAuthResponse;

    @BeforeEach
    void setUp() throws Exception {
        UserResponse userResponse = UserResponse.builder()
                .id(1L).username("testuser").email("test@example.com")
                .firstName("Test").lastName("User")
                .role(UserRole.USER).isActive(true).isVerified(false)
                .build();

        mockAuthResponse = AuthResponse.builder()
                .accessToken("access_token").refreshToken("refresh_token")
                .tokenType("Bearer").expiresIn(3600L).user(userResponse)
                .build();

        // Stand up a real in-process gRPC server — real protobuf wire protocol
        AuthGrpcService impl = new AuthGrpcService(authService, tokenProvider);
        String serverName = InProcessServerBuilder.generateName();
        grpcServer = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(impl)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        stub = AuthServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws Exception {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        grpcServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    // ─── register ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: success — protobuf fields survive real gRPC wire round-trip")
    void register_success_realWire() {
        when(authService.register(any())).thenReturn(mockAuthResponse);

        com.arcana.cloud.grpc.AuthResponse resp = stub.register(
                RegisterRequest.newBuilder()
                        .setUsername("testuser").setEmail("test@example.com")
                        .setPassword("password").setConfirmPassword("password")
                        .setFirstName("Test").setLastName("User")
                        .build()
        );

        // All fields must survive protobuf encode → gRPC → decode intact
        assertEquals("access_token", resp.getAccessToken());
        assertEquals("refresh_token", resp.getRefreshToken());
        assertEquals("Bearer", resp.getTokenType());
        assertEquals(3600L, resp.getExpiresIn());
        assertTrue(resp.hasUser());
        assertEquals("testuser", resp.getUser().getUsername());
    }

    @Test
    @DisplayName("register: exception → gRPC INTERNAL status code over wire")
    void register_error_grpcStatusCode() {
        when(authService.register(any())).thenThrow(new RuntimeException("Registration failed"));

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                stub.register(RegisterRequest.newBuilder()
                        .setUsername("testuser").setEmail("test@example.com")
                        .setPassword("password").setConfirmPassword("password")
                        .build())
        );

        // Service maps RuntimeException → gRPC INTERNAL
        assertEquals(Status.Code.INTERNAL, ex.getStatus().getCode());
    }

    // ─── login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: success — access token returned over real gRPC wire")
    void login_success_realWire() {
        when(authService.login(any())).thenReturn(mockAuthResponse);

        com.arcana.cloud.grpc.AuthResponse resp = stub.login(
                LoginRequest.newBuilder()
                        .setUsernameOrEmail("testuser").setPassword("password")
                        .build()
        );

        assertEquals("access_token", resp.getAccessToken());
        assertEquals(3600L, resp.getExpiresIn());
    }

    @Test
    @DisplayName("login: UnauthorizedException → gRPC UNAUTHENTICATED status over wire")
    void login_unauthorized_grpcStatus() {
        when(authService.login(any())).thenThrow(new UnauthorizedException("Invalid credentials"));

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                stub.login(LoginRequest.newBuilder()
                        .setUsernameOrEmail("testuser").setPassword("wrong")
                        .build())
        );

        assertEquals(Status.Code.UNAUTHENTICATED, ex.getStatus().getCode());
    }

    // ─── refreshToken ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("refreshToken: success — new tokens returned over real gRPC wire")
    void refreshToken_success_realWire() {
        when(authService.refreshToken(any())).thenReturn(mockAuthResponse);

        com.arcana.cloud.grpc.AuthResponse resp = stub.refreshToken(
                RefreshTokenRequest.newBuilder()
                        .setRefreshToken("valid_refresh_token")
                        .build()
        );

        assertEquals("access_token", resp.getAccessToken());
        assertEquals("refresh_token", resp.getRefreshToken());
    }

    @Test
    @DisplayName("refreshToken: invalid token → gRPC UNAUTHENTICATED status over wire")
    void refreshToken_invalid_grpcStatus() {
        when(authService.refreshToken(any())).thenThrow(new UnauthorizedException("Invalid token"));

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                stub.refreshToken(RefreshTokenRequest.newBuilder()
                        .setRefreshToken("bad_token").build())
        );

        assertEquals(Status.Code.UNAUTHENTICATED, ex.getStatus().getCode());
    }

    // ─── validateToken ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("validateToken: valid token — all fields correctly serialized over wire")
    void validateToken_valid_realWire() {
        when(tokenProvider.validateToken("valid_token")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("valid_token")).thenReturn(1L);
        when(tokenProvider.getUsernameFromToken("valid_token")).thenReturn("testuser");
        when(tokenProvider.getRoleFromToken("valid_token")).thenReturn("USER");

        ValidateTokenResponse resp = stub.validateToken(
                ValidateTokenRequest.newBuilder().setToken("valid_token").build()
        );

        assertTrue(resp.getValid());
        assertEquals(1L, resp.getUserId());
        assertEquals("testuser", resp.getUsername());
        assertEquals("USER", resp.getRole());
    }

    @Test
    @DisplayName("validateToken: invalid token → valid=false serialized over wire")
    void validateToken_invalid_realWire() {
        when(tokenProvider.validateToken("bad_token")).thenReturn(false);

        ValidateTokenResponse resp = stub.validateToken(
                ValidateTokenRequest.newBuilder().setToken("bad_token").build()
        );

        assertFalse(resp.getValid());
        assertEquals(0L, resp.getUserId());
        assertEquals("", resp.getUsername());
    }

    // ─── logout ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("logout: success — success=true and message serialized over wire")
    void logout_success_realWire() {
        LogoutResponse resp = stub.logout(
                LogoutRequest.newBuilder().setAccessToken("valid_token").build()
        );

        assertTrue(resp.getSuccess());
        assertEquals("Logged out successfully", resp.getMessage());
        verify(authService).logout("valid_token");
    }

    @Test
    @DisplayName("logout: exception → gRPC INTERNAL status over wire")
    void logout_error_grpcStatus() {
        doThrow(new RuntimeException("Logout error")).when(authService).logout(anyString());

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                stub.logout(LogoutRequest.newBuilder().setAccessToken("token").build())
        );

        assertEquals(Status.Code.INTERNAL, ex.getStatus().getCode());
    }

    @Test
    @DisplayName("logoutAll: success — all sessions cleared, message serialized over wire")
    void logoutAll_success_realWire() {
        LogoutResponse resp = stub.logoutAll(
                LogoutAllRequest.newBuilder().setUserId(1L).build()
        );

        assertTrue(resp.getSuccess());
        assertEquals("All sessions logged out successfully", resp.getMessage());
        verify(authService).logoutAll(1L);
    }

    @Test
    @DisplayName("logoutAll: exception → gRPC INTERNAL status over wire")
    void logoutAll_error_grpcStatus() {
        doThrow(new RuntimeException("User not found")).when(authService).logoutAll(anyLong());

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                stub.logoutAll(LogoutAllRequest.newBuilder().setUserId(999L).build())
        );

        assertEquals(Status.Code.INTERNAL, ex.getStatus().getCode());
    }
}
