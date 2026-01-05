package com.arcana.cloud.dao.impl.jpa.repository;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Internal JPA repository for User entity.
 * Used by UserDaoJpaImpl when JPA mode is enabled.
 */
@Repository
@ConditionalOnProperty(name = "database.orm", havingValue = "jpa")
public interface UserJpaRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameOrEmail(String username, String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.role = :role AND u.isActive = true")
    List<User> findActiveUsersByRole(@Param("role") UserRole role);

    @Query("SELECT u FROM User u WHERE u.isActive = true")
    List<User> findAllActiveUsers();

    @Query("SELECT u FROM User u WHERE u.isVerified = false AND u.isActive = true")
    List<User> findUnverifiedUsers();
}
