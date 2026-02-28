package com.arcana.cloud.service;

import com.arcana.cloud.entity.User;
import org.springframework.data.domain.Page;

import java.util.Optional;

public interface UserService {

    User createUser(User user);

    User getUserById(Long id);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameOrEmail(String usernameOrEmail);

    Page<User> getUsers(int page, int size);

    User updateUser(Long id, User user);

    void deleteUser(Long id);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
