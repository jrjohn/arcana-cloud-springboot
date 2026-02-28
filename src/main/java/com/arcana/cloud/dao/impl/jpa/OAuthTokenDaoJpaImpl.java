package com.arcana.cloud.dao.impl.jpa;

import com.arcana.cloud.dao.impl.jpa.repository.OAuthTokenJpaRepository;
import com.arcana.cloud.dao.OAuthTokenDao;
import com.arcana.cloud.entity.OAuthToken;
import com.arcana.cloud.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA implementation of OAuthTokenDao.
 * Active when database.orm is 'jpa'.
 * Works with MySQL and PostgreSQL databases.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
@Transactional
@ConditionalOnProperty(name = "database.orm", havingValue = "jpa")
public class OAuthTokenDaoJpaImpl implements OAuthTokenDao {

    private final OAuthTokenJpaRepository tokenJpaRepository;

    @Override
    public OAuthToken save(OAuthToken token) {
        log.debug("JPA DAO: Saving OAuth token");
        return tokenJpaRepository.save(token);
    }

    @Override
    public List<OAuthToken> saveAll(Iterable<OAuthToken> entities) {
        return tokenJpaRepository.saveAll(entities);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OAuthToken> findById(Long id) {
        log.debug("JPA DAO: Finding token by id: {}", id);
        return tokenJpaRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return tokenJpaRepository.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OAuthToken> findAll() {
        log.debug("JPA DAO: Finding all tokens");
        return tokenJpaRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OAuthToken> findAll(Pageable pageable) {
        log.debug("JPA DAO: Finding all tokens with pagination");
        return tokenJpaRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return tokenJpaRepository.count();
    }

    @Override
    public void deleteById(Long id) {
        log.info("JPA DAO: Deleting token by id: {}", id);
        tokenJpaRepository.deleteById(id);
    }

    @Override
    public void delete(OAuthToken entity) {
        tokenJpaRepository.delete(entity);
    }

    @Override
    public void deleteAll() {
        log.warn("JPA DAO: Deleting all tokens");
        tokenJpaRepository.deleteAll();
    }

    @Override
    public void deleteAll(Iterable<OAuthToken> entities) {
        tokenJpaRepository.deleteAll(entities);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OAuthToken> findByAccessToken(String accessToken) {
        log.debug("JPA DAO: Finding token by access token");
        return tokenJpaRepository.findByAccessToken(accessToken);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OAuthToken> findByRefreshToken(String refreshToken) {
        log.debug("JPA DAO: Finding token by refresh token");
        return tokenJpaRepository.findByRefreshToken(refreshToken);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OAuthToken> findByUserAndIsRevokedFalse(User user) {
        log.debug("JPA DAO: Finding non-revoked tokens for user: {}", user.getId());
        return tokenJpaRepository.findByUserAndIsRevokedFalse(user);
    }

    @Override
    public void revokeAllTokensByUser(User user) {
        log.info("JPA DAO: Revoking all tokens for user: {}", user.getId());
        tokenJpaRepository.revokeAllTokensByUser(user);
    }

    @Override
    public void revokeByAccessToken(String accessToken) {
        log.info("JPA DAO: Revoking token by access token");
        tokenJpaRepository.revokeByAccessToken(accessToken);
    }

    @Override
    public void deleteExpiredOrRevokedTokens(LocalDateTime now) {
        log.info("JPA DAO: Deleting expired or revoked tokens");
        tokenJpaRepository.deleteExpiredOrRevokedTokens(now);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OAuthToken> findValidTokensByUser(User user, LocalDateTime now) {
        log.debug("JPA DAO: Finding valid tokens for user: {}", user.getId());
        return tokenJpaRepository.findValidTokensByUser(user, now);
    }
}
