package com.arcana.cloud.repository.interfaces;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for User entity.
 * This interface is used by the Service layer and remains unchanged.
 * Implementations delegate to DAO layer for database operations.
 */
public interface UserRepository {

    /**
     * Save a user (create or update).
     */
    User save(User user);

    /**
     * Save all users.
     */
    List<User> saveAll(Iterable<User> users);

    /**
     * Find user by ID.
     */
    Optional<User> findById(Long id);

    /**
     * Check if user exists by ID.
     */
    boolean existsById(Long id);

    /**
     * Find all users.
     */
    List<User> findAll();

    /**
     * Find all users with pagination.
     */
    Page<User> findAll(Pageable pageable);

    /**
     * Count all users.
     */
    long count();

    /**
     * Delete user by ID.
     */
    void deleteById(Long id);

    /**
     * Delete user.
     */
    void delete(User user);

    /**
     * Delete all users.
     */
    void deleteAll();

    /**
     * Find user by username.
     */
    Optional<User> findByUsername(String username);

    /**
     * Find user by email.
     */
    Optional<User> findByEmail(String email);

    /**
     * Find user by username or email.
     */
    Optional<User> findByUsernameOrEmail(String username, String email);

    /**
     * Check if username exists.
     */
    boolean existsByUsername(String username);

    /**
     * Check if email exists.
     */
    boolean existsByEmail(String email);

    /**
     * Find active users by role.
     */
    List<User> findActiveUsersByRole(UserRole role);

    /**
     * Find all active users.
     */
    List<User> findAllActiveUsers();

    /**
     * Find unverified users.
     */
    List<User> findUnverifiedUsers();
}
