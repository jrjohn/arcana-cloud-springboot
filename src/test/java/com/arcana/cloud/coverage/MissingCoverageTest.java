package com.arcana.cloud.coverage;

import com.arcana.cloud.dto.request.LoginRequest;
import com.arcana.cloud.dto.request.RegisterRequest;
import com.arcana.cloud.dto.request.RefreshTokenRequest;
import com.arcana.cloud.dto.response.AuthResponse;
import com.arcana.cloud.dto.response.UserResponse;
import com.arcana.cloud.entity.OAuthToken;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.exception.GlobalExceptionHandler;
import com.arcana.cloud.exception.SchedulerOperationException;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Fills coverage gaps: AuthServiceImpl + GlobalExceptionHandler missing handlers.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MissingCoverageTest {

    // ── AuthServiceImpl ────────────────────────────────────────────────────

    @Mock UserRepository userRepository;
    @Mock OAuthTokenRepository tokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider tokenProvider;
    @Mock UserMapper userMapper;
    @InjectMocks AuthServiceImpl authService;

    private User activeUser;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "accessTokenExpiration", 3600000L);
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", 2592000000L);

        activeUser = User.builder()
            .id(1L).username("alice").email("alice@example.com")
            .password("hashed").role(UserRole.USER)
            .isActive(true).isVerified(true).build();

        userResponse = UserResponse.builder()
            .id(1L).username("alice").email("alice@example.com")
            .role(UserRole.USER).isActive(true).build();
    }

    @Test
    void register_success() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("P@ss1!")).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(activeUser);
        when(tokenProvider.generateAccessToken(any())).thenReturn("at");
        when(tokenProvider.generateRefreshToken(any())).thenReturn("rt");
        when(tokenRepository.save(any())).thenReturn(OAuthToken.builder()
            .accessToken("at").refreshToken("rt")
            .expiresAt(LocalDateTime.now().plusHours(1))
            .refreshExpiresAt(LocalDateTime.now().plusDays(30)).build());
        when(userMapper.toResponse(any())).thenReturn(userResponse);

        RegisterRequest req = RegisterRequest.builder()
            .username("alice").email("alice@example.com")
            .password("P@ss1!").confirmPassword("P@ss1!")
            .firstName("Alice").lastName("Smith").build();

        AuthResponse resp = authService.register(req);
        assertNotNull(resp);
    }

    @Test
    void register_passwordMismatch() {
        RegisterRequest req = RegisterRequest.builder()
            .username("alice").email("a@b.com")
            .password("abc").confirmPassword("xyz").build();
        assertThrows(ValidationException.class, () -> authService.register(req));
    }

    @Test
    void register_usernameExists() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);
        RegisterRequest req = RegisterRequest.builder()
            .username("alice").email("a@b.com")
            .password("pw").confirmPassword("pw").build();
        assertThrows(ValidationException.class, () -> authService.register(req));
    }

    @Test
    void register_emailExists() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);
        RegisterRequest req = RegisterRequest.builder()
            .username("alice").email("alice@example.com")
            .password("pw").confirmPassword("pw").build();
        assertThrows(ValidationException.class, () -> authService.register(req));
    }

    @Test
    void login_success() {
        when(userRepository.findByUsernameOrEmail("alice", "alice")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("pw", "hashed")).thenReturn(true);
        when(tokenProvider.generateAccessToken(any())).thenReturn("at");
        when(tokenProvider.generateRefreshToken(any())).thenReturn("rt");
        when(tokenRepository.save(any())).thenReturn(OAuthToken.builder()
            .accessToken("at").refreshToken("rt")
            .expiresAt(LocalDateTime.now().plusHours(1))
            .refreshExpiresAt(LocalDateTime.now().plusDays(30)).build());
        when(userMapper.toResponse(any())).thenReturn(userResponse);

        AuthResponse resp = authService.login(
            LoginRequest.builder().usernameOrEmail("alice").password("pw").build());
        assertNotNull(resp);
    }

    @Test
    void login_invalidPassword() {
        when(userRepository.findByUsernameOrEmail("alice", "alice")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);
        assertThrows(UnauthorizedException.class,
            () -> authService.login(LoginRequest.builder().usernameOrEmail("alice").password("wrong").build()));
    }

    @Test
    void login_accountDisabled() {
        User disabled = User.builder().id(2L).username("bob").password("h")
            .isActive(false).build();
        when(userRepository.findByUsernameOrEmail("bob", "bob")).thenReturn(Optional.of(disabled));
        when(passwordEncoder.matches("pw", "h")).thenReturn(true);
        assertThrows(UnauthorizedException.class,
            () -> authService.login(LoginRequest.builder().usernameOrEmail("bob").password("pw").build()));
    }

    @Test
    void logout_success() {
        doNothing().when(tokenRepository).revokeByAccessToken("tok");
        authService.logout("tok");
        verify(tokenRepository).revokeByAccessToken("tok");
    }

    @Test
    void logoutAll_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        doNothing().when(tokenRepository).revokeAllTokensByUser(activeUser);
        authService.logoutAll(1L);
        verify(tokenRepository).revokeAllTokensByUser(activeUser);
    }

    @Test
    void refreshToken_success() {
        OAuthToken token = OAuthToken.builder()
            .id(1L).user(activeUser)
            .accessToken("old_at").refreshToken("rt")
            .isRevoked(false)
            .refreshExpiresAt(LocalDateTime.now().plusDays(1)).build();
        when(tokenProvider.validateToken("rt")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("rt")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(tokenRepository.findByRefreshToken("rt")).thenReturn(Optional.of(token));
        when(tokenProvider.generateAccessToken(any())).thenReturn("new_at");
        when(tokenProvider.generateRefreshToken(any())).thenReturn("new_rt");
        when(tokenRepository.save(any())).thenReturn(token);
        when(userMapper.toResponse(any())).thenReturn(userResponse);

        AuthResponse resp = authService.refreshToken(
            RefreshTokenRequest.builder().refreshToken("rt").build());
        assertNotNull(resp);
    }

    @Test
    void refreshToken_invalidToken() {
        when(tokenProvider.validateToken("bad_rt")).thenReturn(false);
        assertThrows(UnauthorizedException.class,
            () -> authService.refreshToken(RefreshTokenRequest.builder().refreshToken("bad_rt").build()));
    }

    // ── GlobalExceptionHandler - missing SchedulerOperation ────────────────

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final WebRequest mockRequest = mock(WebRequest.class);

    @Test
    void handler_schedulerOperationException() {
        SchedulerOperationException ex = new SchedulerOperationException("Scheduler failed", null);
        ResponseEntity<com.arcana.cloud.dto.response.ApiResponse<Void>> resp =
            handler.handleSchedulerOperation(ex, mockRequest);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        assertFalse(resp.getBody().isSuccess());
        assertEquals("Scheduler failed", resp.getBody().getMessage());
    }

    @Test
    void handler_schedulerOperationException_withCause() {
        SchedulerOperationException ex = new SchedulerOperationException(
            "Job failed", new RuntimeException("cause"));
        ResponseEntity<com.arcana.cloud.dto.response.ApiResponse<Void>> resp =
            handler.handleSchedulerOperation(ex, mockRequest);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
    }
}
