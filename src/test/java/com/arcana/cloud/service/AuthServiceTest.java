package com.arcana.cloud.service;

import com.arcana.cloud.dto.request.LoginRequest;
import com.arcana.cloud.dto.request.RefreshTokenRequest;
import com.arcana.cloud.dto.request.RegisterRequest;
import com.arcana.cloud.dto.response.AuthResponse;
import com.arcana.cloud.dto.response.UserResponse;
import com.arcana.cloud.entity.OAuthToken;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.exception.UnauthorizedException;
import com.arcana.cloud.exception.ValidationException;
import com.arcana.cloud.mapper.UserMapper;
import com.arcana.cloud.repository.OAuthTokenRepository;
import com.arcana.cloud.repository.UserRepository;
import com.arcana.cloud.security.JwtTokenProvider;
import com.arcana.cloud.security.UserPrincipal;
import com.arcana.cloud.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OAuthTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;
    private UserResponse testUserResponse;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        // Set the expiration values using reflection
        ReflectionTestUtils.setField(authService, "accessTokenExpiration", 3600000L);
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", 2592000000L);

        testUser = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .password("encoded_password")
            .firstName("Test")
            .lastName("User")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(false)
            .build();

        testUserResponse = UserResponse.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .firstName("Test")
            .lastName("User")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(false)
            .build();

        registerRequest = RegisterRequest.builder()
            .username("newuser")
            .email("new@example.com")
            .password("Password123")
            .confirmPassword("Password123")
            .firstName("New")
            .lastName("User")
            .build();

        loginRequest = LoginRequest.builder()
            .usernameOrEmail("testuser")
            .password("Password123")
            .build();
    }

    @Test
    void testRegister_Success() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_password");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(tokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("access_token");
        when(tokenProvider.generateRefreshToken(any(UserPrincipal.class))).thenReturn("refresh_token");
        when(tokenRepository.save(any(OAuthToken.class))).thenReturn(OAuthToken.builder().build());
        when(userMapper.toResponse(any(User.class))).thenReturn(testUserResponse);

        AuthResponse response = authService.register(registerRequest);

        assertNotNull(response);
        assertEquals("access_token", response.getAccessToken());
        assertEquals("refresh_token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        verify(userRepository, times(1)).save(any(User.class));
        verify(tokenRepository, times(1)).save(any(OAuthToken.class));
    }

    @Test
    void testRegister_PasswordMismatch() {
        RegisterRequest badRequest = RegisterRequest.builder()
            .username("newuser")
            .email("new@example.com")
            .password("Password123")
            .confirmPassword("DifferentPassword123")
            .build();

        assertThrows(ValidationException.class, () -> authService.register(badRequest));
    }

    @Test
    void testRegister_UsernameExists() {
        when(userRepository.existsByUsername(anyString())).thenReturn(true);

        assertThrows(ValidationException.class, () -> authService.register(registerRequest));
    }

    @Test
    void testRegister_EmailExists() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThrows(ValidationException.class, () -> authService.register(registerRequest));
    }

    @Test
    void testLogin_Success() {
        when(userRepository.findByUsernameOrEmail(anyString(), anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(tokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("access_token");
        when(tokenProvider.generateRefreshToken(any(UserPrincipal.class))).thenReturn("refresh_token");
        when(tokenRepository.save(any(OAuthToken.class))).thenReturn(OAuthToken.builder().build());
        when(userMapper.toResponse(any(User.class))).thenReturn(testUserResponse);

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("access_token", response.getAccessToken());
        assertEquals("refresh_token", response.getRefreshToken());
    }

    @Test
    void testLogin_UserNotFound() {
        when(userRepository.findByUsernameOrEmail(anyString(), anyString())).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.login(loginRequest));
    }

    @Test
    void testLogin_InvalidPassword() {
        when(userRepository.findByUsernameOrEmail(anyString(), anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.login(loginRequest));
    }

    @Test
    void testLogin_AccountDisabled() {
        User disabledUser = User.builder()
            .id(1L)
            .username("disableduser")
            .email("disabled@example.com")
            .password("encoded_password")
            .isActive(false)
            .build();

        when(userRepository.findByUsernameOrEmail(anyString(), anyString())).thenReturn(Optional.of(disabledUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThrows(UnauthorizedException.class, () -> authService.login(loginRequest));
    }

    @Test
    void testLogin_ByEmail() {
        LoginRequest emailLoginRequest = LoginRequest.builder()
            .usernameOrEmail("test@example.com")
            .password("Password123")
            .build();

        when(userRepository.findByUsernameOrEmail(anyString(), anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(tokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("access_token");
        when(tokenProvider.generateRefreshToken(any(UserPrincipal.class))).thenReturn("refresh_token");
        when(tokenRepository.save(any(OAuthToken.class))).thenReturn(OAuthToken.builder().build());
        when(userMapper.toResponse(any(User.class))).thenReturn(testUserResponse);

        AuthResponse response = authService.login(emailLoginRequest);

        assertNotNull(response);
        assertEquals("access_token", response.getAccessToken());
    }

    @Test
    void testRefreshToken_Success() {
        RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
            .refreshToken("valid_refresh_token")
            .build();

        OAuthToken existingToken = OAuthToken.builder()
            .id(1L)
            .user(testUser)
            .accessToken("old_access_token")
            .refreshToken("valid_refresh_token")
            .expiresAt(LocalDateTime.now().plusHours(1))
            .refreshExpiresAt(LocalDateTime.now().plusDays(30))
            .isRevoked(false)
            .build();

        when(tokenProvider.validateToken(anyString())).thenReturn(true);
        when(tokenProvider.getUserIdFromToken(anyString())).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(tokenRepository.findByRefreshToken(anyString())).thenReturn(Optional.of(existingToken));
        when(tokenRepository.save(any(OAuthToken.class))).thenReturn(existingToken);
        when(tokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("new_access_token");
        when(tokenProvider.generateRefreshToken(any(UserPrincipal.class))).thenReturn("new_refresh_token");
        when(userMapper.toResponse(any(User.class))).thenReturn(testUserResponse);

        AuthResponse response = authService.refreshToken(refreshRequest);

        assertNotNull(response);
        assertEquals("new_access_token", response.getAccessToken());
        assertEquals("new_refresh_token", response.getRefreshToken());
    }

    @Test
    void testRefreshToken_InvalidToken() {
        RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
            .refreshToken("invalid_refresh_token")
            .build();

        when(tokenProvider.validateToken(anyString())).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(refreshRequest));
    }

    @Test
    void testRefreshToken_UserNotFound() {
        RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
            .refreshToken("valid_refresh_token")
            .build();

        when(tokenProvider.validateToken(anyString())).thenReturn(true);
        when(tokenProvider.getUserIdFromToken(anyString())).thenReturn(999L);
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(refreshRequest));
    }

    @Test
    void testRefreshToken_AccountDisabled() {
        RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
            .refreshToken("valid_refresh_token")
            .build();

        User disabledUser = User.builder()
            .id(1L)
            .username("disableduser")
            .email("disabled@example.com")
            .isActive(false)
            .build();

        when(tokenProvider.validateToken(anyString())).thenReturn(true);
        when(tokenProvider.getUserIdFromToken(anyString())).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(disabledUser));

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(refreshRequest));
    }

    @Test
    void testRefreshToken_TokenNotFound() {
        RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
            .refreshToken("valid_refresh_token")
            .build();

        when(tokenProvider.validateToken(anyString())).thenReturn(true);
        when(tokenProvider.getUserIdFromToken(anyString())).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(tokenRepository.findByRefreshToken(anyString())).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(refreshRequest));
    }

    @Test
    void testRefreshToken_TokenRevoked() {
        RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
            .refreshToken("revoked_refresh_token")
            .build();

        OAuthToken revokedToken = OAuthToken.builder()
            .id(1L)
            .user(testUser)
            .accessToken("access_token")
            .refreshToken("revoked_refresh_token")
            .isRevoked(true)
            .build();

        when(tokenProvider.validateToken(anyString())).thenReturn(true);
        when(tokenProvider.getUserIdFromToken(anyString())).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(tokenRepository.findByRefreshToken(anyString())).thenReturn(Optional.of(revokedToken));

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(refreshRequest));
    }

    @Test
    void testLogout_Success() {
        String accessToken = "valid_access_token";

        authService.logout(accessToken);

        verify(tokenRepository, times(1)).revokeByAccessToken(accessToken);
    }

    @Test
    void testLogoutAll_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        authService.logoutAll(1L);

        verify(tokenRepository, times(1)).revokeAllTokensByUser(testUser);
    }

    @Test
    void testLogoutAll_UserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ValidationException.class, () -> authService.logoutAll(999L));
    }

    @Test
    void testRegister_AdminRole() {
        // Even if a user tries to register, they should get USER role by default
        RegisterRequest adminRequest = RegisterRequest.builder()
            .username("adminwannabe")
            .email("admin@example.com")
            .password("Password123")
            .confirmPassword("Password123")
            .firstName("Admin")
            .lastName("Wannabe")
            .build();

        User savedUser = User.builder()
            .id(2L)
            .username("adminwannabe")
            .email("admin@example.com")
            .role(UserRole.USER)  // Should be USER, not ADMIN
            .isActive(true)
            .build();

        UserResponse savedUserResponse = UserResponse.builder()
            .id(2L)
            .username("adminwannabe")
            .email("admin@example.com")
            .role(UserRole.USER)
            .build();

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(tokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("access_token");
        when(tokenProvider.generateRefreshToken(any(UserPrincipal.class))).thenReturn("refresh_token");
        when(tokenRepository.save(any(OAuthToken.class))).thenReturn(OAuthToken.builder().build());
        when(userMapper.toResponse(any(User.class))).thenReturn(savedUserResponse);

        AuthResponse response = authService.register(adminRequest);

        assertNotNull(response);
        assertEquals(UserRole.USER, response.getUser().getRole());
    }
}
