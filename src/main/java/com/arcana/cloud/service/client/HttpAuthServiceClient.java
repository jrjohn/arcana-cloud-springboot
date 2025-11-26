package com.arcana.cloud.service.client;

import com.arcana.cloud.dto.request.LoginRequest;
import com.arcana.cloud.dto.request.RefreshTokenRequest;
import com.arcana.cloud.dto.request.RegisterRequest;
import com.arcana.cloud.dto.response.ApiResponse;
import com.arcana.cloud.dto.response.AuthResponse;
import com.arcana.cloud.exception.UnauthorizedException;
import com.arcana.cloud.service.interfaces.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for AuthService.
 * Active when communication.protocol=http.
 * Calls AuthService on the service layer via HTTP REST.
 */
@Service
@ConditionalOnExpression(
    "'${communication.protocol:grpc}' == 'http' and '${deployment.layer:}' == 'controller'"
)
@Slf4j
public class HttpAuthServiceClient implements AuthService {

    @Value("${service.http.url:http://localhost:8081}")
    private String serviceUrl;

    private final RestTemplate restTemplate;

    public HttpAuthServiceClient() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        try {
            log.debug("HTTP client: Registering user via {}", serviceUrl);
            ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
                serviceUrl + "/internal/api/v1/auth/register",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() { }
            );

            if (response.getBody() != null && response.getBody().isSuccess()) {
                return response.getBody().getData();
            }
            throw new RuntimeException("Failed to register user: "
                + (response.getBody() != null ? response.getBody().getMessage() : "Unknown error"));
        } catch (RestClientException e) {
            log.error("HTTP error during registration", e);
            throw new RuntimeException("Failed to register user: " + e.getMessage());
        }
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        try {
            log.debug("HTTP client: Login attempt via {}", serviceUrl);
            ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
                serviceUrl + "/internal/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() { }
            );

            if (response.getBody() != null && response.getBody().isSuccess()) {
                return response.getBody().getData();
            }
            throw new UnauthorizedException("Invalid credentials");
        } catch (RestClientException e) {
            log.error("HTTP error during login", e);
            throw new UnauthorizedException("Invalid credentials");
        }
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        try {
            log.debug("HTTP client: Refreshing token via {}", serviceUrl);
            ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
                serviceUrl + "/internal/api/v1/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() { }
            );

            if (response.getBody() != null && response.getBody().isSuccess()) {
                return response.getBody().getData();
            }
            throw new UnauthorizedException("Invalid refresh token");
        } catch (RestClientException e) {
            log.error("HTTP error refreshing token", e);
            throw new UnauthorizedException("Invalid refresh token");
        }
    }

    @Override
    public void logout(String token) {
        try {
            log.debug("HTTP client: Logout via {}", serviceUrl);
            restTemplate.postForEntity(
                serviceUrl + "/internal/api/v1/auth/logout?accessToken=" + token,
                HttpEntity.EMPTY,
                Void.class
            );
        } catch (RestClientException e) {
            log.error("HTTP error during logout", e);
            // Silently ignore logout errors
        }
    }

    @Override
    public void logoutAll(Long userId) {
        try {
            log.debug("HTTP client: Logout all via {}", serviceUrl);
            restTemplate.postForEntity(
                serviceUrl + "/internal/api/v1/auth/logout-all?userId=" + userId,
                HttpEntity.EMPTY,
                Void.class
            );
        } catch (RestClientException e) {
            log.error("HTTP error during logout all", e);
            // Silently ignore logout errors
        }
    }
}
