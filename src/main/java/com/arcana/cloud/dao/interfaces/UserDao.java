package com.arcana.cloud.dao.interfaces;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;

import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for User entity.
 * Provides database-agnostic access to user data.
 * Implementations can use MyBatis, JPA, or MongoDB.
 */
public interface UserDao extends BaseDao<User, Long> {

    /**
     * Find user by username.
     *
     * @param username the username
     * @return an Optional containing the user if found
     */
    Optional<User> findByUsername(String username);

    /**
     * Find user by email.
     *
     * @param email the email
     * @return an Optional containing the user if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Find user by username or email.
     *
     * @param username the username
     * @param email the email
     * @return an Optional containing the user if found
     */
    Optional<User> findByUsernameOrEmail(String username, String email);

    /**
     * Check if username exists.
     *
     * @param username the username
     * @return true if username exists
     */
    boolean existsByUsername(String username);

    /**
     * Check if email exists.
     *
     * @param email the email
     * @return true if email exists
     */
    boolean existsByEmail(String email);

    /**
     * Find active users by role.
     *
     * @param role the user role
     * @return list of active users with the specified role
     */
    List<User> findActiveUsersByRole(UserRole role);

    /**
     * Find all active users.
     *
     * @return list of all active users
     */
    List<User> findAllActiveUsers();

    /**
     * Find unverified users.
     *
     * @return list of unverified users
     */
    List<User> findUnverifiedUsers();
}
