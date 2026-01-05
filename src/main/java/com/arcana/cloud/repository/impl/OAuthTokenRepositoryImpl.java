package com.arcana.cloud.repository.impl;

import com.arcana.cloud.dao.interfaces.OAuthTokenDao;
import com.arcana.cloud.entity.OAuthToken;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.repository.interfaces.OAuthTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository implementation for OAuthToken entity.
 * Delegates all operations to the OAuthTokenDao interface.
 * The actual DAO implementation (MyBatis, JPA, or MongoDB) is selected by configuration.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class OAuthTokenRepositoryImpl implements OAuthTokenRepository {

    private final OAuthTokenDao tokenDao;

    @Override
    public OAuthToken save(OAuthToken token) {
        log.debug("Repository: Saving OAuth token");
        return tokenDao.save(token);
    }

    @Override
    public List<OAuthToken> saveAll(Iterable<OAuthToken> tokens) {
        return tokenDao.saveAll(tokens);
    }

    @Override
    public Optional<OAuthToken> findById(Long id) {
        log.debug("Repository: Finding token by id: {}", id);
        return tokenDao.findById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return tokenDao.existsById(id);
    }

    @Override
    public List<OAuthToken> findAll() {
        log.debug("Repository: Finding all tokens");
        return tokenDao.findAll();
    }

    @Override
    public Page<OAuthToken> findAll(Pageable pageable) {
        log.debug("Repository: Finding all tokens with pagination");
        return tokenDao.findAll(pageable);
    }

    @Override
    public long count() {
        return tokenDao.count();
    }

    @Override
    public void deleteById(Long id) {
        log.info("Repository: Deleting token by id: {}", id);
        tokenDao.deleteById(id);
    }

    @Override
    public void delete(OAuthToken token) {
        tokenDao.delete(token);
    }

    @Override
    public void deleteAll() {
        log.warn("Repository: Deleting all tokens");
        tokenDao.deleteAll();
    }

    @Override
    public Optional<OAuthToken> findByAccessToken(String accessToken) {
        log.debug("Repository: Finding token by access token");
        return tokenDao.findByAccessToken(accessToken);
    }

    @Override
    public Optional<OAuthToken> findByRefreshToken(String refreshToken) {
        log.debug("Repository: Finding token by refresh token");
        return tokenDao.findByRefreshToken(refreshToken);
    }

    @Override
    public List<OAuthToken> findByUserAndIsRevokedFalse(User user) {
        log.debug("Repository: Finding non-revoked tokens for user: {}", user.getId());
        return tokenDao.findByUserAndIsRevokedFalse(user);
    }

    @Override
    public void revokeAllTokensByUser(User user) {
        log.info("Repository: Revoking all tokens for user: {}", user.getId());
        tokenDao.revokeAllTokensByUser(user);
    }

    @Override
    public void revokeByAccessToken(String accessToken) {
        log.info("Repository: Revoking token by access token");
        tokenDao.revokeByAccessToken(accessToken);
    }

    @Override
    public void deleteExpiredOrRevokedTokens(LocalDateTime now) {
        log.info("Repository: Deleting expired or revoked tokens");
        tokenDao.deleteExpiredOrRevokedTokens(now);
    }

    @Override
    public List<OAuthToken> findValidTokensByUser(User user, LocalDateTime now) {
        log.debug("Repository: Finding valid tokens for user: {}", user.getId());
        return tokenDao.findValidTokensByUser(user, now);
    }
}
