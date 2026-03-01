package com.arcana.cloud.service;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.exception.ResourceNotFoundException;
import com.arcana.cloud.exception.ValidationException;
import com.arcana.cloud.repository.UserRepository;
import com.arcana.cloud.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceExtendedTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .password("encoded_password")
            .firstName("Test")
            .lastName("User")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(false)
            .build();
    }

    @Test
    void testExistsByUsername_True() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);
        assertTrue(userService.existsByUsername("testuser"));
    }

    @Test
    void testExistsByUsername_False() {
        when(userRepository.existsByUsername("unknown")).thenReturn(false);
        assertFalse(userService.existsByUsername("unknown"));
    }

    @Test
    void testExistsByEmail_True() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);
        assertTrue(userService.existsByEmail("test@example.com"));
    }

    @Test
    void testExistsByEmail_False() {
        when(userRepository.existsByEmail("unknown@example.com")).thenReturn(false);
        assertFalse(userService.existsByEmail("unknown@example.com"));
    }

    @Test
    void testFindByUsernameOrEmail_Found() {
        when(userRepository.findByUsernameOrEmail("testuser", "testuser"))
            .thenReturn(Optional.of(testUser));

        Optional<User> result = userService.findByUsernameOrEmail("testuser");

        assertTrue(result.isPresent());
        assertEquals("testuser", result.get().getUsername());
    }

    @Test
    void testFindByUsernameOrEmail_ByEmail() {
        when(userRepository.findByUsernameOrEmail("test@example.com", "test@example.com"))
            .thenReturn(Optional.of(testUser));

        Optional<User> result = userService.findByUsernameOrEmail("test@example.com");

        assertTrue(result.isPresent());
        assertEquals("test@example.com", result.get().getEmail());
    }

    @Test
    void testFindByUsernameOrEmail_NotFound() {
        when(userRepository.findByUsernameOrEmail("unknown", "unknown"))
            .thenReturn(Optional.empty());

        Optional<User> result = userService.findByUsernameOrEmail("unknown");

        assertFalse(result.isPresent());
    }

    @Test
    void testUpdateUser_DuplicateUsername() {
        User userUpdate = User.builder()
            .username("existinguser")
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        assertThrows(ValidationException.class, () -> userService.updateUser(1L, userUpdate));
    }

    @Test
    void testUpdateUser_DuplicateEmail() {
        User userUpdate = User.builder()
            .email("existing@example.com")
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThrows(ValidationException.class, () -> userService.updateUser(1L, userUpdate));
    }

    @Test
    void testUpdateUser_WithPasswordUpdate() {
        User userUpdate = User.builder()
            .password("newPassword123")
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("newPassword123")).thenReturn("new_encoded_password");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User updated = userService.updateUser(1L, userUpdate);

        assertNotNull(updated);
        verify(passwordEncoder).encode("newPassword123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testUpdateUser_WithSameUsername_NoConflict() {
        User userUpdate = User.builder()
            .username("testuser") // same as existing, no conflict check
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User updated = userService.updateUser(1L, userUpdate);

        assertNotNull(updated);
    }

    @Test
    void testUpdateUser_WithSameEmail_NoConflict() {
        User userUpdate = User.builder()
            .email("test@example.com") // same as existing, no conflict check
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User updated = userService.updateUser(1L, userUpdate);

        assertNotNull(updated);
    }

    @Test
    void testUpdateUser_WithIsActiveAndIsVerified() {
        User userUpdate = User.builder()
            .isActive(false)
            .isVerified(true)
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User updated = userService.updateUser(1L, userUpdate);

        assertNotNull(updated);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testUpdateUser_WithFirstNameAndLastName() {
        User userUpdate = User.builder()
            .firstName("Updated")
            .lastName("Name")
            .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User updated = userService.updateUser(1L, userUpdate);

        assertNotNull(updated);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void testFindByUsername_NotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        Optional<User> result = userService.findByUsername("unknown");

        assertFalse(result.isPresent());
    }

    @Test
    void testFindByEmail_NotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        Optional<User> result = userService.findByEmail("unknown@example.com");

        assertFalse(result.isPresent());
    }
}
