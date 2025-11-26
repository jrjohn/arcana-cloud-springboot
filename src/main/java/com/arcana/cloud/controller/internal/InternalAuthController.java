package com.arcana.cloud.controller.internal;

import com.arcana.cloud.dto.request.LoginRequest;
import com.arcana.cloud.dto.request.RefreshTokenRequest;
import com.arcana.cloud.dto.request.RegisterRequest;
import com.arcana.cloud.dto.response.ApiResponse;
import com.arcana.cloud.dto.response.AuthResponse;
import com.arcana.cloud.security.JwtTokenProvider;
import com.arcana.cloud.service.interfaces.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal REST controller for AuthService.
 * Active in: service layer of layered mode when using HTTP communication.
 * Provides HTTP endpoints for controller layer to consume (alternative to gRPC).
 */
@RestController
@RequestMapping("/internal/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnExpression(
    "'${deployment.layer:}' == 'service' and '${communication.protocol:grpc}' == 'http'"
)
public class InternalAuthController {

    private final AuthService authService;
    private final JwtTokenProvider tokenProvider;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        log.debug("Internal HTTP: Registering user: {}", request.getUsername());
        AuthResponse response = authService.register(request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        log.debug("Internal HTTP: Login attempt for: {}", request.getUsernameOrEmail());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        log.debug("Internal HTTP: Refreshing token");
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestParam String accessToken) {
        log.debug("Internal HTTP: Logging out");
        authService.logout(accessToken);
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(@RequestParam Long userId) {
        log.debug("Internal HTTP: Logging out all sessions for user: {}", userId);
        authService.logoutAll(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "All sessions logged out successfully"));
    }

    @PostMapping("/validate-token")
    public ResponseEntity<ApiResponse<TokenValidationResponse>> validateToken(
            @RequestParam String token) {
        log.debug("Internal HTTP: Validating token");
        boolean valid = tokenProvider.validateToken(token);

        TokenValidationResponse response = new TokenValidationResponse();
        response.setValid(valid);

        if (valid) {
            response.setUserId(tokenProvider.getUserIdFromToken(token));
            response.setUsername(tokenProvider.getUsernameFromToken(token));
            response.setRole(tokenProvider.getRoleFromToken(token));
        }

        return ResponseEntity.ok(ApiResponse.success(response, "Token validation complete"));
    }

    @lombok.Data
    public static class TokenValidationResponse {
        private boolean valid;
        private Long userId;
        private String username;
        private String role;
    }
}
