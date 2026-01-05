package com.arcana.cloud.dao.impl.mybatis;

import com.arcana.cloud.dao.impl.mybatis.mapper.UserMapper;
import com.arcana.cloud.dao.interfaces.UserDao;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MyBatis implementation of UserDao.
 * Active when database.orm is 'mybatis' or not specified (default).
 * Works with MySQL and PostgreSQL databases.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
@Transactional
@ConditionalOnProperty(name = "database.orm", havingValue = "mybatis", matchIfMissing = true)
public class UserDaoMybatisImpl implements UserDao {

    private final UserMapper userMapper;

    @Override
    public User save(User user) {
        log.debug("MyBatis DAO: Saving user: {}", user.getUsername());
        if (user.getId() == null) {
            userMapper.insert(user);
        } else {
            userMapper.update(user);
        }
        return user;
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
    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        log.debug("MyBatis DAO: Finding user by id: {}", id);
        return userMapper.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return userMapper.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        log.debug("MyBatis DAO: Finding all users");
        return userMapper.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<User> findAll(Pageable pageable) {
        log.debug("MyBatis DAO: Finding all users with pagination");
        long total = userMapper.count();
        List<User> users = userMapper.findAllWithPagination(
                pageable.getOffset(),
                pageable.getPageSize()
        );
        return new PageImpl<>(users, pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return userMapper.count();
    }

    @Override
    public void deleteById(Long id) {
        log.info("MyBatis DAO: Deleting user by id: {}", id);
        userMapper.deleteById(id);
    }

    @Override
    public void delete(User entity) {
        if (entity.getId() != null) {
            deleteById(entity.getId());
        }
    }

    @Override
    public void deleteAll() {
        log.warn("MyBatis DAO: Deleting all users");
        userMapper.deleteAll();
    }

    @Override
    public void deleteAll(Iterable<User> entities) {
        for (User user : entities) {
            delete(user);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        log.debug("MyBatis DAO: Finding user by username: {}", username);
        return userMapper.findByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        log.debug("MyBatis DAO: Finding user by email: {}", email);
        return userMapper.findByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsernameOrEmail(String username, String email) {
        log.debug("MyBatis DAO: Finding user by username or email: {}/{}", username, email);
        return userMapper.findByUsernameOrEmail(username, email);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return userMapper.existsByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userMapper.existsByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findActiveUsersByRole(UserRole role) {
        log.debug("MyBatis DAO: Finding active users by role: {}", role);
        return userMapper.findActiveUsersByRole(role);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAllActiveUsers() {
        log.debug("MyBatis DAO: Finding all active users");
        return userMapper.findAllActiveUsers();
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findUnverifiedUsers() {
        log.debug("MyBatis DAO: Finding unverified users");
        return userMapper.findUnverifiedUsers();
    }
}
