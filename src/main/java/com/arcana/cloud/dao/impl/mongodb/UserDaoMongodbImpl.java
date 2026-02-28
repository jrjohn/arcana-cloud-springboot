package com.arcana.cloud.dao.impl.mongodb;

import com.arcana.cloud.dao.UserDao;
import com.arcana.cloud.document.UserDocument;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * MongoDB implementation of UserDao.
 * Active when database.type is 'mongodb'.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "database.type", havingValue = "mongodb")
public class UserDaoMongodbImpl implements UserDao {

    private static final String FIELD_LEGACY_ID = "legacyId";
    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_IS_ACTIVE = "isActive";

    private final MongoTemplate mongoTemplate;

    // Simple ID generator for legacy ID compatibility
    private static final AtomicLong idGenerator = new AtomicLong(1000000);

    @Override
    public User save(User entity) {
        log.debug("MongoDB DAO: Saving user: {}", entity.getUsername());

        UserDocument document = UserDocument.fromEntity(entity);

        // Handle new entities (no legacy ID)
        if (document.getLegacyId() == null) {
            document.setLegacyId(idGenerator.incrementAndGet());
            document.setCreatedAt(LocalDateTime.now());
        }
        document.setUpdatedAt(LocalDateTime.now());

        // Check if document exists by legacyId
        if (entity.getId() != null) {
            Query query = new Query(Criteria.where(FIELD_LEGACY_ID).is(entity.getId()));
            UserDocument existing = mongoTemplate.findOne(query, UserDocument.class);
            if (existing != null) {
                document.setId(existing.getId());
            }
        }

        UserDocument saved = mongoTemplate.save(document);
        User result = saved.toEntity();
        result.setId(saved.getLegacyId());
        return result;
    }

    @Override
    public List<User> saveAll(Iterable<User> entities) {
        List<User> result = new ArrayList<>();
        for (User user : entities) {
            result.add(save(user));
        }
        return result;
    }

    @Override
    public Optional<User> findById(Long id) {
        log.debug("MongoDB DAO: Finding user by ID: {}", id);
        Query query = new Query(Criteria.where(FIELD_LEGACY_ID).is(id));
        UserDocument doc = mongoTemplate.findOne(query, UserDocument.class);
        if (doc != null) {
            User user = doc.toEntity();
            user.setId(doc.getLegacyId());
            return Optional.of(user);
        }
        return Optional.empty();
    }

    @Override
    public boolean existsById(Long id) {
        Query query = new Query(Criteria.where(FIELD_LEGACY_ID).is(id));
        return mongoTemplate.exists(query, UserDocument.class);
    }

    @Override
    public List<User> findAll() {
        return mongoTemplate.findAll(UserDocument.class).stream()
                .map(doc -> {
                    User user = doc.toEntity();
                    user.setId(doc.getLegacyId());
                    return user;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Page<User> findAll(Pageable pageable) {
        Query query = new Query().with(pageable);
        long total = mongoTemplate.count(new Query(), UserDocument.class);
        List<User> users = mongoTemplate.find(query, UserDocument.class).stream()
                .map(doc -> {
                    User user = doc.toEntity();
                    user.setId(doc.getLegacyId());
                    return user;
                })
                .collect(Collectors.toList());
        return new PageImpl<>(users, pageable, total);
    }

    @Override
    public long count() {
        return mongoTemplate.count(new Query(), UserDocument.class);
    }

    @Override
    public void deleteById(Long id) {
        log.info("MongoDB DAO: Deleting user by ID: {}", id);
        Query query = new Query(Criteria.where(FIELD_LEGACY_ID).is(id));
        mongoTemplate.remove(query, UserDocument.class);
    }

    @Override
    public void delete(User entity) {
        if (entity.getId() != null) {
            deleteById(entity.getId());
        }
    }

    @Override
    public void deleteAll() {
        mongoTemplate.remove(new Query(), UserDocument.class);
    }

    @Override
    public void deleteAll(Iterable<User> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        log.debug("MongoDB DAO: Finding user by username: {}", username);
        Query query = new Query(Criteria.where(FIELD_USERNAME).is(username));
        UserDocument doc = mongoTemplate.findOne(query, UserDocument.class);
        if (doc != null) {
            User user = doc.toEntity();
            user.setId(doc.getLegacyId());
            return Optional.of(user);
        }
        return Optional.empty();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        log.debug("MongoDB DAO: Finding user by email: {}", email);
        Query query = new Query(Criteria.where(FIELD_EMAIL).is(email));
        UserDocument doc = mongoTemplate.findOne(query, UserDocument.class);
        if (doc != null) {
            User user = doc.toEntity();
            user.setId(doc.getLegacyId());
            return Optional.of(user);
        }
        return Optional.empty();
    }

    @Override
    public Optional<User> findByUsernameOrEmail(String username, String email) {
        log.debug("MongoDB DAO: Finding user by username or email: {}/{}", username, email);
        Query query = new Query(new Criteria().orOperator(
                Criteria.where(FIELD_USERNAME).is(username),
                Criteria.where(FIELD_EMAIL).is(email)
        ));
        UserDocument doc = mongoTemplate.findOne(query, UserDocument.class);
        if (doc != null) {
            User user = doc.toEntity();
            user.setId(doc.getLegacyId());
            return Optional.of(user);
        }
        return Optional.empty();
    }

    @Override
    public boolean existsByUsername(String username) {
        Query query = new Query(Criteria.where(FIELD_USERNAME).is(username));
        return mongoTemplate.exists(query, UserDocument.class);
    }

    @Override
    public boolean existsByEmail(String email) {
        Query query = new Query(Criteria.where(FIELD_EMAIL).is(email));
        return mongoTemplate.exists(query, UserDocument.class);
    }

    @Override
    public List<User> findActiveUsersByRole(UserRole role) {
        log.debug("MongoDB DAO: Finding active users by role: {}", role);
        Query query = new Query(Criteria.where("role").is(role).and(FIELD_IS_ACTIVE).is(true));
        return mongoTemplate.find(query, UserDocument.class).stream()
                .map(doc -> {
                    User user = doc.toEntity();
                    user.setId(doc.getLegacyId());
                    return user;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<User> findAllActiveUsers() {
        log.debug("MongoDB DAO: Finding all active users");
        Query query = new Query(Criteria.where(FIELD_IS_ACTIVE).is(true));
        return mongoTemplate.find(query, UserDocument.class).stream()
                .map(doc -> {
                    User user = doc.toEntity();
                    user.setId(doc.getLegacyId());
                    return user;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<User> findUnverifiedUsers() {
        log.debug("MongoDB DAO: Finding unverified users");
        Query query = new Query(Criteria.where("isVerified").is(false).and(FIELD_IS_ACTIVE).is(true));
        return mongoTemplate.find(query, UserDocument.class).stream()
                .map(doc -> {
                    User user = doc.toEntity();
                    user.setId(doc.getLegacyId());
                    return user;
                })
                .collect(Collectors.toList());
    }
}
