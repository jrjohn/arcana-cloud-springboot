package com.arcana.cloud.service.impl;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.exception.ResourceNotFoundException;
import com.arcana.cloud.exception.ValidationException;
import com.arcana.cloud.repository.UserRepository;
import com.arcana.cloud.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * UserService implementation with direct database access.
 * Active in: monolithic mode OR service layer of layered mode.
 */
@Service
@Primary
@RequiredArgsConstructor
@Slf4j
@Transactional
@ConditionalOnExpression("'${deployment.layer:}' == '' or '${deployment.layer:}' == 'service'")
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User createUser(User user) {
        log.info("Creating new user with username: {}", user.getUsername());

        if (userRepository.existsByUsername(user.getUsername())) {
            throw new ValidationException("Username already exists");
        }

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ValidationException("Email already exists");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User savedUser = userRepository.save(user);
        log.info("User created successfully with id: {}", savedUser.getId());
        return savedUser;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#id")
    public User getUserById(Long id) {
        log.debug("Fetching user by id: {}", id);
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        log.debug("Fetching user by username: {}", username);
        return userRepository.findByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        log.debug("Fetching user by email: {}", email);
        return userRepository.findByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsernameOrEmail(String usernameOrEmail) {
        log.debug("Fetching user by username or email: {}", usernameOrEmail);
        return userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<User> getUsers(int page, int size) {
        log.debug("Fetching users page: {}, size: {}", page, size);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return userRepository.findAll(pageRequest);
    }

    @Override
    @CacheEvict(value = "users", key = "#id")
    public User updateUser(Long id, User userUpdate) {
        log.info("Updating user with id: {}", id);

        User existingUser = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (userUpdate.getUsername() != null && !userUpdate.getUsername().equals(existingUser.getUsername())) {
            if (userRepository.existsByUsername(userUpdate.getUsername())) {
                throw new ValidationException("Username already exists");
            }
            existingUser.setUsername(userUpdate.getUsername());
        }

        if (userUpdate.getEmail() != null && !userUpdate.getEmail().equals(existingUser.getEmail())) {
            if (userRepository.existsByEmail(userUpdate.getEmail())) {
                throw new ValidationException("Email already exists");
            }
            existingUser.setEmail(userUpdate.getEmail());
        }

        if (userUpdate.getPassword() != null) {
            existingUser.setPassword(passwordEncoder.encode(userUpdate.getPassword()));
        }

        if (userUpdate.getFirstName() != null) {
            existingUser.setFirstName(userUpdate.getFirstName());
        }

        if (userUpdate.getLastName() != null) {
            existingUser.setLastName(userUpdate.getLastName());
        }

        if (userUpdate.getIsActive() != null) {
            existingUser.setIsActive(userUpdate.getIsActive());
        }

        if (userUpdate.getIsVerified() != null) {
            existingUser.setIsVerified(userUpdate.getIsVerified());
        }

        User savedUser = userRepository.save(existingUser);
        log.info("User updated successfully with id: {}", savedUser.getId());
        return savedUser;
    }

    @Override
    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(Long id) {
        log.info("Deleting user with id: {}", id);

        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User", "id", id);
        }

        userRepository.deleteById(id);
        log.info("User deleted successfully with id: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}
