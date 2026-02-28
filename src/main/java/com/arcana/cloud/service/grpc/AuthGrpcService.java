package com.arcana.cloud.service.grpc;

import com.arcana.cloud.dto.request.LoginRequest;
import com.arcana.cloud.dto.request.RefreshTokenRequest;
import com.arcana.cloud.dto.request.RegisterRequest;
import com.arcana.cloud.dto.response.AuthResponse;
import com.arcana.cloud.grpc.AuthServiceGrpc;
import com.arcana.cloud.grpc.LogoutAllRequest;
import com.arcana.cloud.grpc.LogoutRequest;
import com.arcana.cloud.grpc.LogoutResponse;
import com.arcana.cloud.grpc.UserInfo;
import com.arcana.cloud.grpc.ValidateTokenRequest;
import com.arcana.cloud.grpc.ValidateTokenResponse;
import com.arcana.cloud.security.JwtTokenProvider;
import com.arcana.cloud.service.AuthService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * gRPC server for AuthService.
 * Active in: monolithic mode OR service layer of layered mode.
 * Exposes AuthService operations over gRPC for controller layer to consume.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnExpression("'${deployment.layer:}' == '' or '${deployment.layer:}' == 'service'")
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private final AuthService authService;
    private final JwtTokenProvider tokenProvider;

    @Override
    public void register(com.arcana.cloud.grpc.RegisterRequest request,
                         StreamObserver<com.arcana.cloud.grpc.AuthResponse> responseObserver) {
        try {
            log.debug("gRPC: Registering user: {}", request.getUsername());

            RegisterRequest registerRequest = RegisterRequest.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(request.getPassword())
                .confirmPassword(request.getConfirmPassword())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .build();

            AuthResponse response = authService.register(registerRequest);
            responseObserver.onNext(toGrpcResponse(response));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC: Error registering user", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Failed to register: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void login(com.arcana.cloud.grpc.LoginRequest request,
                      StreamObserver<com.arcana.cloud.grpc.AuthResponse> responseObserver) {
        try {
            log.debug("gRPC: Login attempt for: {}", request.getUsernameOrEmail());

            LoginRequest loginRequest = LoginRequest.builder()
                .usernameOrEmail(request.getUsernameOrEmail())
                .password(request.getPassword())
                .build();

            AuthResponse response = authService.login(loginRequest);
            responseObserver.onNext(toGrpcResponse(response));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC: Error during login", e);
            responseObserver.onError(Status.UNAUTHENTICATED
                .withDescription("Login failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void refreshToken(com.arcana.cloud.grpc.RefreshTokenRequest request,
                             StreamObserver<com.arcana.cloud.grpc.AuthResponse> responseObserver) {
        try {
            log.debug("gRPC: Refreshing token");

            RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
                .refreshToken(request.getRefreshToken())
                .build();

            AuthResponse response = authService.refreshToken(refreshRequest);
            responseObserver.onNext(toGrpcResponse(response));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC: Error refreshing token", e);
            responseObserver.onError(Status.UNAUTHENTICATED
                .withDescription("Token refresh failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void logout(LogoutRequest request, StreamObserver<LogoutResponse> responseObserver) {
        try {
            log.debug("gRPC: Logging out");
            authService.logout(request.getAccessToken());
            responseObserver.onNext(LogoutResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Logged out successfully")
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC: Error during logout", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Logout failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void logoutAll(LogoutAllRequest request, StreamObserver<LogoutResponse> responseObserver) {
        try {
            log.debug("gRPC: Logging out all sessions for user: {}", request.getUserId());
            authService.logoutAll(request.getUserId());
            responseObserver.onNext(LogoutResponse.newBuilder()
                .setSuccess(true)
                .setMessage("All sessions logged out successfully")
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC: Error during logout all", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Logout all failed: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void validateToken(ValidateTokenRequest request,
                              StreamObserver<ValidateTokenResponse> responseObserver) {
        try {
            log.debug("gRPC: Validating token");
            boolean valid = tokenProvider.validateToken(request.getToken());

            ValidateTokenResponse.Builder builder = ValidateTokenResponse.newBuilder()
                .setValid(valid);

            if (valid) {
                Long userId = tokenProvider.getUserIdFromToken(request.getToken());
                String username = tokenProvider.getUsernameFromToken(request.getToken());
                String role = tokenProvider.getRoleFromToken(request.getToken());

                builder.setUserId(userId)
                    .setUsername(username != null ? username : "")
                    .setRole(role != null ? role : "");
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC: Error validating token", e);
            responseObserver.onNext(ValidateTokenResponse.newBuilder()
                .setValid(false)
                .build());
            responseObserver.onCompleted();
        }
    }

    private com.arcana.cloud.grpc.AuthResponse toGrpcResponse(AuthResponse response) {
        UserInfo.Builder userInfoBuilder = UserInfo.newBuilder();
        if (response.getUser() != null) {
            userInfoBuilder
                .setId(response.getUser().getId())
                .setUsername(response.getUser().getUsername())
                .setEmail(response.getUser().getEmail())
                .setFirstName(response.getUser().getFirstName() != null ? response.getUser().getFirstName() : "")
                .setLastName(response.getUser().getLastName() != null ? response.getUser().getLastName() : "")
                .setRole(response.getUser().getRole().name())
                .setIsActive(response.getUser().getIsActive() != null && response.getUser().getIsActive())
                .setIsVerified(response.getUser().getIsVerified() != null && response.getUser().getIsVerified());
        }

        return com.arcana.cloud.grpc.AuthResponse.newBuilder()
            .setAccessToken(response.getAccessToken())
            .setRefreshToken(response.getRefreshToken())
            .setTokenType(response.getTokenType())
            .setExpiresIn(response.getExpiresIn())
            .setUser(userInfoBuilder.build())
            .build();
    }
}
