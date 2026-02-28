package com.arcana.cloud.controller;

import com.arcana.cloud.dto.request.UserCreateRequest;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.mapper.UserMapper;
import com.arcana.cloud.repository.UserRepository;
import com.arcana.cloud.security.JwtTokenProvider;
import com.arcana.cloud.security.UserPrincipal;
import com.arcana.cloud.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest {

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
            .build();

        // Mock the repository to return admin user for authentication
        when(userRepository.findById(99L)).thenReturn(Optional.of(adminUser));

        UserPrincipal adminPrincipal = UserPrincipal.create(adminUser);
        adminToken = tokenProvider.generateAccessToken(adminPrincipal);
    }

    @Test
    void testGetUsers_AsAdmin_Success() throws Exception {
        Page<User> usersPage = new PageImpl<>(List.of(testUser), PageRequest.of(0, 20), 1);
        when(userService.getUsers(anyInt(), anyInt())).thenReturn(usersPage);

        mockMvc.perform(get("/api/v1/users")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].username").value("testuser"));
    }

    @Test
    void testGetUsers_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isForbidden());
    }

    @Test
    void testGetUserById_AsAdmin_Success() throws Exception {
        when(userService.getUserById(1L)).thenReturn(testUser);

        mockMvc.perform(get("/api/v1/users/1")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.username").value("testuser"))
            .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    void testCreateUser_AsAdmin_Success() throws Exception {
        UserCreateRequest request = UserCreateRequest.builder()
            .username("newuser")
            .email("newuser@example.com")
            .password("Password123")
            .firstName("New")
            .lastName("User")
            .build();

        User createdUser = User.builder()
            .id(2L)
            .username("newuser")
            .email("newuser@example.com")
            .firstName("New")
            .lastName("User")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(false)
            .createdAt(LocalDateTime.now())
            .build();

        when(userService.createUser(any(User.class))).thenReturn(createdUser);

        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.username").value("newuser"));
    }

    @Test
    void testDeleteUser_AsAdmin_Success() throws Exception {
        mockMvc.perform(delete("/api/v1/users/1")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("User deleted successfully"));
    }
}
