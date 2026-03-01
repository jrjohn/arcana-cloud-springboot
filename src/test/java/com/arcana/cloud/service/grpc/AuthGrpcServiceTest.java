package com.arcana.cloud.service.grpc;

import com.arcana.cloud.dto.response.AuthResponse;
import com.arcana.cloud.dto.response.UserResponse;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.exception.UnauthorizedException;
import com.arcana.cloud.grpc.LogoutAllRequest;
import com.arcana.cloud.grpc.LogoutRequest;
import com.arcana.cloud.grpc.LogoutResponse;
import com.arcana.cloud.grpc.ValidateTokenRequest;
import com.arcana.cloud.grpc.ValidateTokenResponse;
import com.arcana.cloud.security.JwtTokenProvider;
import com.arcana.cloud.service.AuthService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthGrpcServiceTest {

    @Mock
    private AuthService authService;

    @Mock
    private JwtTokenProvider tokenProvider;

    @InjectMocks
    private AuthGrpcService authGrpcService;

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
        com.arcana.cloud.grpc.RegisterRequest request = com.arcana.cloud.grpc.RegisterRequest.newBuilder()
            .setUsername("testuser")
            .setEmail("test@example.com")
            .setPassword("password")
            .setConfirmPassword("password")
            .setFirstName("Test")
            .setLastName("User")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<com.arcana.cloud.grpc.AuthResponse> responseObserver = mock(StreamObserver.class);

        when(authService.register(any())).thenReturn(mockAuthResponse);

        authGrpcService.register(request, responseObserver);

        ArgumentCaptor<com.arcana.cloud.grpc.AuthResponse> captor =
            ArgumentCaptor.forClass(com.arcana.cloud.grpc.AuthResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        com.arcana.cloud.grpc.AuthResponse grpcResponse = captor.getValue();
        assertNotNull(grpcResponse);
        assertEquals("access_token", grpcResponse.getAccessToken());
        assertEquals("refresh_token", grpcResponse.getRefreshToken());
        assertEquals("Bearer", grpcResponse.getTokenType());
    }

    @Test
    void testRegister_Error() {
        com.arcana.cloud.grpc.RegisterRequest request = com.arcana.cloud.grpc.RegisterRequest.newBuilder()
            .setUsername("testuser")
            .setEmail("test@example.com")
            .setPassword("password")
            .setConfirmPassword("password")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<com.arcana.cloud.grpc.AuthResponse> responseObserver = mock(StreamObserver.class);

        when(authService.register(any())).thenThrow(new RuntimeException("Registration failed"));

        authGrpcService.register(request, responseObserver);

        verify(responseObserver).onError(any());
    }

    @Test
    void testLogin_Success() {
        com.arcana.cloud.grpc.LoginRequest request = com.arcana.cloud.grpc.LoginRequest.newBuilder()
            .setUsernameOrEmail("testuser")
            .setPassword("password")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<com.arcana.cloud.grpc.AuthResponse> responseObserver = mock(StreamObserver.class);

        when(authService.login(any())).thenReturn(mockAuthResponse);

        authGrpcService.login(request, responseObserver);

        ArgumentCaptor<com.arcana.cloud.grpc.AuthResponse> captor =
            ArgumentCaptor.forClass(com.arcana.cloud.grpc.AuthResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertEquals("access_token", captor.getValue().getAccessToken());
    }

    @Test
    void testLogin_Error() {
        com.arcana.cloud.grpc.LoginRequest request = com.arcana.cloud.grpc.LoginRequest.newBuilder()
            .setUsernameOrEmail("testuser")
            .setPassword("wrong_password")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<com.arcana.cloud.grpc.AuthResponse> responseObserver = mock(StreamObserver.class);

        when(authService.login(any())).thenThrow(new UnauthorizedException("Invalid credentials"));

        authGrpcService.login(request, responseObserver);

        verify(responseObserver).onError(any());
    }

    @Test
    void testRefreshToken_Success() {
        com.arcana.cloud.grpc.RefreshTokenRequest request = com.arcana.cloud.grpc.RefreshTokenRequest.newBuilder()
            .setRefreshToken("valid_refresh_token")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<com.arcana.cloud.grpc.AuthResponse> responseObserver = mock(StreamObserver.class);

        when(authService.refreshToken(any())).thenReturn(mockAuthResponse);

        authGrpcService.refreshToken(request, responseObserver);

        ArgumentCaptor<com.arcana.cloud.grpc.AuthResponse> captor =
            ArgumentCaptor.forClass(com.arcana.cloud.grpc.AuthResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertEquals("access_token", captor.getValue().getAccessToken());
    }

    @Test
    void testRefreshToken_Error() {
        com.arcana.cloud.grpc.RefreshTokenRequest request = com.arcana.cloud.grpc.RefreshTokenRequest.newBuilder()
            .setRefreshToken("invalid_token")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<com.arcana.cloud.grpc.AuthResponse> responseObserver = mock(StreamObserver.class);

        when(authService.refreshToken(any())).thenThrow(new UnauthorizedException("Invalid token"));

        authGrpcService.refreshToken(request, responseObserver);

        verify(responseObserver).onError(any());
    }

    @Test
    void testLogout_Success() {
        LogoutRequest request = LogoutRequest.newBuilder()
            .setAccessToken("valid_access_token")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<LogoutResponse> responseObserver = mock(StreamObserver.class);

        authGrpcService.logout(request, responseObserver);

        ArgumentCaptor<LogoutResponse> captor = ArgumentCaptor.forClass(LogoutResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertTrue(captor.getValue().getSuccess());
        assertEquals("Logged out successfully", captor.getValue().getMessage());
    }

    @Test
    void testLogout_Error() {
        LogoutRequest request = LogoutRequest.newBuilder()
            .setAccessToken("valid_access_token")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<LogoutResponse> responseObserver = mock(StreamObserver.class);

        doThrow(new RuntimeException("Logout error")).when(authService).logout(anyString());

        authGrpcService.logout(request, responseObserver);

        verify(responseObserver).onError(any());
    }

    @Test
    void testLogoutAll_Success() {
        LogoutAllRequest request = LogoutAllRequest.newBuilder()
            .setUserId(1L)
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<LogoutResponse> responseObserver = mock(StreamObserver.class);

        authGrpcService.logoutAll(request, responseObserver);

        ArgumentCaptor<LogoutResponse> captor = ArgumentCaptor.forClass(LogoutResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertTrue(captor.getValue().getSuccess());
        assertEquals("All sessions logged out successfully", captor.getValue().getMessage());
    }

    @Test
    void testLogoutAll_Error() {
        LogoutAllRequest request = LogoutAllRequest.newBuilder()
            .setUserId(999L)
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<LogoutResponse> responseObserver = mock(StreamObserver.class);

        doThrow(new RuntimeException("User not found")).when(authService).logoutAll(999L);

        authGrpcService.logoutAll(request, responseObserver);

        verify(responseObserver).onError(any());
    }

    @Test
    void testValidateToken_ValidToken() {
        ValidateTokenRequest request = ValidateTokenRequest.newBuilder()
            .setToken("valid_token")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<ValidateTokenResponse> responseObserver = mock(StreamObserver.class);

        when(tokenProvider.validateToken("valid_token")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("valid_token")).thenReturn(1L);
        when(tokenProvider.getUsernameFromToken("valid_token")).thenReturn("testuser");
        when(tokenProvider.getRoleFromToken("valid_token")).thenReturn("USER");

        authGrpcService.validateToken(request, responseObserver);

        ArgumentCaptor<ValidateTokenResponse> captor = ArgumentCaptor.forClass(ValidateTokenResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        ValidateTokenResponse response = captor.getValue();
        assertTrue(response.getValid());
        assertEquals(1L, response.getUserId());
        assertEquals("testuser", response.getUsername());
        assertEquals("USER", response.getRole());
    }

    @Test
    void testValidateToken_InvalidToken() {
        ValidateTokenRequest request = ValidateTokenRequest.newBuilder()
            .setToken("invalid_token")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<ValidateTokenResponse> responseObserver = mock(StreamObserver.class);

        when(tokenProvider.validateToken("invalid_token")).thenReturn(false);

        authGrpcService.validateToken(request, responseObserver);

        ArgumentCaptor<ValidateTokenResponse> captor = ArgumentCaptor.forClass(ValidateTokenResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertFalse(captor.getValue().getValid());
    }

    @Test
    void testValidateToken_Exception() {
        ValidateTokenRequest request = ValidateTokenRequest.newBuilder()
            .setToken("error_token")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<ValidateTokenResponse> responseObserver = mock(StreamObserver.class);

        when(tokenProvider.validateToken("error_token")).thenThrow(new RuntimeException("Parse error"));

        authGrpcService.validateToken(request, responseObserver);

        // On exception, returns invalid response (not error)
        ArgumentCaptor<ValidateTokenResponse> captor = ArgumentCaptor.forClass(ValidateTokenResponse.class);
        verify(responseObserver).onNext(captor.capture());
        assertFalse(captor.getValue().getValid());
    }

    @Test
    void testValidateToken_ValidTokenWithNullFields() {
        ValidateTokenRequest request = ValidateTokenRequest.newBuilder()
            .setToken("valid_token")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<ValidateTokenResponse> responseObserver = mock(StreamObserver.class);

        when(tokenProvider.validateToken("valid_token")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("valid_token")).thenReturn(1L);
        when(tokenProvider.getUsernameFromToken("valid_token")).thenReturn(null);
        when(tokenProvider.getRoleFromToken("valid_token")).thenReturn(null);

        authGrpcService.validateToken(request, responseObserver);

        ArgumentCaptor<ValidateTokenResponse> captor = ArgumentCaptor.forClass(ValidateTokenResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        ValidateTokenResponse response = captor.getValue();
        assertTrue(response.getValid());
        assertEquals("", response.getUsername());
        assertEquals("", response.getRole());
    }

    @Test
    void testRegister_WithNullUserInAuthResponse() {
        com.arcana.cloud.grpc.RegisterRequest request = com.arcana.cloud.grpc.RegisterRequest.newBuilder()
            .setUsername("testuser")
            .setEmail("test@example.com")
            .setPassword("password")
            .setConfirmPassword("password")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<com.arcana.cloud.grpc.AuthResponse> responseObserver = mock(StreamObserver.class);

        AuthResponse responseWithNullUser = AuthResponse.builder()
            .accessToken("access_token")
            .refreshToken("refresh_token")
            .tokenType("Bearer")
            .expiresIn(3600L)
            .user(null)
            .build();

        when(authService.register(any())).thenReturn(responseWithNullUser);

        authGrpcService.register(request, responseObserver);

        verify(responseObserver).onNext(any());
        verify(responseObserver).onCompleted();
    }
}
