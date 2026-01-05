package com.arcana.cloud.dao.impl.jpa;

import com.arcana.cloud.dao.impl.jpa.repository.UserJpaRepository;
import com.arcana.cloud.dao.interfaces.UserDao;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA implementation of UserDao.
 * Active when database.orm is 'jpa'.
 * Works with MySQL and PostgreSQL databases.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
@Transactional
@ConditionalOnProperty(name = "database.orm", havingValue = "jpa")
public class UserDaoJpaImpl implements UserDao {

    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        log.debug("JPA DAO: Saving user: {}", user.getUsername());
        return userJpaRepository.save(user);
    }

    @Override
    public List<User> saveAll(Iterable<User> entities) {
        return userJpaRepository.saveAll(entities);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        log.debug("JPA DAO: Finding user by id: {}", id);
        return userJpaRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return userJpaRepository.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        log.debug("JPA DAO: Finding all users");
        return userJpaRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<User> findAll(Pageable pageable) {
        log.debug("JPA DAO: Finding all users with pagination");
        return userJpaRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return userJpaRepository.count();
    }

    @Override
    public void deleteById(Long id) {
        log.info("JPA DAO: Deleting user by id: {}", id);
        userJpaRepository.deleteById(id);
    }

    @Override
    public void delete(User entity) {
        userJpaRepository.delete(entity);
    }

    @Override
    public void deleteAll() {
        log.warn("JPA DAO: Deleting all users");
        userJpaRepository.deleteAll();
    }

    @Override
    public void deleteAll(Iterable<User> entities) {
        userJpaRepository.deleteAll(entities);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        log.debug("JPA DAO: Finding user by username: {}", username);
        return userJpaRepository.findByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        log.debug("JPA DAO: Finding user by email: {}", email);
        return userJpaRepository.findByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsernameOrEmail(String username, String email) {
        log.debug("JPA DAO: Finding user by username or email: {}/{}", username, email);
        return userJpaRepository.findByUsernameOrEmail(username, email);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return userJpaRepository.existsByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findActiveUsersByRole(UserRole role) {
        log.debug("JPA DAO: Finding active users by role: {}", role);
        return userJpaRepository.findActiveUsersByRole(role);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAllActiveUsers() {
        log.debug("JPA DAO: Finding all active users");
        return userJpaRepository.findAllActiveUsers();
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findUnverifiedUsers() {
        log.debug("JPA DAO: Finding unverified users");
        return userJpaRepository.findUnverifiedUsers();
    }
}
