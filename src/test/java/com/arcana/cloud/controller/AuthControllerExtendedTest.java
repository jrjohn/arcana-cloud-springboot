package com.arcana.cloud.controller;

import com.arcana.cloud.dto.request.RefreshTokenRequest;
import com.arcana.cloud.dto.response.AuthResponse;
import com.arcana.cloud.dto.response.UserResponse;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.repository.UserRepository;
import com.arcana.cloud.security.JwtTokenProvider;
import com.arcana.cloud.security.UserPrincipal;
import com.arcana.cloud.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Extended tests for AuthController covering refresh, logout, and logoutAll endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerExtendedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserRepository userRepository;

    private AuthResponse mockAuthResponse;
    private String userToken;

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
            .isVerified(true)
            .build();

        mockAuthResponse = AuthResponse.builder()
            .accessToken("new-access-token")
            .refreshToken("new-refresh-token")
            .tokenType("Bearer")
            .expiresIn(3600L)
            .user(userResponse)
            .build();

        User user = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .password("encoded_password")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(true)
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        userToken = tokenProvider.generateAccessToken(UserPrincipal.create(user));
    }

    // ==================== /refresh ====================

    @Test
    void testRefreshToken_Success() throws Exception {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
            .refreshToken("valid-refresh-token")
            .build();

        when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(mockAuthResponse);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
            .andExpect(jsonPath("$.message").value("Token refreshed successfully"));
    }

    @Test
    void testRefreshToken_MissingToken_ValidationError() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        // refreshToken is null/empty → @NotBlank validation

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ==================== /logout ====================

    @Test
    void testLogout_WithBearerToken_Success() throws Exception {
        doNothing().when(authService).logout(anyString());

        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    @Test
    void testLogout_WithoutAuthorizationHeader_StillSucceeds() throws Exception {
        // No Authorization header — controller still returns 200 (no auth required on logout)
        mockMvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    @Test
    void testLogout_WithNonBearerAuthHeader_StillSucceeds() throws Exception {
        // Authorization header present but not "Bearer " prefix — controller skips logout call
        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Basic dXNlcjpwYXNz"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    // ==================== /logout-all ====================

    @Test
    void testLogoutAll_WhenAuthenticated_Success() throws Exception {
        doNothing().when(authService).logoutAll(anyLong());

        mockMvc.perform(post("/api/v1/auth/logout-all")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("All sessions logged out successfully"));
    }

    @Test
    void testLogoutAll_WhenNotAuthenticated_StillSucceeds() throws Exception {
        // No JWT → SecurityService.getCurrentUserId() returns null → controller skips logoutAll
        // But request is allowed because endpoint is accessible (depends on security config)
        mockMvc.perform(post("/api/v1/auth/logout-all"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("All sessions logged out successfully"));
    }
}
