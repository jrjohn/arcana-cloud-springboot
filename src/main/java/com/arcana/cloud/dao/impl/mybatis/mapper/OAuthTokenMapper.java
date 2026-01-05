package com.arcana.cloud.dao.impl.mybatis.mapper;

import com.arcana.cloud.entity.OAuthToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MyBatis mapper interface for OAuthToken entity.
 * SQL mappings are defined in OAuthTokenMapper.xml.
 */
@Mapper
public interface OAuthTokenMapper {

    /**
     * Insert a new token.
     */
    int insert(OAuthToken token);

    /**
     * Update an existing token.
     */
    int update(OAuthToken token);

    /**
     * Find token by ID.
     */
    Optional<OAuthToken> findById(@Param("id") Long id);

    /**
     * Find token by access token.
     */
    Optional<OAuthToken> findByAccessToken(@Param("accessToken") String accessToken);

    /**
     * Find token by refresh token.
     */
    Optional<OAuthToken> findByRefreshToken(@Param("refreshToken") String refreshToken);

    /**
     * Find non-revoked tokens by user ID.
     */
    List<OAuthToken> findByUserIdAndIsRevokedFalse(@Param("userId") Long userId);

    /**
     * Find valid tokens by user ID.
     */
    List<OAuthToken> findValidTokensByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * Check if token exists by ID.
     */
    boolean existsById(@Param("id") Long id);

    /**
     * Find all tokens.
     */
    List<OAuthToken> findAll();

    /**
     * Find all tokens with pagination.
     */
    List<OAuthToken> findAllWithPagination(@Param("offset") long offset, @Param("limit") int limit);

    /**
     * Count all tokens.
     */
    long count();

    /**
     * Revoke all tokens by user ID.
     */
    int revokeAllTokensByUserId(@Param("userId") Long userId);

    /**
     * Revoke token by access token.
     */
    int revokeByAccessToken(@Param("accessToken") String accessToken);

    /**
     * Delete expired or revoked tokens.
     */
    int deleteExpiredOrRevokedTokens(@Param("now") LocalDateTime now);

    /**
     * Delete token by ID.
     */
    int deleteById(@Param("id") Long id);

    /**
     * Delete all tokens.
     */
    int deleteAll();
}
