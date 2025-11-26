package com.arcana.cloud.repository;

import com.arcana.cloud.entity.OAuthToken;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(com.arcana.cloud.config.TestCacheConfig.class)
class OAuthTokenRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OAuthTokenRepository tokenRepository;

    private User testUser;
    private OAuthToken testToken;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();

        testUser = User.builder()
            .username("testuser")
            .email("test@example.com")
            .password("encoded_password")
            .firstName("Test")
            .lastName("User")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(false)
            .build();

        entityManager.persist(testUser);

        testToken = OAuthToken.builder()
            .user(testUser)
            .accessToken("access_token_123")
            .refreshToken("refresh_token_456")
            .tokenType("Bearer")
            .expiresAt(now.plusHours(1))
            .refreshExpiresAt(now.plusDays(30))
            .isRevoked(false)
            .build();

        entityManager.persist(testToken);
        entityManager.flush();
    }

    @Test
    void testFindByAccessToken_Found() {
        Optional<OAuthToken> found = tokenRepository.findByAccessToken("access_token_123");

        assertTrue(found.isPresent());
        assertEquals("access_token_123", found.get().getAccessToken());
        assertEquals(testUser.getId(), found.get().getUser().getId());
    }

    @Test
    void testFindByAccessToken_NotFound() {
        Optional<OAuthToken> found = tokenRepository.findByAccessToken("nonexistent_token");

        assertFalse(found.isPresent());
    }

    @Test
    void testFindByRefreshToken_Found() {
        Optional<OAuthToken> found = tokenRepository.findByRefreshToken("refresh_token_456");

        assertTrue(found.isPresent());
        assertEquals("refresh_token_456", found.get().getRefreshToken());
    }

    @Test
    void testFindByRefreshToken_NotFound() {
        Optional<OAuthToken> found = tokenRepository.findByRefreshToken("nonexistent_refresh_token");

        assertFalse(found.isPresent());
    }

    @Test
    void testFindByUserAndIsRevokedFalse() {
        // Add another non-revoked token
        OAuthToken activeToken = OAuthToken.builder()
            .user(testUser)
            .accessToken("another_access_token")
            .refreshToken("another_refresh_token")
            .expiresAt(now.plusHours(1))
            .refreshExpiresAt(now.plusDays(30))
            .isRevoked(false)
            .build();
        entityManager.persist(activeToken);

        // Add a revoked token
        OAuthToken revokedToken = OAuthToken.builder()
            .user(testUser)
            .accessToken("revoked_access_token")
            .refreshToken("revoked_refresh_token")
            .expiresAt(now.plusHours(1))
            .refreshExpiresAt(now.plusDays(30))
            .isRevoked(true)
            .build();
        entityManager.persist(revokedToken);
        entityManager.flush();

        List<OAuthToken> activeTokens = tokenRepository.findByUserAndIsRevokedFalse(testUser);

        assertEquals(2, activeTokens.size());
        assertTrue(activeTokens.stream().noneMatch(OAuthToken::getIsRevoked));
    }

    @Test
    void testRevokeAllTokensByUser() {
        // Add another token for the same user
        OAuthToken anotherToken = OAuthToken.builder()
            .user(testUser)
            .accessToken("another_access_token")
            .refreshToken("another_refresh_token")
            .expiresAt(now.plusHours(1))
            .refreshExpiresAt(now.plusDays(30))
            .isRevoked(false)
            .build();
        entityManager.persist(anotherToken);
        entityManager.flush();

        tokenRepository.revokeAllTokensByUser(testUser);
        entityManager.clear();

        List<OAuthToken> activeTokens = tokenRepository.findByUserAndIsRevokedFalse(testUser);
        assertEquals(0, activeTokens.size());
    }

    @Test
    void testRevokeByAccessToken() {
        assertFalse(testToken.getIsRevoked());

        tokenRepository.revokeByAccessToken("access_token_123");
        entityManager.clear();

        Optional<OAuthToken> found = tokenRepository.findByAccessToken("access_token_123");
        assertTrue(found.isPresent());
        assertTrue(found.get().getIsRevoked());
    }

    @Test
    void testDeleteExpiredOrRevokedTokens() {
        // Add an expired token
        OAuthToken expiredToken = OAuthToken.builder()
            .user(testUser)
            .accessToken("expired_access_token")
            .refreshToken("expired_refresh_token")
            .expiresAt(now.minusHours(1))  // Expired
            .refreshExpiresAt(now.minusDays(1))
            .isRevoked(false)
            .build();
        entityManager.persist(expiredToken);

        // Add a revoked token
        OAuthToken revokedToken = OAuthToken.builder()
            .user(testUser)
            .accessToken("revoked_access_token")
            .refreshToken("revoked_refresh_token")
            .expiresAt(now.plusHours(1))
            .refreshExpiresAt(now.plusDays(30))
            .isRevoked(true)
            .build();
        entityManager.persist(revokedToken);
        entityManager.flush();

        long countBefore = tokenRepository.count();
        assertEquals(3, countBefore);

        tokenRepository.deleteExpiredOrRevokedTokens(now);
        entityManager.clear();

        long countAfter = tokenRepository.count();
        assertEquals(1, countAfter);
    }

    @Test
    void testFindValidTokensByUser() {
        // Add a valid token
        OAuthToken validToken = OAuthToken.builder()
            .user(testUser)
            .accessToken("valid_access_token")
            .refreshToken("valid_refresh_token")
            .expiresAt(now.plusHours(1))
            .refreshExpiresAt(now.plusDays(30))
            .isRevoked(false)
            .build();
        entityManager.persist(validToken);

        // Add an expired token
        OAuthToken expiredToken = OAuthToken.builder()
            .user(testUser)
            .accessToken("expired_access_token")
            .refreshToken("expired_refresh_token")
            .expiresAt(now.minusHours(1))
            .refreshExpiresAt(now.minusDays(1))
            .isRevoked(false)
            .build();
        entityManager.persist(expiredToken);

        // Add a revoked token
        OAuthToken revokedToken = OAuthToken.builder()
            .user(testUser)
            .accessToken("revoked_access_token")
            .refreshToken("revoked_refresh_token")
            .expiresAt(now.plusHours(1))
            .refreshExpiresAt(now.plusDays(30))
            .isRevoked(true)
            .build();
        entityManager.persist(revokedToken);
        entityManager.flush();

        List<OAuthToken> validTokens = tokenRepository.findValidTokensByUser(testUser, now);

        // Should include testToken and validToken, but not expired or revoked
        assertEquals(2, validTokens.size());
        assertTrue(validTokens.stream().noneMatch(OAuthToken::getIsRevoked));
        assertTrue(validTokens.stream().allMatch(t -> t.getExpiresAt().isAfter(now)));
    }

    @Test
    void testMultipleUsersTokens() {
        User anotherUser = User.builder()
            .username("anotheruser")
            .email("another@example.com")
            .password("encoded_password")
            .role(UserRole.USER)
            .isActive(true)
            .build();
        entityManager.persist(anotherUser);

        OAuthToken anotherUserToken = OAuthToken.builder()
            .user(anotherUser)
            .accessToken("another_user_access_token")
            .refreshToken("another_user_refresh_token")
            .expiresAt(now.plusHours(1))
            .refreshExpiresAt(now.plusDays(30))
            .isRevoked(false)
            .build();
        entityManager.persist(anotherUserToken);
        entityManager.flush();

        List<OAuthToken> testUserTokens = tokenRepository.findByUserAndIsRevokedFalse(testUser);
        List<OAuthToken> anotherUserTokens = tokenRepository.findByUserAndIsRevokedFalse(anotherUser);

        assertEquals(1, testUserTokens.size());
        assertEquals(1, anotherUserTokens.size());
        assertEquals(testUser.getId(), testUserTokens.get(0).getUser().getId());
        assertEquals(anotherUser.getId(), anotherUserTokens.get(0).getUser().getId());
    }

    @Test
    void testTokenSaveAndRetrieve() {
        OAuthToken newToken = OAuthToken.builder()
            .user(testUser)
            .accessToken("new_access_token")
            .refreshToken("new_refresh_token")
            .expiresAt(now.plusHours(2))
            .refreshExpiresAt(now.plusDays(60))
            .clientIp("192.168.1.1")
            .userAgent("TestBrowser/1.0")
            .build();

        OAuthToken savedToken = tokenRepository.save(newToken);
        entityManager.flush();
        entityManager.clear();

        Optional<OAuthToken> retrieved = tokenRepository.findByAccessToken("new_access_token");
        assertTrue(retrieved.isPresent());
        assertEquals("192.168.1.1", retrieved.get().getClientIp());
        assertEquals("TestBrowser/1.0", retrieved.get().getUserAgent());
    }
}
