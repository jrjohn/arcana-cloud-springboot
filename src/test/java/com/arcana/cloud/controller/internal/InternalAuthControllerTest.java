package com.arcana.cloud.controller.internal;

import com.arcana.cloud.dto.request.LoginRequest;
import com.arcana.cloud.dto.request.RefreshTokenRequest;
import com.arcana.cloud.dto.request.RegisterRequest;
import com.arcana.cloud.dto.response.ApiResponse;
import com.arcana.cloud.dto.response.AuthResponse;
import com.arcana.cloud.dto.response.UserResponse;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.exception.UnauthorizedException;
import com.arcana.cloud.security.JwtTokenProvider;
import com.arcana.cloud.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalAuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private JwtTokenProvider tokenProvider;

    @InjectMocks
    private InternalAuthController internalAuthController;

    private AuthResponse mockAuthResponse;

    @BeforeEach
    void setUp() {
        UserResponse userResponse = UserResponse.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .firstName("Test")
            .lastName("User")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(false)
            .build();

        mockAuthResponse = AuthResponse.builder()
            .accessToken("access_token")
            .refreshToken("refresh_token")
            .tokenType("Bearer")
            .expiresIn(3600L)
            .user(userResponse)
            .build();
    }

    @Test
    void testRegister_Success() {
        RegisterRequest request = RegisterRequest.builder()
            .username("testuser")
            .email("test@example.com")
            .password("Password123")
            .confirmPassword("Password123")
            .firstName("Test")
            .lastName("User")
            .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(mockAuthResponse);

        ResponseEntity<ApiResponse<AuthResponse>> response = internalAuthController.register(request);

        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("access_token", response.getBody().getData().getAccessToken());
        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void testLogin_Success() {
        LoginRequest request = LoginRequest.builder()
            .usernameOrEmail("testuser")
            .password("Password123")
            .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(mockAuthResponse);

        ResponseEntity<ApiResponse<AuthResponse>> response = internalAuthController.login(request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("access_token", response.getBody().getData().getAccessToken());
        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    void testRefreshToken_Success() {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
            .refreshToken("valid_refresh_token")
            .build();

        when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(mockAuthResponse);

        ResponseEntity<ApiResponse<AuthResponse>> response = internalAuthController.refreshToken(request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("access_token", response.getBody().getData().getAccessToken());
    }

    @Test
    void testLogout_Success() {
        doNothing().when(authService).logout(anyString());

        ResponseEntity<ApiResponse<Void>> response = internalAuthController.logout("valid_access_token");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertNull(response.getBody().getData());
        verify(authService).logout("valid_access_token");
    }

    @Test
    void testLogoutAll_Success() {
        doNothing().when(authService).logoutAll(anyLong());

        ResponseEntity<ApiResponse<Void>> response = internalAuthController.logoutAll(1L);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        verify(authService).logoutAll(1L);
    }

    @Test
    void testValidateToken_ValidToken() {
        when(tokenProvider.validateToken("valid_token")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("valid_token")).thenReturn(1L);
        when(tokenProvider.getUsernameFromToken("valid_token")).thenReturn("testuser");
        when(tokenProvider.getRoleFromToken("valid_token")).thenReturn("USER");

        ResponseEntity<ApiResponse<InternalAuthController.TokenValidationResponse>> response =
            internalAuthController.validateToken("valid_token");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());

        InternalAuthController.TokenValidationResponse data = response.getBody().getData();
        assertTrue(data.isValid());
        assertEquals(1L, data.getUserId());
        assertEquals("testuser", data.getUsername());
        assertEquals("USER", data.getRole());
    }

    @Test
    void testValidateToken_InvalidToken() {
        when(tokenProvider.validateToken("invalid_token")).thenReturn(false);

        ResponseEntity<ApiResponse<InternalAuthController.TokenValidationResponse>> response =
            internalAuthController.validateToken("invalid_token");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        InternalAuthController.TokenValidationResponse data = response.getBody().getData();
        assertTrue(!data.isValid());
        assertNull(data.getUserId());
    }

    @Test
    void testRegister_ResponseMessage() {
        RegisterRequest request = RegisterRequest.builder()
            .username("newuser")
            .email("new@example.com")
            .password("Password123")
            .confirmPassword("Password123")
            .build();

        when(authService.register(any())).thenReturn(mockAuthResponse);

        ResponseEntity<ApiResponse<AuthResponse>> response = internalAuthController.register(request);

        assertEquals("User registered successfully", response.getBody().getMessage());
    }

    @Test
    void testLogin_ResponseMessage() {
        LoginRequest request = LoginRequest.builder()
            .usernameOrEmail("testuser")
            .password("Password123")
            .build();

        when(authService.login(any())).thenReturn(mockAuthResponse);

        ResponseEntity<ApiResponse<AuthResponse>> response = internalAuthController.login(request);

        assertEquals("Login successful", response.getBody().getMessage());
    }

    @Test
    void testRefreshToken_ResponseMessage() {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
            .refreshToken("valid_token")
            .build();

        when(authService.refreshToken(any())).thenReturn(mockAuthResponse);

        ResponseEntity<ApiResponse<AuthResponse>> response = internalAuthController.refreshToken(request);

        assertEquals("Token refreshed successfully", response.getBody().getMessage());
    }

    @Test
    void testLogout_ResponseMessage() {
        ResponseEntity<ApiResponse<Void>> response = internalAuthController.logout("token");
        assertEquals("Logged out successfully", response.getBody().getMessage());
    }

    @Test
    void testLogoutAll_ResponseMessage() {
        ResponseEntity<ApiResponse<Void>> response = internalAuthController.logoutAll(1L);
        assertEquals("All sessions logged out successfully", response.getBody().getMessage());
    }

    @Test
    void testValidateToken_ResponseMessage() {
        when(tokenProvider.validateToken("token")).thenReturn(false);
        ResponseEntity<ApiResponse<InternalAuthController.TokenValidationResponse>> response =
            internalAuthController.validateToken("token");
        assertEquals("Token validation complete", response.getBody().getMessage());
    }
}
