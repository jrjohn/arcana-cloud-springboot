package com.arcana.cloud.controller;

import com.arcana.cloud.dto.request.UserUpdateRequest;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.exception.ResourceNotFoundException;
import com.arcana.cloud.mapper.UserMapper;
import com.arcana.cloud.repository.UserRepository;
import com.arcana.cloud.security.JwtTokenProvider;
import com.arcana.cloud.security.UserPrincipal;
import com.arcana.cloud.service.UserService;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Extended tests for UserController covering the updateUser (PUT /api/v1/users/{id}) endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerExtendedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @SuppressWarnings("unused")
    @Autowired
    private UserMapper userMapper;

    private String adminToken;
    private String userToken;
    private User testUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
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
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        adminUser = User.builder()
            .id(99L)
            .username("admin")
            .email("admin@example.com")
            .password("encoded_admin_password")
            .role(UserRole.ADMIN)
            .isActive(true)
            .isVerified(true)
            .build();

        // Mock repository for JWT authentication filter
        when(userRepository.findById(99L)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        adminToken = tokenProvider.generateAccessToken(UserPrincipal.create(adminUser));
        userToken  = tokenProvider.generateAccessToken(UserPrincipal.create(testUser));
    }

    // ==================== PUT /api/v1/users/{id} ====================

    @Test
    void testUpdateUser_AsAdmin_Success() throws Exception {
        UserUpdateRequest request = UserUpdateRequest.builder()
            .username("updateduser")
            .email("updated@example.com")
            .firstName("Updated")
            .lastName("Name")
            .isActive(true)
            .isVerified(true)
            .build();

        User updatedUser = User.builder()
            .id(1L)
            .username("updateduser")
            .email("updated@example.com")
            .firstName("Updated")
            .lastName("Name")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(userService.updateUser(anyLong(), any(User.class))).thenReturn(updatedUser);

        mockMvc.perform(put("/api/v1/users/1")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.username").value("updateduser"))
            .andExpect(jsonPath("$.data.email").value("updated@example.com"))
            .andExpect(jsonPath("$.message").value("User updated successfully"));
    }

    @Test
    void testUpdateUser_AsOwner_Success() throws Exception {
        // User updating their own profile (isOwner check passes)
        UserUpdateRequest request = UserUpdateRequest.builder()
            .firstName("NewFirst")
            .lastName("NewLast")
            .build();

        User updatedUser = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .firstName("NewFirst")
            .lastName("NewLast")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(false)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(userService.updateUser(anyLong(), any(User.class))).thenReturn(updatedUser);

        mockMvc.perform(put("/api/v1/users/1")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.firstName").value("NewFirst"));
    }

    @Test
    void testUpdateUser_AsUser_OtherUser_Forbidden() throws Exception {
        // Regular user trying to update someone else — @PreAuthorize denies it
        UserUpdateRequest request = UserUpdateRequest.builder()
            .firstName("Hacker")
            .build();

        mockMvc.perform(put("/api/v1/users/99")   // id=99 is adminUser, not the user token owner
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void testUpdateUser_Unauthorized_NoToken() throws Exception {
        UserUpdateRequest request = UserUpdateRequest.builder()
            .firstName("Test")
            .build();

        mockMvc.perform(put("/api/v1/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void testUpdateUser_UserNotFound_Returns404() throws Exception {
        UserUpdateRequest request = UserUpdateRequest.builder()
            .username("newname")
            .build();

        when(userService.updateUser(anyLong(), any(User.class)))
            .thenThrow(new ResourceNotFoundException("User", "id", 999L));

        mockMvc.perform(put("/api/v1/users/999")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testUpdateUser_ValidationError_ShortUsername() throws Exception {
        UserUpdateRequest request = UserUpdateRequest.builder()
            .username("ab")   // too short, min = 3
            .build();

        mockMvc.perform(put("/api/v1/users/1")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testUpdateUser_ValidationError_InvalidEmail() throws Exception {
        UserUpdateRequest request = UserUpdateRequest.builder()
            .email("not-a-valid-email")
            .build();

        mockMvc.perform(put("/api/v1/users/1")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testUpdateUser_OnlyPasswordUpdate() throws Exception {
        UserUpdateRequest request = UserUpdateRequest.builder()
            .password("NewPass1234")
            .build();

        User updatedUser = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .firstName("Test")
            .lastName("User")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(false)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(userService.updateUser(anyLong(), any(User.class))).thenReturn(updatedUser);

        mockMvc.perform(put("/api/v1/users/1")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("User updated successfully"));
    }

    @Test
    void testUpdateUser_DeactivateUser() throws Exception {
        UserUpdateRequest request = UserUpdateRequest.builder()
            .isActive(false)
            .build();

        User updatedUser = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .role(UserRole.USER)
            .isActive(false)
            .isVerified(false)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(userService.updateUser(anyLong(), any(User.class))).thenReturn(updatedUser);

        mockMvc.perform(put("/api/v1/users/1")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.isActive").value(false));
    }
}
