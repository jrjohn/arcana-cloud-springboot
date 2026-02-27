package com.arcana.cloud.dao.impl.mongodb;

import com.arcana.cloud.dao.interfaces.OAuthTokenDao;
import com.arcana.cloud.dao.interfaces.UserDao;
import com.arcana.cloud.document.OAuthTokenDocument;
import com.arcana.cloud.entity.OAuthToken;
import com.arcana.cloud.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * MongoDB implementation of OAuthTokenDao.
 * Active when database.type is 'mongodb'.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "database.type", havingValue = "mongodb")
public class OAuthTokenDaoMongodbImpl implements OAuthTokenDao {

    private final MongoTemplate mongoTemplate;
    private final UserDao userDao;

    // Simple ID generator for legacy ID compatibility
    private static final AtomicLong idGenerator = new AtomicLong(1000000);

    @Override
    public OAuthToken save(OAuthToken entity) {
        log.debug("MongoDB DAO: Saving OAuth token");

        OAuthTokenDocument document = OAuthTokenDocument.fromEntity(entity);

        // Handle new entities (no legacy ID)
        if (document.getLegacyId() == null) {
            document.setLegacyId(idGenerator.incrementAndGet());
            document.setCreatedAt(LocalDateTime.now());
        }

        // Check if document exists by legacyId
        if (entity.getId() != null) {
            Query query = new Query(Criteria.where(FIELD_LEGACY_ID).is(entity.getId()));
            OAuthTokenDocument existing = mongoTemplate.findOne(query, OAuthTokenDocument.class);
            if (existing != null) {
                document.setId(existing.getId());
            }
        }

        OAuthTokenDocument saved = mongoTemplate.save(document);

        // Resolve user for the returned entity
        User user = null;
        if (saved.getUserLegacyId() != null) {
            user = userDao.findById(saved.getUserLegacyId()).orElse(null);
        }

        OAuthToken result = saved.toEntity(user != null ? user : entity.getUser());
        result.setId(saved.getLegacyId());
        return result;
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
    public Optional<OAuthToken> findById(Long id) {
        log.debug("MongoDB DAO: Finding token by ID: {}", id);
        Query query = new Query(Criteria.where(FIELD_LEGACY_ID).is(id));
        OAuthTokenDocument doc = mongoTemplate.findOne(query, OAuthTokenDocument.class);
        return convertToEntity(doc);
    }

    @Override
    public boolean existsById(Long id) {
        Query query = new Query(Criteria.where(FIELD_LEGACY_ID).is(id));
        return mongoTemplate.exists(query, OAuthTokenDocument.class);
    }

    @Override
    public List<OAuthToken> findAll() {
        return mongoTemplate.findAll(OAuthTokenDocument.class).stream()
                .map(this::convertToEntityOrNull)
                .collect(Collectors.toList());
    }

    @Override
    public Page<OAuthToken> findAll(Pageable pageable) {
        Query query = new Query().with(pageable);
        long total = mongoTemplate.count(new Query(), OAuthTokenDocument.class);
        List<OAuthToken> tokens = mongoTemplate.find(query, OAuthTokenDocument.class).stream()
                .map(this::convertToEntityOrNull)
                .collect(Collectors.toList());
        return new PageImpl<>(tokens, pageable, total);
    }

    @Override
    public long count() {
        return mongoTemplate.count(new Query(), OAuthTokenDocument.class);
    }

    @Override
    public void deleteById(Long id) {
        log.info("MongoDB DAO: Deleting token by ID: {}", id);
        Query query = new Query(Criteria.where(FIELD_LEGACY_ID).is(id));
        mongoTemplate.remove(query, OAuthTokenDocument.class);
    }

    @Override
    public void delete(OAuthToken entity) {
        if (entity.getId() != null) {
            deleteById(entity.getId());
        }
    }

    @Override
    public void deleteAll() {
        mongoTemplate.remove(new Query(), OAuthTokenDocument.class);
    }

    @Override
    public void deleteAll(Iterable<OAuthToken> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public Optional<OAuthToken> findByAccessToken(String accessToken) {
        log.debug("MongoDB DAO: Finding token by access token");
        Query query = new Query(Criteria.where("accessToken").is(accessToken));
        OAuthTokenDocument doc = mongoTemplate.findOne(query, OAuthTokenDocument.class);
        return convertToEntity(doc);
    }

    @Override
    public Optional<OAuthToken> findByRefreshToken(String refreshToken) {
        log.debug("MongoDB DAO: Finding token by refresh token");
        Query query = new Query(Criteria.where("refreshToken").is(refreshToken));
        OAuthTokenDocument doc = mongoTemplate.findOne(query, OAuthTokenDocument.class);
        return convertToEntity(doc);
    }

    @Override
    public List<OAuthToken> findByUserAndIsRevokedFalse(User user) {
        log.debug("MongoDB DAO: Finding non-revoked tokens for user: {}", user.getId());
        Query query = new Query(Criteria.where(FIELD_USER_LEGACY_ID).is(user.getId()).and(FIELD_IS_REVOKED).is(false));
        return mongoTemplate.find(query, OAuthTokenDocument.class).stream()
                .map(doc -> {
                    OAuthToken token = doc.toEntity(user);
                    token.setId(doc.getLegacyId());
                    return token;
                })
                .collect(Collectors.toList());
    }

    @Override
    public void revokeAllTokensByUser(User user) {
        log.info("MongoDB DAO: Revoking all tokens for user: {}", user.getId());
        Query query = new Query(Criteria.where(FIELD_USER_LEGACY_ID).is(user.getId()));
        Update update = new Update().set(FIELD_IS_REVOKED, true);
        mongoTemplate.updateMulti(query, update, OAuthTokenDocument.class);
    }

    @Override
    public void revokeByAccessToken(String accessToken) {
        log.info("MongoDB DAO: Revoking token by access token");
        Query query = new Query(Criteria.where("accessToken").is(accessToken));
        Update update = new Update().set(FIELD_IS_REVOKED, true);
        mongoTemplate.updateFirst(query, update, OAuthTokenDocument.class);
    }

    @Override
    public void deleteExpiredOrRevokedTokens(LocalDateTime now) {
        log.info("MongoDB DAO: Deleting expired or revoked tokens");
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("expiresAt").lt(now),
                Criteria.where(FIELD_IS_REVOKED).is(true)
        ));
        mongoTemplate.remove(query, OAuthTokenDocument.class);
    }

    @Override
    public List<OAuthToken> findValidTokensByUser(User user, LocalDateTime now) {
        log.debug("MongoDB DAO: Finding valid tokens for user: {}", user.getId());
        Query query = new Query(Criteria.where(FIELD_USER_LEGACY_ID).is(user.getId())
                .and(FIELD_IS_REVOKED).is(false)
                .and("expiresAt").gt(now));
        return mongoTemplate.find(query, OAuthTokenDocument.class).stream()
                .map(doc -> {
                    OAuthToken token = doc.toEntity(user);
                    token.setId(doc.getLegacyId());
                    return token;
                })
                .collect(Collectors.toList());
    }

    private Optional<OAuthToken> convertToEntity(OAuthTokenDocument doc) {
        if (doc == null) {
            return Optional.empty();
        }
        User user = null;
        if (doc.getUserLegacyId() != null) {
            user = userDao.findById(doc.getUserLegacyId()).orElse(null);
        }
        OAuthToken token = doc.toEntity(user);
        token.setId(doc.getLegacyId());
        return Optional.of(token);
    }

    private OAuthToken convertToEntityOrNull(OAuthTokenDocument doc) {
        return convertToEntity(doc).orElse(null);
    }
}
