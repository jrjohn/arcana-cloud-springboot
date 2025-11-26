package com.arcana.cloud.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuthTokenTest {

    private OAuthToken token;
    private User user;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();

        user = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .password("encoded_password")
            .build();

        token = OAuthToken.builder()
            .id(1L)
            .user(user)
            .accessToken("access_token_123")
            .refreshToken("refresh_token_456")
            .tokenType("Bearer")
            .expiresAt(now.plusHours(1))
            .refreshExpiresAt(now.plusDays(30))
            .isRevoked(false)
            .createdAt(now)
            .clientIp("127.0.0.1")
            .userAgent("Test/1.0")
            .build();
    }

    @Test
    void testOAuthTokenBuilder() {
        assertNotNull(token);
        assertEquals(1L, token.getId());
        assertEquals(user, token.getUser());
        assertEquals("access_token_123", token.getAccessToken());
        assertEquals("refresh_token_456", token.getRefreshToken());
        assertEquals("Bearer", token.getTokenType());
        assertFalse(token.getIsRevoked());
        assertEquals("127.0.0.1", token.getClientIp());
        assertEquals("Test/1.0", token.getUserAgent());
    }

    @Test
    void testOAuthTokenDefaultValues() {
        OAuthToken defaultToken = OAuthToken.builder()
            .user(user)
            .accessToken("access")
            .refreshToken("refresh")
            .expiresAt(now.plusHours(1))
            .refreshExpiresAt(now.plusDays(30))
            .build();

        assertEquals("Bearer", defaultToken.getTokenType());
        assertFalse(defaultToken.getIsRevoked());
    }

    @Test
    void testOAuthTokenWithNullOptionalFields() {
        OAuthToken minimalToken = OAuthToken.builder()
            .user(user)
            .accessToken("access")
            .refreshToken("refresh")
            .expiresAt(now.plusHours(1))
            .refreshExpiresAt(now.plusDays(30))
            .build();

        assertNull(minimalToken.getId());
        assertNull(minimalToken.getCreatedAt());
        assertNull(minimalToken.getClientIp());
        assertNull(minimalToken.getUserAgent());
    }

    @Test
    void testOAuthTokenSetters() {
        token.setAccessToken("new_access_token");
        token.setRefreshToken("new_refresh_token");
        token.setIsRevoked(true);
        token.setClientIp("192.168.1.1");
        token.setUserAgent("NewAgent/2.0");

        assertEquals("new_access_token", token.getAccessToken());
        assertEquals("new_refresh_token", token.getRefreshToken());
        assertTrue(token.getIsRevoked());
        assertEquals("192.168.1.1", token.getClientIp());
        assertEquals("NewAgent/2.0", token.getUserAgent());
    }

    @Test
    void testOAuthTokenExpiration() {
        LocalDateTime expiresAt = now.plusHours(1);
        LocalDateTime refreshExpiresAt = now.plusDays(30);

        token.setExpiresAt(expiresAt);
        token.setRefreshExpiresAt(refreshExpiresAt);

        assertEquals(expiresAt, token.getExpiresAt());
        assertEquals(refreshExpiresAt, token.getRefreshExpiresAt());
    }

    @Test
    void testOAuthTokenEquality() {
        OAuthToken token1 = OAuthToken.builder()
            .id(1L)
            .user(user)
            .accessToken("access_token")
            .refreshToken("refresh_token")
            .expiresAt(now)
            .refreshExpiresAt(now.plusDays(30))
            .build();

        OAuthToken token2 = OAuthToken.builder()
            .id(1L)
            .user(user)
            .accessToken("access_token")
            .refreshToken("refresh_token")
            .expiresAt(now)
            .refreshExpiresAt(now.plusDays(30))
            .build();

        assertEquals(token1, token2);
        assertEquals(token1.hashCode(), token2.hashCode());
    }

    @Test
    void testOAuthTokenInequality() {
        OAuthToken token1 = OAuthToken.builder()
            .id(1L)
            .user(user)
            .accessToken("access_token_1")
            .refreshToken("refresh_token_1")
            .expiresAt(now)
            .refreshExpiresAt(now.plusDays(30))
            .build();

        OAuthToken token2 = OAuthToken.builder()
            .id(2L)
            .user(user)
            .accessToken("access_token_2")
            .refreshToken("refresh_token_2")
            .expiresAt(now)
            .refreshExpiresAt(now.plusDays(30))
            .build();

        assertNotEquals(token1, token2);
    }

    @Test
    void testOAuthTokenToString() {
        String tokenString = token.toString();

        assertTrue(tokenString.contains("access_token_123"));
        assertTrue(tokenString.contains("refresh_token_456"));
        assertTrue(tokenString.contains("Bearer"));
    }

    @Test
    void testOAuthTokenNoArgsConstructor() {
        OAuthToken emptyToken = new OAuthToken();

        assertNull(emptyToken.getId());
        assertNull(emptyToken.getUser());
        assertNull(emptyToken.getAccessToken());
        assertNull(emptyToken.getRefreshToken());
    }

    @Test
    void testOAuthTokenRevocation() {
        assertFalse(token.getIsRevoked());

        token.setIsRevoked(true);

        assertTrue(token.getIsRevoked());
    }

    @Test
    void testOAuthTokenUserRelationship() {
        assertEquals(user, token.getUser());
        assertEquals("testuser", token.getUser().getUsername());
        assertEquals("test@example.com", token.getUser().getEmail());
    }

    @Test
    void testOAuthTokenDifferentTokenTypes() {
        token.setTokenType("JWT");
        assertEquals("JWT", token.getTokenType());

        token.setTokenType("OAuth2");
        assertEquals("OAuth2", token.getTokenType());
    }
}
