package com.arcana.cloud.security;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("java:S2925")
class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();

        // Set properties via reflection
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret",
            "test-secret-key-for-testing-only-must-be-32-chars");
        ReflectionTestUtils.setField(tokenProvider, "accessTokenExpiration", 3600000L);
        ReflectionTestUtils.setField(tokenProvider, "refreshTokenExpiration", 86400000L);

        User user = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .password("encoded_password")
            .role(UserRole.USER)
            .isActive(true)
            .build();

        userPrincipal = UserPrincipal.create(user);
    }

    @Test
    void testGenerateAccessToken() {
        String token = tokenProvider.generateAccessToken(userPrincipal);

        assertNotNull(token);
        assertTrue(!token.isEmpty());
        assertTrue(token.contains("."));  // JWT format
    }

    @Test
    void testGenerateRefreshToken() {
        String token = tokenProvider.generateRefreshToken(userPrincipal);

        assertNotNull(token);
        assertTrue(!token.isEmpty());
        assertTrue(token.contains("."));
    }

    @Test
    void testValidateToken_ValidToken() {
        String token = tokenProvider.generateAccessToken(userPrincipal);

        boolean isValid = tokenProvider.validateToken(token);

        assertTrue(isValid);
    }

    @Test
    void testValidateToken_InvalidToken() {
        boolean isValid = tokenProvider.validateToken("invalid.token.here");

        assertFalse(isValid);
    }

    @Test
    void testValidateToken_MalformedToken() {
        boolean isValid = tokenProvider.validateToken("not-a-jwt-token");

        assertFalse(isValid);
    }

    @Test
    void testValidateToken_EmptyToken() {
        boolean isValid = tokenProvider.validateToken("");

        assertFalse(isValid);
    }

    @Test
    void testGetUserIdFromToken() {
        String token = tokenProvider.generateAccessToken(userPrincipal);

        Long userId = tokenProvider.getUserIdFromToken(token);

        assertEquals(1L, userId);
    }

    @Test
    void testGetUsernameFromToken() {
        String token = tokenProvider.generateAccessToken(userPrincipal);

        String username = tokenProvider.getUsernameFromToken(token);

        assertEquals("testuser", username);
    }

    @Test
    void testGetRoleFromToken() {
        String token = tokenProvider.generateAccessToken(userPrincipal);

        String role = tokenProvider.getRoleFromToken(token);

        assertEquals("USER", role);
    }

    @Test
    void testTokenForAdminUser() {
        User adminUser = User.builder()
            .id(2L)
            .username("admin")
            .email("admin@example.com")
            .password("encoded_password")
            .role(UserRole.ADMIN)
            .isActive(true)
            .build();

        UserPrincipal adminPrincipal = UserPrincipal.create(adminUser);
        String token = tokenProvider.generateAccessToken(adminPrincipal);

        String role = tokenProvider.getRoleFromToken(token);

        assertEquals("ADMIN", role);
    }

    @Test
    void testAccessAndRefreshTokensAreDifferent() {
        String accessToken = tokenProvider.generateAccessToken(userPrincipal);
        String refreshToken = tokenProvider.generateRefreshToken(userPrincipal);

        assertFalse(accessToken.equals(refreshToken));
    }

    @Test
    void testMultipleTokensForSameUser() throws InterruptedException {
        String token1 = tokenProvider.generateAccessToken(userPrincipal);
        // Small delay to ensure different timestamp
        Thread.sleep(10);
        String token2 = tokenProvider.generateAccessToken(userPrincipal);

        // Both should be valid
        assertTrue(tokenProvider.validateToken(token1));
        assertTrue(tokenProvider.validateToken(token2));
        // Tokens may be the same if generated in the same millisecond
        // but the important thing is that both are valid
    }

    @Test
    void testRefreshTokenIsValid() {
        String refreshToken = tokenProvider.generateRefreshToken(userPrincipal);

        assertTrue(tokenProvider.validateToken(refreshToken));
    }

    @Test
    void testGetUserIdFromRefreshToken() {
        String refreshToken = tokenProvider.generateRefreshToken(userPrincipal);

        Long userId = tokenProvider.getUserIdFromToken(refreshToken);

        assertEquals(1L, userId);
    }

    @Test
    void testTokenForDifferentUsers() {
        User user1 = User.builder()
            .id(1L)
            .username("user1")
            .email("user1@example.com")
            .password("password")
            .role(UserRole.USER)
            .isActive(true)
            .build();

        User user2 = User.builder()
            .id(2L)
            .username("user2")
            .email("user2@example.com")
            .password("password")
            .role(UserRole.ADMIN)
            .isActive(true)
            .build();

        String token1 = tokenProvider.generateAccessToken(UserPrincipal.create(user1));
        String token2 = tokenProvider.generateAccessToken(UserPrincipal.create(user2));

        assertEquals(1L, tokenProvider.getUserIdFromToken(token1));
        assertEquals(2L, tokenProvider.getUserIdFromToken(token2));
        assertEquals("user1", tokenProvider.getUsernameFromToken(token1));
        assertEquals("user2", tokenProvider.getUsernameFromToken(token2));
        assertEquals("USER", tokenProvider.getRoleFromToken(token1));
        assertEquals("ADMIN", tokenProvider.getRoleFromToken(token2));
    }

    @Test
    void testValidateToken_NullToken() {
        boolean isValid = tokenProvider.validateToken(null);

        assertFalse(isValid);
    }
}
