package com.arcana.cloud.dao.impl.jpa.repository;

import com.arcana.cloud.entity.OAuthToken;
import com.arcana.cloud.entity.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Internal JPA repository for OAuthToken entity.
 * Used by OAuthTokenDaoJpaImpl when JPA mode is enabled.
 */
@Repository
@ConditionalOnProperty(name = "database.orm", havingValue = "jpa")
public interface OAuthTokenJpaRepository extends JpaRepository<OAuthToken, Long> {

    Optional<OAuthToken> findByAccessToken(String accessToken);

    Optional<OAuthToken> findByRefreshToken(String refreshToken);

    List<OAuthToken> findByUserAndIsRevokedFalse(User user);

    @Modifying
    @Query("UPDATE OAuthToken t SET t.isRevoked = true WHERE t.user = :user")
    void revokeAllTokensByUser(@Param("user") User user);

    @Modifying
    @Query("UPDATE OAuthToken t SET t.isRevoked = true WHERE t.accessToken = :accessToken")
    void revokeByAccessToken(@Param("accessToken") String accessToken);

    @Modifying
    @Query("DELETE FROM OAuthToken t WHERE t.expiresAt < :now OR t.isRevoked = true")
    void deleteExpiredOrRevokedTokens(@Param("now") LocalDateTime now);

    @Query("SELECT t FROM OAuthToken t WHERE t.user = :user AND t.isRevoked = false AND t.expiresAt > :now")
    List<OAuthToken> findValidTokensByUser(@Param("user") User user, @Param("now") LocalDateTime now);
}
