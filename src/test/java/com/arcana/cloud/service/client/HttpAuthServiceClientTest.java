package com.arcana.cloud.service.client;

import com.arcana.cloud.dto.request.LoginRequest;
import com.arcana.cloud.dto.request.RefreshTokenRequest;
import com.arcana.cloud.dto.request.RegisterRequest;
import com.arcana.cloud.dto.response.ApiResponse;
import com.arcana.cloud.dto.response.AuthResponse;
import com.arcana.cloud.dto.response.UserResponse;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HttpAuthServiceClientTest {

    private RestTemplate restTemplate;
    private HttpAuthServiceClient client;

    private static final String BASE_URL = "http://localhost:8081";

    @BeforeEach
    void setUp() {
        client = new HttpAuthServiceClient();
        restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(client, "serviceUrl", BASE_URL);
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
    }

    // ==================== register ====================

    @Test
    void register_success_returnsAuthResponse() {
        RegisterRequest request = RegisterRequest.builder()
            .username("john").email("john@test.com")
            .password("password1").confirmPassword("password1")
            .firstName("John").lastName("Doe")
            .build();

        AuthResponse authResponse = AuthResponse.builder()
            .accessToken("access-token").refreshToken("refresh-token")
            .tokenType("Bearer").expiresIn(3600L).build();

        ApiResponse<AuthResponse> apiResponse = ApiResponse.success(authResponse);

        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<AuthResponse>> entity =
            (ResponseEntity<ApiResponse<AuthResponse>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class));

        AuthResponse result = client.register(request);

        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo("access-token");
    }

    @Test
    void register_nullBody_throwsIllegalState() {
        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<AuthResponse>> entity =
            (ResponseEntity<ApiResponse<AuthResponse>>) (ResponseEntity<?>) ResponseEntity.ok(null);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class));

        assertThatThrownBy(() -> client.register(RegisterRequest.builder()
                .username("u").email("e@e.com").password("pass").confirmPassword("pass").build()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void register_failureResponse_throwsIllegalState() {
        ApiResponse<AuthResponse> apiResponse = ApiResponse.error("Registration failed");

        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<AuthResponse>> entity =
            (ResponseEntity<ApiResponse<AuthResponse>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class));

        assertThatThrownBy(() -> client.register(RegisterRequest.builder()
                .username("u").email("e@e.com").password("pass").confirmPassword("pass").build()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Registration failed");
    }

    @Test
    void register_restClientException_throwsIllegalState() {
        doThrow(new RestClientException("connection refused")).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class));

        assertThatThrownBy(() -> client.register(RegisterRequest.builder()
                .username("u").email("e@e.com").password("pass").confirmPassword("pass").build()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("connection refused");
    }

    // ==================== login ====================

    @Test
    void login_success_returnsAuthResponse() {
        LoginRequest request = LoginRequest.builder()
            .usernameOrEmail("john").password("password1").build();

        AuthResponse authResponse = AuthResponse.builder()
            .accessToken("at").refreshToken("rt").tokenType("Bearer").expiresIn(3600L).build();

        ApiResponse<AuthResponse> apiResponse = ApiResponse.success(authResponse);

        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<AuthResponse>> entity =
            (ResponseEntity<ApiResponse<AuthResponse>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class));

        AuthResponse result = client.login(request);
        assertThat(result.getAccessToken()).isEqualTo("at");
    }

    @Test
    void login_failureResponse_throwsUnauthorized() {
        ApiResponse<AuthResponse> apiResponse = ApiResponse.error("Bad credentials");

        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<AuthResponse>> entity =
            (ResponseEntity<ApiResponse<AuthResponse>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class));

        assertThatThrownBy(() -> client.login(
                LoginRequest.builder().usernameOrEmail("u").password("p").build()))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void login_restClientException_throwsUnauthorized() {
        doThrow(new RestClientException("timeout")).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class));

        assertThatThrownBy(() -> client.login(
                LoginRequest.builder().usernameOrEmail("u").password("p").build()))
            .isInstanceOf(UnauthorizedException.class);
    }

    // ==================== refreshToken ====================

    @Test
    void refreshToken_success_returnsAuthResponse() {
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken("rt").build();

        AuthResponse authResponse = AuthResponse.builder()
            .accessToken("new-at").refreshToken("new-rt").expiresIn(3600L).build();

        ApiResponse<AuthResponse> apiResponse = ApiResponse.success(authResponse);

        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<AuthResponse>> entity =
            (ResponseEntity<ApiResponse<AuthResponse>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class));

        AuthResponse result = client.refreshToken(request);
        assertThat(result.getAccessToken()).isEqualTo("new-at");
    }

    @Test
    void refreshToken_nullBody_throwsUnauthorized() {
        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<AuthResponse>> entity =
            (ResponseEntity<ApiResponse<AuthResponse>>) (ResponseEntity<?>) ResponseEntity.ok(null);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class));

        assertThatThrownBy(() -> client.refreshToken(RefreshTokenRequest.builder().refreshToken("rt").build()))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refreshToken_restClientException_throwsUnauthorized() {
        doThrow(new RestClientException("timeout")).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class));

        assertThatThrownBy(() -> client.refreshToken(RefreshTokenRequest.builder().refreshToken("rt").build()))
            .isInstanceOf(UnauthorizedException.class);
    }

    // ==================== logout ====================

    @Test
    void logout_success_noException() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
            .thenReturn(ResponseEntity.ok().build());

        client.logout("access-token");

        verify(restTemplate).postForEntity(contains("logout"), any(), eq(Void.class));
    }

    @Test
    void logout_restClientException_silentlyIgnored() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
            .thenThrow(new RestClientException("connection refused"));

        // Should not throw
        client.logout("access-token");
    }

    // ==================== logoutAll ====================

    @Test
    void logoutAll_success_noException() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
            .thenReturn(ResponseEntity.ok().build());

        client.logoutAll(1L);

        verify(restTemplate).postForEntity(contains("logout-all"), any(), eq(Void.class));
    }

    @Test
    void logoutAll_restClientException_silentlyIgnored() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
            .thenThrow(new RestClientException("timeout"));

        // Should not throw
        client.logoutAll(1L);
    }
}
