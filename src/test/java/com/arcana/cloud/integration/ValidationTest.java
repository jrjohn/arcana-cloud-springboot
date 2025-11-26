package com.arcana.cloud.integration;

import com.arcana.cloud.dto.request.LoginRequest;
import com.arcana.cloud.dto.request.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for input validation across all endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    // ============ Registration Validation Tests ============

    @Test
    void testRegister_UsernameRequired() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
            .email("test@example.com")
            .password("Password123")
            .confirmPassword("Password123")
            .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testRegister_UsernameTooShort() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
            .username("ab")
            .email("test@example.com")
            .password("Password123")
            .confirmPassword("Password123")
            .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testRegister_UsernameTooLong() throws Exception {
        String longUsername = "a".repeat(51);
        RegisterRequest request = RegisterRequest.builder()
            .username(longUsername)
            .email("test@example.com")
            .password("Password123")
            .confirmPassword("Password123")
            .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testRegister_EmailRequired() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
            .username("validuser")
            .password("Password123")
            .confirmPassword("Password123")
            .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testRegister_InvalidEmailFormat() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
            .username("validuser")
            .email("invalid-email")
            .password("Password123")
            .confirmPassword("Password123")
            .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testRegister_PasswordRequired() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
            .username("validuser")
            .email("test@example.com")
            .confirmPassword("Password123")
            .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testRegister_PasswordTooShort() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
            .username("validuser")
            .email("test@example.com")
            .password("short")
            .confirmPassword("short")
            .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testRegister_ConfirmPasswordRequired() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
            .username("validuser")
            .email("test@example.com")
            .password("Password123")
            .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testRegister_PasswordMismatch() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
            .username("validuser")
            .email("test@example.com")
            .password("Password123")
            .confirmPassword("DifferentPassword")
            .firstName("Test")
            .lastName("User")
            .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testRegister_EmptyBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testRegister_DuplicateUsername() throws Exception {
        String uniqueUsername = "duplicateuser" + System.currentTimeMillis();

        // First registration
        RegisterRequest request = RegisterRequest.builder()
            .username(uniqueUsername)
            .email(uniqueUsername + "@example.com")
            .password("Password123")
            .confirmPassword("Password123")
            .firstName("Duplicate")
            .lastName("User")
            .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        // Second registration with same username
        RegisterRequest duplicateRequest = RegisterRequest.builder()
            .username(uniqueUsername)
            .email("different@example.com")
            .password("Password123")
            .confirmPassword("Password123")
            .firstName("Duplicate")
            .lastName("User")
            .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(duplicateRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testRegister_DuplicateEmail() throws Exception {
        String uniqueEmail = "duplicate" + System.currentTimeMillis() + "@example.com";

        // First registration
        RegisterRequest request = RegisterRequest.builder()
            .username("unique1" + System.currentTimeMillis())
            .email(uniqueEmail)
            .password("Password123")
            .confirmPassword("Password123")
            .firstName("Duplicate")
            .lastName("Email")
            .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        // Second registration with same email
        RegisterRequest duplicateRequest = RegisterRequest.builder()
            .username("unique2" + System.currentTimeMillis())
            .email(uniqueEmail)
            .password("Password123")
            .confirmPassword("Password123")
            .firstName("Duplicate")
            .lastName("Email")
            .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(duplicateRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ============ Login Validation Tests ============

    @Test
    void testLogin_UsernameOrEmailRequired() throws Exception {
        LoginRequest request = LoginRequest.builder()
            .password("Password123")
            .build();

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testLogin_PasswordRequired() throws Exception {
        LoginRequest request = LoginRequest.builder()
            .usernameOrEmail("testuser")
            .build();

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testLogin_EmptyBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testLogin_InvalidCredentials() throws Exception {
        LoginRequest request = LoginRequest.builder()
            .usernameOrEmail("nonexistentuser")
            .password("WrongPassword123")
            .build();

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testLogin_WrongPassword() throws Exception {
        // First register a user
        String uniqueUsername = "wrongpassuser" + System.currentTimeMillis();
        RegisterRequest registerRequest = RegisterRequest.builder()
            .username(uniqueUsername)
            .email(uniqueUsername + "@example.com")
            .password("CorrectPassword123")
            .confirmPassword("CorrectPassword123")
            .firstName("Wrong")
            .lastName("Password")
            .build();

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated());

        // Then try to login with wrong password
        LoginRequest loginRequest = LoginRequest.builder()
            .usernameOrEmail(uniqueUsername)
            .password("WrongPassword123")
            .build();

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ============ Refresh Token Validation Tests ============

    @Test
    void testRefreshToken_TokenRequired() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testRefreshToken_InvalidToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"invalid.token.here\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

}
