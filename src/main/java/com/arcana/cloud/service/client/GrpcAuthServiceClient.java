package com.arcana.cloud.service.client;

import com.arcana.cloud.dto.request.LoginRequest;
import com.arcana.cloud.dto.request.RefreshTokenRequest;
import com.arcana.cloud.dto.request.RegisterRequest;
import com.arcana.cloud.dto.response.AuthResponse;
import com.arcana.cloud.dto.response.UserResponse;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.exception.ServiceUnavailableException;
import com.arcana.cloud.exception.UnauthorizedException;
import com.arcana.cloud.grpc.AuthServiceGrpc;
import com.arcana.cloud.grpc.LogoutAllRequest;
import com.arcana.cloud.grpc.LogoutRequest;
import com.arcana.cloud.grpc.UserInfo;
import com.arcana.cloud.service.interfaces.AuthService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * gRPC client for AuthService with Circuit Breaker protection.
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

    @Autowired(required = false)
    private CircuitBreaker authServiceCircuitBreaker;

    @PostConstruct
    public void init() {
        log.info("Initializing gRPC Auth client for service URL: {}", serviceUrl);
        this.channel = ManagedChannelBuilder.forTarget(serviceUrl)
            .usePlaintext()
            .build();
        this.stub = AuthServiceGrpc.newBlockingStub(channel);

        if (authServiceCircuitBreaker != null) {
            log.info("Circuit Breaker enabled for Auth Service");
        }
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
        return executeWithCircuitBreaker(() -> {
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
        }, "register");
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        return executeWithCircuitBreaker(() -> {
            com.arcana.cloud.grpc.LoginRequest grpcRequest = com.arcana.cloud.grpc.LoginRequest.newBuilder()
                .setUsernameOrEmail(request.getUsernameOrEmail())
                .setPassword(request.getPassword())
                .build();

            com.arcana.cloud.grpc.AuthResponse response = stub.login(grpcRequest);
            return fromGrpcResponse(response);
        }, "login");
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        return executeWithCircuitBreaker(() -> {
            com.arcana.cloud.grpc.RefreshTokenRequest grpcRequest = com.arcana.cloud.grpc.RefreshTokenRequest.newBuilder()
                .setRefreshToken(request.getRefreshToken())
                .build();

            com.arcana.cloud.grpc.AuthResponse response = stub.refreshToken(grpcRequest);
            return fromGrpcResponse(response);
        }, "refreshToken");
    }

    @Override
    public void logout(String accessToken) {
        executeWithCircuitBreaker(() -> {
            LogoutRequest grpcRequest = LogoutRequest.newBuilder()
                .setAccessToken(accessToken)
                .build();

            stub.logout(grpcRequest);
            return null;
        }, "logout");
    }

    @Override
    public void logoutAll(Long userId) {
        executeWithCircuitBreaker(() -> {
            LogoutAllRequest grpcRequest = LogoutAllRequest.newBuilder()
                .setUserId(userId)
                .build();

            stub.logoutAll(grpcRequest);
            return null;
        }, "logoutAll");
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

    /**
     * Executes a gRPC call with circuit breaker protection.
     */
    private <T> T executeWithCircuitBreaker(Supplier<T> supplier, String operation) {
        try {
            if (authServiceCircuitBreaker != null) {
                return authServiceCircuitBreaker.executeSupplier(() -> executeGrpcCall(supplier, operation));
            } else {
                return executeGrpcCall(supplier, operation);
            }
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker OPEN for Auth Service, operation: {}", operation);
            throw new ServiceUnavailableException("Auth service is unavailable");
        }
    }

    /**
     * Executes a gRPC call with proper error handling and categorization.
     */
    private <T> T executeGrpcCall(Supplier<T> supplier, String operation) {
        try {
            return supplier.get();
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();

            // Categorize error by status code for better debugging
            switch (code) {
                case UNAUTHENTICATED:
                case PERMISSION_DENIED:
                    log.debug("Authentication failed in {}: {}", operation, e.getStatus().getDescription());
                    throw new UnauthorizedException(e.getStatus().getDescription());
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    log.error("Service unavailable in {}: {} ({})", operation, e.getStatus().getDescription(), code);
                    throw new ServiceUnavailableException("Auth service unavailable: " + e.getStatus().getDescription());
                case INVALID_ARGUMENT:
                    log.error("Invalid argument in {}: {}", operation, e.getStatus().getDescription());
                    throw new IllegalArgumentException("Invalid argument: " + e.getStatus().getDescription());
                case ALREADY_EXISTS:
                    log.debug("Resource already exists in {}: {}", operation, e.getStatus().getDescription());
                    throw new RuntimeException("Resource already exists: " + e.getStatus().getDescription());
                default:
                    log.error("gRPC error in {}: {} ({})", operation, e.getStatus().getDescription(), code);
                    throw new RuntimeException("Service error: " + e.getStatus().getDescription());
            }
        }
    }
}
