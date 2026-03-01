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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Full-coverage unit tests for AuthServiceImpl.
 * Uses Mockito only — no Spring context, no MySQL.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl - Full Coverage Tests")
class AuthServiceImplFullCoverageTest {

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

    private User activeUser;
    private User inactiveUser;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "accessTokenExpiration", 3600000L);
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", 2592000000L);

        activeUser = User.builder()
            .id(1L)
            .username("alice")
            .email("alice@example.com")
            .password("hashed_pw")
            .firstName("Alice")
            .lastName("Smith")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(true)
            .build();

        inactiveUser = User.builder()
            .id(2L)
            .username("bob")
            .email("bob@example.com")
            .password("hashed_pw")
            .role(UserRole.USER)
            .isActive(false)
            .isVerified(false)
            .build();

        userResponse = UserResponse.builder()
            .id(1L)
            .username("alice")
            .email("alice@example.com")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(true)
            .build();
    }

    // ─── register() ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: success path creates user, saves token, returns AuthResponse")
    void register_success() {
        RegisterRequest req = RegisterRequest.builder()
            .username("charlie")
            .email("charlie@example.com")
            .password("Secure123!")
            .confirmPassword("Secure123!")
            .firstName("Charlie")
            .lastName("Brown")
            .build();

        when(userRepository.existsByUsername("charlie")).thenReturn(false);
        when(userRepository.existsByEmail("charlie@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secure123!")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(activeUser);
        when(tokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("AT");
        when(tokenProvider.generateRefreshToken(any(UserPrincipal.class))).thenReturn("RT");
        when(tokenRepository.save(any(OAuthToken.class))).thenReturn(OAuthToken.builder().build());
        when(userMapper.toResponse(activeUser)).thenReturn(userResponse);

        AuthResponse resp = authService.register(req);

        assertNotNull(resp);
        assertEquals("AT", resp.getAccessToken());
        assertEquals("RT", resp.getRefreshToken());
        assertEquals("Bearer", resp.getTokenType());
        assertEquals(3600L, resp.getExpiresIn());
        verify(userRepository).save(any(User.class));
        verify(tokenRepository).save(any(OAuthToken.class));
    }

    @Test
    @DisplayName("register: passwords do not match → ValidationException before any DB call")
    void register_passwordMismatch() {
        RegisterRequest req = RegisterRequest.builder()
            .username("dave")
            .email("dave@example.com")
            .password("abc")
            .confirmPassword("xyz")
            .build();

        ValidationException ex = assertThrows(ValidationException.class,
            () -> authService.register(req));
        assertTrue(ex.getMessage().contains("Passwords do not match"));
        verify(userRepository, never()).existsByUsername(anyString());
    }

    @Test
    @DisplayName("register: username already taken → ValidationException")
    void register_usernameExists() {
        RegisterRequest req = RegisterRequest.builder()
            .username("alice")
            .email("alice2@example.com")
            .password("Same1!")
            .confirmPassword("Same1!")
            .build();

        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(ValidationException.class, () -> authService.register(req));
        verify(userRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("register: email already taken → ValidationException")
    void register_emailExists() {
        RegisterRequest req = RegisterRequest.builder()
            .username("newguy")
            .email("alice@example.com")
            .password("Same1!")
            .confirmPassword("Same1!")
            .build();

        when(userRepository.existsByUsername("newguy")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThrows(ValidationException.class, () -> authService.register(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register: new user is built with ROLE_USER, isActive=true, isVerified=false")
    void register_userBuiltCorrectly() {
        RegisterRequest req = RegisterRequest.builder()
            .username("newuser")
            .email("new@example.com")
            .password("Pass1!")
            .confirmPassword("Pass1!")
            .firstName("New")
            .lastName("User")
            .build();

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("enc");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            assertEquals(UserRole.USER, u.getRole());
            assertTrue(u.getIsActive());
            return activeUser;
        });
        when(tokenProvider.generateAccessToken(any())).thenReturn("a");
        when(tokenProvider.generateRefreshToken(any())).thenReturn("r");
        when(tokenRepository.save(any())).thenReturn(OAuthToken.builder().build());
        when(userMapper.toResponse(any())).thenReturn(userResponse);

        authService.register(req);

        verify(userRepository).save(any(User.class));
    }

    // ─── login() ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: success via username")
    void login_successByUsername() {
        LoginRequest req = LoginRequest.builder()
            .usernameOrEmail("alice")
            .password("plainPw")
            .build();

        when(userRepository.findByUsernameOrEmail("alice", "alice"))
            .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("plainPw", "hashed_pw")).thenReturn(true);
        when(tokenProvider.generateAccessToken(any())).thenReturn("AT");
        when(tokenProvider.generateRefreshToken(any())).thenReturn("RT");
        when(tokenRepository.save(any())).thenReturn(OAuthToken.builder().build());
        when(userMapper.toResponse(activeUser)).thenReturn(userResponse);

        AuthResponse resp = authService.login(req);

        assertEquals("AT", resp.getAccessToken());
        assertEquals("RT", resp.getRefreshToken());
    }

    @Test
    @DisplayName("login: success via email")
    void login_successByEmail() {
        LoginRequest req = LoginRequest.builder()
            .usernameOrEmail("alice@example.com")
            .password("plainPw")
            .build();

        when(userRepository.findByUsernameOrEmail("alice@example.com", "alice@example.com"))
            .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("plainPw", "hashed_pw")).thenReturn(true);
        when(tokenProvider.generateAccessToken(any())).thenReturn("AT");
        when(tokenProvider.generateRefreshToken(any())).thenReturn("RT");
        when(tokenRepository.save(any())).thenReturn(OAuthToken.builder().build());
        when(userMapper.toResponse(activeUser)).thenReturn(userResponse);

        AuthResponse resp = authService.login(req);

        assertNotNull(resp);
    }

    @Test
    @DisplayName("login: user not found → UnauthorizedException")
    void login_userNotFound() {
        LoginRequest req = LoginRequest.builder()
            .usernameOrEmail("nobody")
            .password("pw")
            .build();

        when(userRepository.findByUsernameOrEmail(anyString(), anyString()))
            .thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.login(req));
    }

    @Test
    @DisplayName("login: wrong password → UnauthorizedException")
    void login_wrongPassword() {
        LoginRequest req = LoginRequest.builder()
            .usernameOrEmail("alice")
            .password("wrong")
            .build();

        when(userRepository.findByUsernameOrEmail("alice", "alice"))
            .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrong", "hashed_pw")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.login(req));
    }

    @Test
    @DisplayName("login: account disabled → UnauthorizedException")
    void login_accountDisabled() {
        LoginRequest req = LoginRequest.builder()
            .usernameOrEmail("bob")
            .password("pw")
            .build();

        when(userRepository.findByUsernameOrEmail("bob", "bob"))
            .thenReturn(Optional.of(inactiveUser));
        when(passwordEncoder.matches("pw", "hashed_pw")).thenReturn(true);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
            () -> authService.login(req));
        assertTrue(ex.getMessage().contains("disabled"));
    }

    // ─── refreshToken() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("refreshToken: success → revokes old token, issues new tokens")
    void refreshToken_success() {
        OAuthToken existing = OAuthToken.builder()
            .id(10L)
            .user(activeUser)
            .accessToken("old_AT")
            .refreshToken("valid_RT")
            .isRevoked(false)
            .expiresAt(LocalDateTime.now().plusHours(1))
            .refreshExpiresAt(LocalDateTime.now().plusDays(30))
            .build();

        RefreshTokenRequest req = RefreshTokenRequest.builder()
            .refreshToken("valid_RT")
            .build();

        when(tokenProvider.validateToken("valid_RT")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("valid_RT")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(tokenRepository.findByRefreshToken("valid_RT")).thenReturn(Optional.of(existing));
        when(tokenRepository.save(any(OAuthToken.class))).thenReturn(existing);
        when(tokenProvider.generateAccessToken(any())).thenReturn("new_AT");
        when(tokenProvider.generateRefreshToken(any())).thenReturn("new_RT");
        when(userMapper.toResponse(activeUser)).thenReturn(userResponse);

        AuthResponse resp = authService.refreshToken(req);

        assertEquals("new_AT", resp.getAccessToken());
        assertEquals("new_RT", resp.getRefreshToken());

        // verify old token was marked revoked
        ArgumentCaptor<OAuthToken> cap = ArgumentCaptor.forClass(OAuthToken.class);
        verify(tokenRepository, times(2)).save(cap.capture());
        assertTrue(cap.getAllValues().get(0).getIsRevoked());
    }

    @Test
    @DisplayName("refreshToken: invalid token → UnauthorizedException")
    void refreshToken_invalidToken() {
        RefreshTokenRequest req = RefreshTokenRequest.builder()
            .refreshToken("bad_RT")
            .build();

        when(tokenProvider.validateToken("bad_RT")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(req));
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("refreshToken: user not found → UnauthorizedException")
    void refreshToken_userNotFound() {
        RefreshTokenRequest req = RefreshTokenRequest.builder()
            .refreshToken("valid_RT")
            .build();

        when(tokenProvider.validateToken("valid_RT")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("valid_RT")).thenReturn(99L);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(req));
    }

    @Test
    @DisplayName("refreshToken: user account disabled → UnauthorizedException")
    void refreshToken_accountDisabled() {
        RefreshTokenRequest req = RefreshTokenRequest.builder()
            .refreshToken("valid_RT")
            .build();

        when(tokenProvider.validateToken("valid_RT")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("valid_RT")).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(inactiveUser));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
            () -> authService.refreshToken(req));
        assertTrue(ex.getMessage().contains("disabled"));
    }

    @Test
    @DisplayName("refreshToken: token not in DB → UnauthorizedException")
    void refreshToken_tokenNotFound() {
        RefreshTokenRequest req = RefreshTokenRequest.builder()
            .refreshToken("valid_RT")
            .build();

        when(tokenProvider.validateToken("valid_RT")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("valid_RT")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(tokenRepository.findByRefreshToken("valid_RT")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(req));
    }

    @Test
    @DisplayName("refreshToken: token already revoked → UnauthorizedException")
    void refreshToken_tokenRevoked() {
        OAuthToken revoked = OAuthToken.builder()
            .id(10L)
            .refreshToken("valid_RT")
            .isRevoked(true)
            .build();

        RefreshTokenRequest req = RefreshTokenRequest.builder()
            .refreshToken("valid_RT")
            .build();

        when(tokenProvider.validateToken("valid_RT")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("valid_RT")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(tokenRepository.findByRefreshToken("valid_RT")).thenReturn(Optional.of(revoked));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
            () -> authService.refreshToken(req));
        assertTrue(ex.getMessage().contains("revoked"));
    }

    // ─── logout() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("logout: delegates to tokenRepository.revokeByAccessToken")
    void logout_success() {
        authService.logout("some_AT");

        verify(tokenRepository).revokeByAccessToken("some_AT");
    }

    @Test
    @DisplayName("logout: empty token string still delegates")
    void logout_emptyToken() {
        authService.logout("");
        verify(tokenRepository).revokeByAccessToken("");
    }

    // ─── logoutAll() ────────────────────────────────────────────────────────

    @Test
    @DisplayName("logoutAll: success → revokes all tokens for user")
    void logoutAll_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        authService.logoutAll(1L);

        verify(tokenRepository).revokeAllTokensByUser(activeUser);
    }

    @Test
    @DisplayName("logoutAll: user not found → ValidationException")
    void logoutAll_userNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        ValidationException ex = assertThrows(ValidationException.class,
            () -> authService.logoutAll(999L));
        assertTrue(ex.getMessage().contains("not found"));
        verify(tokenRepository, never()).revokeAllTokensByUser(any());
    }

    @Test
    @DisplayName("logoutAll: inactive user can still be logged out")
    void logoutAll_inactiveUser() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(inactiveUser));

        authService.logoutAll(2L);

        verify(tokenRepository).revokeAllTokensByUser(inactiveUser);
    }

    // ─── generateAuthResponse (indirect) ────────────────────────────────────

    @Test
    @DisplayName("generateAuthResponse: OAuthToken expiresAt/refreshExpiresAt are set correctly")
    void generateAuthResponse_tokenDatesAreSet() {
        RegisterRequest req = RegisterRequest.builder()
            .username("frank")
            .email("frank@example.com")
            .password("P@ss1!")
            .confirmPassword("P@ss1!")
            .build();

        ArgumentCaptor<OAuthToken> tokenCaptor = ArgumentCaptor.forClass(OAuthToken.class);

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("enc");
        when(userRepository.save(any(User.class))).thenReturn(activeUser);
        when(tokenProvider.generateAccessToken(any())).thenReturn("A");
        when(tokenProvider.generateRefreshToken(any())).thenReturn("R");
        when(tokenRepository.save(tokenCaptor.capture()))
            .thenReturn(OAuthToken.builder().build());
        when(userMapper.toResponse(any())).thenReturn(userResponse);

        authService.register(req);

        OAuthToken saved = tokenCaptor.getValue();
        assertNotNull(saved.getExpiresAt());
        assertNotNull(saved.getRefreshExpiresAt());
        assertEquals(activeUser, saved.getUser());
        assertEquals("A", saved.getAccessToken());
        assertEquals("R", saved.getRefreshToken());
    }
}
