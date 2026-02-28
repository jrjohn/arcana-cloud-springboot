package com.arcana.cloud.dao;

import com.arcana.cloud.entity.OAuthToken;
import com.arcana.cloud.entity.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for OAuthToken entity.
 * Provides database-agnostic access to OAuth token data.
 * Implementations can use MyBatis, JPA, or MongoDB.
 */
public interface OAuthTokenDao extends BaseDao<OAuthToken, Long> {

    /**
     * Find token by access token string.
     *
     * @param accessToken the access token
     * @return an Optional containing the token if found
     */
    Optional<OAuthToken> findByAccessToken(String accessToken);

    /**
     * Find token by refresh token string.
     *
     * @param refreshToken the refresh token
     * @return an Optional containing the token if found
     */
    Optional<OAuthToken> findByRefreshToken(String refreshToken);

    /**
     * Find non-revoked tokens for a user.
     *
     * @param user the user
     * @return list of non-revoked tokens
     */
    List<OAuthToken> findByUserAndIsRevokedFalse(User user);

    /**
     * Revoke all tokens for a user.
     *
     * @param user the user
     */
    void revokeAllTokensByUser(User user);

    /**
     * Revoke token by access token string.
     *
     * @param accessToken the access token
     */
    void revokeByAccessToken(String accessToken);

    /**
     * Delete expired or revoked tokens.
     *
     * @param now the current timestamp
     */
    void deleteExpiredOrRevokedTokens(LocalDateTime now);

    /**
     * Find valid (non-revoked, non-expired) tokens for a user.
     *
     * @param user the user
     * @param now the current timestamp
     * @return list of valid tokens
     */
    List<OAuthToken> findValidTokensByUser(User user, LocalDateTime now);
}
