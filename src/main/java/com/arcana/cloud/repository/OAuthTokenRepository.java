package com.arcana.cloud.repository;

import com.arcana.cloud.entity.OAuthToken;
import com.arcana.cloud.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for OAuthToken entity.
 * This interface is used by the Service layer and remains unchanged.
 * Implementations delegate to DAO layer for database operations.
 */
public interface OAuthTokenRepository {

    /**
     * Save a token (create or update).
     */
    OAuthToken save(OAuthToken token);

    /**
     * Save all tokens.
     */
    List<OAuthToken> saveAll(Iterable<OAuthToken> tokens);

    /**
     * Find token by ID.
     */
    Optional<OAuthToken> findById(Long id);

    /**
     * Check if token exists by ID.
     */
    boolean existsById(Long id);

    /**
     * Find all tokens.
     */
    List<OAuthToken> findAll();

    /**
     * Find all tokens with pagination.
     */
    Page<OAuthToken> findAll(Pageable pageable);

    /**
     * Count all tokens.
     */
    long count();

    /**
     * Delete token by ID.
     */
    void deleteById(Long id);

    /**
     * Delete token.
     */
    void delete(OAuthToken token);

    /**
     * Delete all tokens.
     */
    void deleteAll();

    /**
     * Find token by access token.
     */
    Optional<OAuthToken> findByAccessToken(String accessToken);

    /**
     * Find token by refresh token.
     */
    Optional<OAuthToken> findByRefreshToken(String refreshToken);

    /**
     * Find non-revoked tokens for a user.
     */
    List<OAuthToken> findByUserAndIsRevokedFalse(User user);

    /**
     * Revoke all tokens for a user.
     */
    void revokeAllTokensByUser(User user);

    /**
     * Revoke token by access token.
     */
    void revokeByAccessToken(String accessToken);

    /**
     * Delete expired or revoked tokens.
     */
    void deleteExpiredOrRevokedTokens(LocalDateTime now);

    /**
     * Find valid tokens for a user.
     */
    List<OAuthToken> findValidTokensByUser(User user, LocalDateTime now);
}
