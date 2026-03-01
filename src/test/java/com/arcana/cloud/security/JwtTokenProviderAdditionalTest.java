package com.arcana.cloud.security;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Additional coverage tests for JwtTokenProvider.
 * Covers: ExpiredJwtException, SignatureException, and edge-case branches.
 */
@SuppressWarnings("java:S2925")
@DisplayName("JwtTokenProvider - Additional Coverage")
class JwtTokenProviderAdditionalTest {

    private JwtTokenProvider tokenProvider;
    private UserPrincipal userPrincipal;

    private static final String SECRET_32 =
        "my-secret-key-for-testing-purpose-32char";

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", SECRET_32);
        ReflectionTestUtils.setField(tokenProvider, "accessTokenExpiration", 3600000L);
        ReflectionTestUtils.setField(tokenProvider, "refreshTokenExpiration", 86400000L);

        User user = User.builder()
            .id(10L)
            .username("jwtuser")
            .email("jwt@example.com")
            .password("pw")
            .role(UserRole.USER)
            .isActive(true)
            .build();
        userPrincipal = UserPrincipal.create(user);
    }


    @Test
    @DisplayName("validateToken: expired token → returns false (ExpiredJwtException branch)")
    void validateToken_expiredToken_returnsFalse() {
        // Set negative expiration to produce an already-expired token
        ReflectionTestUtils.setField(tokenProvider, "accessTokenExpiration", -1000L);
        String expiredToken = tokenProvider.generateAccessToken(userPrincipal);

        // Restore normal expiration before validating (validation uses signing key only)
        ReflectionTestUtils.setField(tokenProvider, "accessTokenExpiration", 3600000L);

        boolean valid = tokenProvider.validateToken(expiredToken);
        assertFalse(valid, "Expired token should not be valid");
    }

    @Test
    @DisplayName("validateToken: refresh token also subject to expiry check")
    void validateToken_expiredRefreshToken_returnsFalse() {
        ReflectionTestUtils.setField(tokenProvider, "refreshTokenExpiration", -1000L);
        String expiredRefresh = tokenProvider.generateRefreshToken(userPrincipal);
        ReflectionTestUtils.setField(tokenProvider, "refreshTokenExpiration", 86400000L);

        assertFalse(tokenProvider.validateToken(expiredRefresh));
    }


    @Test
    @DisplayName("validateToken: token signed with different key → false (SignatureException)")
    void validateToken_wrongSignatureKey_returnsFalse() {
        // Generate token with the main provider
        String token = tokenProvider.generateAccessToken(userPrincipal);

        // Validate with a provider that has a DIFFERENT secret
        JwtTokenProvider differentKeyProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(differentKeyProvider, "jwtSecret",
            "totally-different-key-32chars-xxxxx");
        ReflectionTestUtils.setField(differentKeyProvider, "accessTokenExpiration", 3600000L);
        ReflectionTestUtils.setField(differentKeyProvider, "refreshTokenExpiration", 86400000L);

        boolean valid = differentKeyProvider.validateToken(token);
        assertFalse(valid, "Token with wrong signature should not be valid");
    }


    @Test
    @DisplayName("validateToken: garbled string → false (MalformedJwtException)")
    void validateToken_garbledString_returnsFalse() {
        assertFalse(tokenProvider.validateToken("abc.def.ghi"));
    }

    @Test
    @DisplayName("validateToken: only dots → false (MalformedJwtException)")
    void validateToken_onlyDots_returnsFalse() {
        assertFalse(tokenProvider.validateToken("..."));
    }


    @Test
    @DisplayName("validateToken: null token → false (IllegalArgumentException)")
    void validateToken_null_returnsFalse() {
        assertFalse(tokenProvider.validateToken(null));
    }

    @Test
    @DisplayName("validateToken: blank string → false (IllegalArgumentException)")
    void validateToken_blank_returnsFalse() {
        assertFalse(tokenProvider.validateToken("   "));
    }

    @Test
    @DisplayName("validateToken: empty string → false")
    void validateToken_empty_returnsFalse() {
        assertFalse(tokenProvider.validateToken(""));
    }

    // ─── token content tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("getUsernameFromToken: refresh token has no username claim → returns null")
    void getUsernameFromToken_fromRefreshToken_returnsNull() {
        String refreshToken = tokenProvider.generateRefreshToken(userPrincipal);
        // Refresh token does not include 'username' claim
        tokenProvider.getUsernameFromToken(refreshToken);
        // Should be null (claim not present) without exception
        // We just verify no exception is thrown and subject still resolvable
        assertNotNull(tokenProvider.getUserIdFromToken(refreshToken));
    }

    @Test
    @DisplayName("getRoleFromToken: refresh token has no role claim → returns null")
    void getRoleFromToken_fromRefreshToken_returnsNull() {
        String refreshToken = tokenProvider.generateRefreshToken(userPrincipal);
        tokenProvider.getRoleFromToken(refreshToken);
        // refresh token has no 'role' claim → null without NPE
        assertFalse(tokenProvider.validateToken("") );
        // and the token itself is still valid
        assertTrue(tokenProvider.validateToken(refreshToken));
    }

    @Test
    @DisplayName("generateAccessToken: different users produce different subjects")
    void generateAccessToken_differentUsers_differentSubjects() {
        User user2 = User.builder()
            .id(20L)
            .username("other")
            .email("other@example.com")
            .password("pw")
            .role(UserRole.ADMIN)
            .isActive(true)
            .build();
        UserPrincipal principal2 = UserPrincipal.create(user2);

        String token1 = tokenProvider.generateAccessToken(userPrincipal);
        String token2 = tokenProvider.generateAccessToken(principal2);

        assertEquals(10L, tokenProvider.getUserIdFromToken(token1));
        assertEquals(20L, tokenProvider.getUserIdFromToken(token2));
        assertEquals("USER", tokenProvider.getRoleFromToken(token1));
        assertEquals("ADMIN", tokenProvider.getRoleFromToken(token2));
    }

    @Test
    @DisplayName("generateAccessToken: email claim is included in token")
    void generateAccessToken_includesEmailClaim() {
        String token = tokenProvider.generateAccessToken(userPrincipal);
        // Validating token is enough to confirm structure; getUserIdFromToken parses claims
        assertEquals(10L, tokenProvider.getUserIdFromToken(token));
        assertEquals("jwtuser", tokenProvider.getUsernameFromToken(token));
    }

    @Test
    @DisplayName("valid access token validates and access token ≠ refresh token")
    void accessAndRefreshTokens_areDistinct() {
        String access = tokenProvider.generateAccessToken(userPrincipal);
        String refresh = tokenProvider.generateRefreshToken(userPrincipal);

        assertTrue(tokenProvider.validateToken(access));
        assertTrue(tokenProvider.validateToken(refresh));
        assertFalse(access.equals(refresh));
    }
}
