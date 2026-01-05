package com.arcana.cloud.dao.impl.mybatis.mapper;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis mapper interface for User entity.
 * SQL mappings are defined in UserMapper.xml.
 */
@Mapper
public interface UserMapper {

    /**
     * Insert a new user.
     */
    int insert(User user);

    /**
     * Update an existing user.
     */
    int update(User user);

    /**
     * Find user by ID.
     */
    Optional<User> findById(@Param("id") Long id);

    /**
     * Find user by username.
     */
    Optional<User> findByUsername(@Param("username") String username);

    /**
     * Find user by email.
     */
    Optional<User> findByEmail(@Param("email") String email);

    /**
     * Find user by username or email.
     */
    Optional<User> findByUsernameOrEmail(@Param("username") String username, @Param("email") String email);

    /**
     * Check if user exists by ID.
     */
    boolean existsById(@Param("id") Long id);

    /**
     * Check if username exists.
     */
    boolean existsByUsername(@Param("username") String username);

    /**
     * Check if email exists.
     */
    boolean existsByEmail(@Param("email") String email);

    /**
     * Find all users.
     */
    List<User> findAll();

    /**
     * Find all users with pagination.
     */
    List<User> findAllWithPagination(@Param("offset") long offset, @Param("limit") int limit);

    /**
     * Count all users.
     */
    long count();

    /**
     * Find active users by role.
     */
    List<User> findActiveUsersByRole(@Param("role") UserRole role);

    /**
     * Find all active users.
     */
    List<User> findAllActiveUsers();

    /**
     * Find unverified users.
     */
    List<User> findUnverifiedUsers();

    /**
     * Delete user by ID.
     */
    int deleteById(@Param("id") Long id);

    /**
     * Delete all users.
     */
    int deleteAll();
}
