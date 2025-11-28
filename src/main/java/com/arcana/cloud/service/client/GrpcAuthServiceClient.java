package com.arcana.cloud.service.client;

import com.arcana.cloud.dto.request.LoginRequest;
import com.arcana.cloud.dto.request.RefreshTokenRequest;
import com.arcana.cloud.dto.request.RegisterRequest;
import com.arcana.cloud.dto.response.AuthResponse;
import com.arcana.cloud.dto.response.UserResponse;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.exception.UnauthorizedException;
import com.arcana.cloud.grpc.AuthServiceGrpc;
import com.arcana.cloud.grpc.LogoutAllRequest;
import com.arcana.cloud.grpc.LogoutRequest;
import com.arcana.cloud.grpc.UserInfo;
import com.arcana.cloud.service.interfaces.AuthService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * gRPC client for AuthService.
 * Active only in controller layer of layered deployment.
 * Calls AuthGrpcService on the service layer via gRPC.
 */
@Service
@ConditionalOnExpression(
    "'${communication.protocol:grpc}' == 'grpc' and '${deployment.layer:}' == 'controller'"
)
@Slf4j
public class GrpcAuthServiceClient implements AuthService {

    @Value("${service.grpc.url:localhost:9090}")
    private String serviceUrl;

    @Value("${grpc.client.shutdown-timeout-seconds:5}")
    private long shutdownTimeoutSeconds;

    private ManagedChannel channel;
    private AuthServiceGrpc.AuthServiceBlockingStub stub;

    @PostConstruct
    public void init() {
        log.info("Initializing gRPC Auth client for service URL: {}", serviceUrl);
        this.channel = ManagedChannelBuilder.forTarget(serviceUrl)
            .usePlaintext()
            .build();
        this.stub = AuthServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdown().awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
        }
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        try {
            com.arcana.cloud.grpc.RegisterRequest grpcRequest = com.arcana.cloud.grpc.RegisterRequest.newBuilder()
                .setUsername(request.getUsername())
                .setEmail(request.getEmail())
                .setPassword(request.getPassword())
                .setConfirmPassword(request.getConfirmPassword())
                .setFirstName(request.getFirstName() != null ? request.getFirstName() : "")
                .setLastName(request.getLastName() != null ? request.getLastName() : "")
                .build();

            com.arcana.cloud.grpc.AuthResponse response = stub.register(grpcRequest);
            return fromGrpcResponse(response);
        } catch (StatusRuntimeException e) {
            log.error("gRPC error during registration", e);
            throw new RuntimeException("Registration failed: " + e.getStatus().getDescription());
        }
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        try {
            com.arcana.cloud.grpc.LoginRequest grpcRequest = com.arcana.cloud.grpc.LoginRequest.newBuilder()
                .setUsernameOrEmail(request.getUsernameOrEmail())
                .setPassword(request.getPassword())
                .build();

            com.arcana.cloud.grpc.AuthResponse response = stub.login(grpcRequest);
            return fromGrpcResponse(response);
        } catch (StatusRuntimeException e) {
            log.error("gRPC error during login", e);
            throw new UnauthorizedException("Login failed: " + e.getStatus().getDescription());
        }
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        try {
            com.arcana.cloud.grpc.RefreshTokenRequest grpcRequest = com.arcana.cloud.grpc.RefreshTokenRequest.newBuilder()
                .setRefreshToken(request.getRefreshToken())
                .build();

            com.arcana.cloud.grpc.AuthResponse response = stub.refreshToken(grpcRequest);
            return fromGrpcResponse(response);
        } catch (StatusRuntimeException e) {
            log.error("gRPC error during token refresh", e);
            throw new UnauthorizedException("Token refresh failed: " + e.getStatus().getDescription());
        }
    }

    @Override
    public void logout(String accessToken) {
        try {
            LogoutRequest grpcRequest = LogoutRequest.newBuilder()
                .setAccessToken(accessToken)
                .build();

            stub.logout(grpcRequest);
        } catch (StatusRuntimeException e) {
            log.error("gRPC error during logout", e);
            throw new RuntimeException("Logout failed: " + e.getStatus().getDescription());
        }
    }

    @Override
    public void logoutAll(Long userId) {
        try {
            LogoutAllRequest grpcRequest = LogoutAllRequest.newBuilder()
                .setUserId(userId)
                .build();

            stub.logoutAll(grpcRequest);
        } catch (StatusRuntimeException e) {
            log.error("gRPC error during logout all", e);
            throw new RuntimeException("Logout all failed: " + e.getStatus().getDescription());
        }
    }

    private AuthResponse fromGrpcResponse(com.arcana.cloud.grpc.AuthResponse response) {
        UserResponse userResponse = null;
        if (response.hasUser()) {
            UserInfo userInfo = response.getUser();
            userResponse = UserResponse.builder()
                .id(userInfo.getId())
                .username(userInfo.getUsername())
                .email(userInfo.getEmail())
                .firstName(userInfo.getFirstName().isEmpty() ? null : userInfo.getFirstName())
                .lastName(userInfo.getLastName().isEmpty() ? null : userInfo.getLastName())
                .role(UserRole.valueOf(userInfo.getRole()))
                .isActive(userInfo.getIsActive())
                .isVerified(userInfo.getIsVerified())
                .build();
        }

        return AuthResponse.builder()
            .accessToken(response.getAccessToken())
            .refreshToken(response.getRefreshToken())
            .tokenType(response.getTokenType())
            .expiresIn(response.getExpiresIn())
            .user(userResponse)
            .build();
    }
}
