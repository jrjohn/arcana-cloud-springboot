package com.arcana.cloud.repository.impl.jpa;

import com.arcana.cloud.dao.interfaces.UserDao;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.repository.interfaces.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA-backed Repository implementation for User entity.
 * Active when database.orm is 'jpa'.
 * Delegates all operations to the UserDao interface (resolved to UserDaoJpaImpl).
 */
@Repository
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "database.orm", havingValue = "jpa")
public class UserRepositoryJpaImpl implements UserRepository {

    private final UserDao userDao;

    @Override
    public User save(User user) {
        log.debug("JPA Repository: Saving user: {}", user.getUsername());
        return userDao.save(user);
    }

    @Override
    public List<User> saveAll(Iterable<User> users) {
        return userDao.saveAll(users);
    }

    @Override
    public Optional<User> findById(Long id) {
        log.debug("JPA Repository: Finding user by id: {}", id);
        return userDao.findById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return userDao.existsById(id);
    }

    @Override
    public List<User> findAll() {
        log.debug("JPA Repository: Finding all users");
        return userDao.findAll();
    }

    @Override
    public Page<User> findAll(Pageable pageable) {
        log.debug("JPA Repository: Finding all users with pagination");
        return userDao.findAll(pageable);
    }

    @Override
    public long count() {
        return userDao.count();
    }

    @Override
    public void deleteById(Long id) {
        log.info("JPA Repository: Deleting user by id: {}", id);
        userDao.deleteById(id);
    }

    @Override
    public void delete(User user) {
        userDao.delete(user);
    }

    @Override
    public void deleteAll() {
        log.warn("JPA Repository: Deleting all users");
        userDao.deleteAll();
    }

    @Override
    public Optional<User> findByUsername(String username) {
        log.debug("JPA Repository: Finding user by username: {}", username);
        return userDao.findByUsername(username);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        log.debug("JPA Repository: Finding user by email: {}", email);
        return userDao.findByEmail(email);
    }

    @Override
    public Optional<User> findByUsernameOrEmail(String username, String email) {
        log.debug("JPA Repository: Finding user by username or email: {}/{}", username, email);
        return userDao.findByUsernameOrEmail(username, email);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userDao.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userDao.existsByEmail(email);
    }

    @Override
    public List<User> findActiveUsersByRole(UserRole role) {
        log.debug("JPA Repository: Finding active users by role: {}", role);
        return userDao.findActiveUsersByRole(role);
    }

    @Override
    public List<User> findAllActiveUsers() {
        log.debug("JPA Repository: Finding all active users");
        return userDao.findAllActiveUsers();
    }

    @Override
    public List<User> findUnverifiedUsers() {
        log.debug("JPA Repository: Finding unverified users");
        return userDao.findUnverifiedUsers();
    }
}
