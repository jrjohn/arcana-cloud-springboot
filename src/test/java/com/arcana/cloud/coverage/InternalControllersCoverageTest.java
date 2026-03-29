package com.arcana.cloud.coverage;

import com.arcana.cloud.controller.internal.InternalAuthController;
import com.arcana.cloud.controller.internal.InternalUserController;
import com.arcana.cloud.dto.response.ApiResponse;
import com.arcana.cloud.dto.response.PagedResponse;
import com.arcana.cloud.dto.response.UserResponse;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.dto.request.LoginRequest;
import com.arcana.cloud.dto.request.RefreshTokenRequest;
import com.arcana.cloud.dto.request.RegisterRequest;
import com.arcana.cloud.dto.response.AuthResponse;
import com.arcana.cloud.mapper.UserMapper;
import com.arcana.cloud.security.JwtTokenProvider;
import com.arcana.cloud.service.AuthService;
import com.arcana.cloud.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalControllersCoverageTest {

    // ── InternalAuthController ─────────────────────────────────────────────
    @Mock AuthService authService;
    @Mock JwtTokenProvider tokenProvider;
    @InjectMocks InternalAuthController internalAuthController;

    // ── InternalUserController ─────────────────────────────────────────────
    @Mock UserService userService;
    @Mock UserMapper userMapper;
    @InjectMocks InternalUserController internalUserController;

    private AuthResponse mockAuthResponse;
    private UserResponse mockUserResponse;
    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUserResponse = UserResponse.builder()
            .id(1L).username("alice").email("alice@example.com")
            .firstName("Alice").lastName("Smith")
            .role(UserRole.USER).isActive(true).isVerified(true).build();

        mockAuthResponse = AuthResponse.builder()
            .accessToken("access_tok").refreshToken("refresh_tok")
            .tokenType("Bearer").expiresIn(3600L).user(mockUserResponse).build();

        mockUser = User.builder()
            .id(1L).username("alice").email("alice@example.com")
            .firstName("Alice").lastName("Smith")
            .role(UserRole.USER).isActive(true).isVerified(true).build();
    }

    // ── Auth: register ──────────────────────────────────────────────────────

    @Test
    void auth_register_success() {
        when(authService.register(any())).thenReturn(mockAuthResponse);
        RegisterRequest req = RegisterRequest.builder()
            .username("alice").email("alice@example.com")
            .password("P@ss1!").confirmPassword("P@ss1!")
            .firstName("Alice").lastName("Smith").build();
        ResponseEntity<ApiResponse<AuthResponse>> resp = internalAuthController.register(req);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertTrue(resp.getBody().isSuccess());
    }

    @Test
    void auth_login_success() {
        when(authService.login(any())).thenReturn(mockAuthResponse);
        LoginRequest req = LoginRequest.builder()
            .usernameOrEmail("alice").password("P@ss1!").build();
        ResponseEntity<ApiResponse<AuthResponse>> resp = internalAuthController.login(req);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void auth_refreshToken_success() {
        when(authService.refreshToken(any())).thenReturn(mockAuthResponse);
        RefreshTokenRequest req = RefreshTokenRequest.builder()
            .refreshToken("refresh_tok").build();
        ResponseEntity<ApiResponse<AuthResponse>> resp = internalAuthController.refreshToken(req);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void auth_logout_success() {
        doNothing().when(authService).logout(anyString());
        ResponseEntity<ApiResponse<Void>> resp = internalAuthController.logout("tok");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void auth_logoutAll_success() {
        doNothing().when(authService).logoutAll(anyLong());
        ResponseEntity<ApiResponse<Void>> resp = internalAuthController.logoutAll(1L);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void auth_validateToken_valid() {
        when(tokenProvider.validateToken("vtok")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("vtok")).thenReturn(1L);
        when(tokenProvider.getUsernameFromToken("vtok")).thenReturn("alice");
        when(tokenProvider.getRoleFromToken("vtok")).thenReturn("USER");
        ResponseEntity<ApiResponse<InternalAuthController.TokenValidationResponse>> resp =
            internalAuthController.validateToken("vtok");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().getData().isValid());
        assertEquals("alice", resp.getBody().getData().getUsername());
    }

    @Test
    void auth_validateToken_invalid() {
        when(tokenProvider.validateToken("bad")).thenReturn(false);
        ResponseEntity<ApiResponse<InternalAuthController.TokenValidationResponse>> resp =
            internalAuthController.validateToken("bad");
        assertFalse(resp.getBody().getData().isValid());
        assertNull(resp.getBody().getData().getUsername());
    }

    // ── User: getUser ───────────────────────────────────────────────────────

    @Test
    void user_getUser_success() {
        when(userService.getUserById(1L)).thenReturn(mockUser);
        when(userMapper.toResponse(mockUser)).thenReturn(mockUserResponse);
        ResponseEntity<ApiResponse<UserResponse>> resp = internalUserController.getUser(1L);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("alice", resp.getBody().getData().getUsername());
    }

    @Test
    void user_getUserByUsername_found() {
        when(userService.findByUsername("alice")).thenReturn(Optional.of(mockUser));
        when(userMapper.toResponse(mockUser)).thenReturn(mockUserResponse);
        ResponseEntity<ApiResponse<UserResponse>> resp = internalUserController.getUserByUsername("alice");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void user_getUserByUsername_notFound() {
        when(userService.findByUsername("nobody")).thenReturn(Optional.empty());
        ResponseEntity<ApiResponse<UserResponse>> resp = internalUserController.getUserByUsername("nobody");
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void user_getUserByEmail_found() {
        when(userService.findByEmail("alice@example.com")).thenReturn(Optional.of(mockUser));
        when(userMapper.toResponse(mockUser)).thenReturn(mockUserResponse);
        ResponseEntity<ApiResponse<UserResponse>> resp = internalUserController.getUserByEmail("alice@example.com");
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void user_getUserByEmail_notFound() {
        when(userService.findByEmail("x@x.com")).thenReturn(Optional.empty());
        ResponseEntity<ApiResponse<UserResponse>> resp = internalUserController.getUserByEmail("x@x.com");
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void user_listUsers_success() {
        var page = new PageImpl<>(List.of(mockUser), PageRequest.of(0, 10), 1);
        when(userService.getUsers(0, 10)).thenReturn(page);
        when(userMapper.toResponse(mockUser)).thenReturn(mockUserResponse);
        ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> resp =
            internalUserController.listUsers(0, 10);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().getData().getContent().size());
    }

    @Test
    void user_createUser_success() {
        when(userService.createUser(any())).thenReturn(mockUser);
        when(userMapper.toResponse(mockUser)).thenReturn(mockUserResponse);
        InternalUserController.UserCreateDto dto = new InternalUserController.UserCreateDto();
        dto.setUsername("alice"); dto.setEmail("alice@example.com");
        dto.setPassword("pw"); dto.setFirstName("Alice"); dto.setLastName("Smith");
        ResponseEntity<ApiResponse<UserResponse>> resp = internalUserController.createUser(dto);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    @Test
    void user_updateUser_success() {
        when(userService.updateUser(eq(1L), any())).thenReturn(mockUser);
        when(userMapper.toResponse(mockUser)).thenReturn(mockUserResponse);
        InternalUserController.UserUpdateDto dto = new InternalUserController.UserUpdateDto();
        dto.setFirstName("Bob"); dto.setIsActive(true); dto.setIsVerified(true);
        ResponseEntity<ApiResponse<UserResponse>> resp = internalUserController.updateUser(1L, dto);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void user_deleteUser_success() {
        doNothing().when(userService).deleteUser(1L);
        ResponseEntity<ApiResponse<Void>> resp = internalUserController.deleteUser(1L);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void user_existsByUsername_true() {
        when(userService.existsByUsername("alice")).thenReturn(true);
        ResponseEntity<ApiResponse<InternalUserController.ExistsResponse>> resp =
            internalUserController.existsByUsername("alice");
        assertTrue(resp.getBody().getData().isExists());
    }

    @Test
    void user_existsByEmail_false() {
        when(userService.existsByEmail("x@x.com")).thenReturn(false);
        ResponseEntity<ApiResponse<InternalUserController.ExistsResponse>> resp =
            internalUserController.existsByEmail("x@x.com");
        assertFalse(resp.getBody().getData().isExists());
    }
}
