package com.arcana.cloud.dao.impl.mybatis;

import com.arcana.cloud.dao.impl.mybatis.mapper.OAuthTokenMapper;
import com.arcana.cloud.dao.OAuthTokenDao;
import com.arcana.cloud.entity.OAuthToken;
import com.arcana.cloud.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MyBatis implementation of OAuthTokenDao.
 * Active when database.orm is 'mybatis' or not specified (default).
 * Works with MySQL and PostgreSQL databases.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
@Transactional
@ConditionalOnProperty(name = "database.orm", havingValue = "mybatis", matchIfMissing = true)
public class OAuthTokenDaoMybatisImpl implements OAuthTokenDao {

    private final OAuthTokenMapper tokenMapper;

    @Override
    public OAuthToken save(OAuthToken token) {
        log.debug("MyBatis DAO: Saving OAuth token");
        if (token.getId() == null) {
            tokenMapper.insert(token);
        } else {
            tokenMapper.update(token);
        }
        return token;
    }

    @Override
    public List<OAuthToken> saveAll(Iterable<OAuthToken> entities) {
        List<OAuthToken> result = new ArrayList<>();
        for (OAuthToken token : entities) {
            result.add(save(token));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OAuthToken> findById(Long id) {
        log.debug("MyBatis DAO: Finding token by id: {}", id);
        return tokenMapper.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return tokenMapper.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OAuthToken> findAll() {
        log.debug("MyBatis DAO: Finding all tokens");
        return tokenMapper.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OAuthToken> findAll(Pageable pageable) {
        log.debug("MyBatis DAO: Finding all tokens with pagination");
        long total = tokenMapper.count();
        List<OAuthToken> tokens = tokenMapper.findAllWithPagination(
                pageable.getOffset(),
                pageable.getPageSize()
        );
        return new PageImpl<>(tokens, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return tokenMapper.count();
    }

    @Override
    public void deleteById(Long id) {
        log.info("MyBatis DAO: Deleting token by id: {}", id);
        tokenMapper.deleteById(id);
    }

    @Override
    public void delete(OAuthToken entity) {
        if (entity.getId() != null) {
            deleteById(entity.getId());
        }
    }

    @Override
    public void deleteAll() {
        log.warn("MyBatis DAO: Deleting all tokens");
        tokenMapper.deleteAll();
    }

    @Override
    public void deleteAll(Iterable<OAuthToken> entities) {
        for (OAuthToken token : entities) {
            delete(token);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OAuthToken> findByAccessToken(String accessToken) {
        log.debug("MyBatis DAO: Finding token by access token");
        return tokenMapper.findByAccessToken(accessToken);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OAuthToken> findByRefreshToken(String refreshToken) {
        log.debug("MyBatis DAO: Finding token by refresh token");
        return tokenMapper.findByRefreshToken(refreshToken);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OAuthToken> findByUserAndIsRevokedFalse(User user) {
        log.debug("MyBatis DAO: Finding non-revoked tokens for user: {}", user.getId());
        return tokenMapper.findByUserIdAndIsRevokedFalse(user.getId());
    }

    @Override
    public void revokeAllTokensByUser(User user) {
        log.info("MyBatis DAO: Revoking all tokens for user: {}", user.getId());
        tokenMapper.revokeAllTokensByUserId(user.getId());
    }

    @Override
    public void revokeByAccessToken(String accessToken) {
        log.info("MyBatis DAO: Revoking token by access token");
        tokenMapper.revokeByAccessToken(accessToken);
    }

    @Override
    public void deleteExpiredOrRevokedTokens(LocalDateTime now) {
        log.info("MyBatis DAO: Deleting expired or revoked tokens");
        tokenMapper.deleteExpiredOrRevokedTokens(now);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OAuthToken> findValidTokensByUser(User user, LocalDateTime now) {
        log.debug("MyBatis DAO: Finding valid tokens for user: {}", user.getId());
        return tokenMapper.findValidTokensByUserId(user.getId(), now);
    }
}
