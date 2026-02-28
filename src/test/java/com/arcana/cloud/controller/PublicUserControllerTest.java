package com.arcana.cloud.controller;

import com.arcana.cloud.dto.response.UserResponse;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.security.JwtTokenProvider;
import com.arcana.cloud.security.UserPrincipal;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockitoBean
    private UserService userService;

    private String userToken;
    private User testUser;

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

        UserPrincipal userPrincipal = UserPrincipal.create(testUser);
        userToken = tokenProvider.generateAccessToken(userPrincipal);
    }

    @Test
    void testGetCurrentUser_WhenAuthenticated_ReturnsUserProfile() throws Exception {
        when(userService.getUserById(anyLong())).thenReturn(testUser);

        mockMvc.perform(get("/api/v1/me")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.username").value("testuser"))
            .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    void testGetCurrentUser_WhenNotAuthenticated_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
            .andExpect(status().isForbidden());
    }

    @Test
    void testGetCurrentUser_WhenUserServiceThrows_Propagates() throws Exception {
        when(userService.getUserById(anyLong()))
            .thenThrow(new com.arcana.cloud.exception.ResourceNotFoundException("User", "id", 1L));

        mockMvc.perform(get("/api/v1/me")
                .header("Authorization", "Bearer " + userToken))
            .andExpect(status().isNotFound());
    }
}
