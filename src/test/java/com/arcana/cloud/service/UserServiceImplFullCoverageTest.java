package com.arcana.cloud.service;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.exception.ResourceNotFoundException;
import com.arcana.cloud.exception.ValidationException;
import com.arcana.cloud.repository.UserRepository;
import com.arcana.cloud.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Full-coverage unit tests for UserServiceImpl.
 * Uses Mockito only — no Spring context, no MySQL.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl - Full Coverage Tests")
class UserServiceImplFullCoverageTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        existingUser = User.builder()
            .id(1L)
            .username("alice")
            .email("alice@example.com")
            .password("hashed_pw")
            .firstName("Alice")
            .lastName("Smith")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(false)
            .build();
    }

    // ─── createUser() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createUser: success → encodes password and saves user")
    void createUser_success() {
        User newUser = User.builder()
            .username("bob")
            .email("bob@example.com")
            .password("plain")
            .build();

        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plain")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        User result = userService.createUser(newUser);

        assertNotNull(result);
        verify(passwordEncoder).encode("plain");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("createUser: username taken → ValidationException, no save")
    void createUser_usernameExists() {
        User newUser = User.builder()
            .username("alice")
            .email("other@example.com")
            .password("plain")
            .build();

        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(ValidationException.class, () -> userService.createUser(newUser));
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("createUser: email taken → ValidationException, no save")
    void createUser_emailExists() {
        User newUser = User.builder()
            .username("bob")
            .email("alice@example.com")
            .password("plain")
            .build();

        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThrows(ValidationException.class, () -> userService.createUser(newUser));
        verify(userRepository, never()).save(any());
    }

    // ─── getUserById() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getUserById: user found → returns user")
    void getUserById_found() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

        User result = userService.getUserById(1L);

        assertNotNull(result);
        assertEquals("alice", result.getUsername());
    }

    @Test
    @DisplayName("getUserById: user not found → ResourceNotFoundException")
    void getUserById_notFound() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
            () -> userService.getUserById(42L));
        assertNotNull(ex.getMessage());
    }

    // ─── findByUsername() ────────────────────────────────────────────────────

    @Test
    @DisplayName("findByUsername: found → Optional with user")
    void findByUsername_found() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));

        Optional<User> result = userService.findByUsername("alice");

        assertTrue(result.isPresent());
        assertEquals("alice", result.get().getUsername());
    }

    @Test
    @DisplayName("findByUsername: not found → empty Optional")
    void findByUsername_notFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        Optional<User> result = userService.findByUsername("ghost");

        assertFalse(result.isPresent());
    }

    // ─── findByEmail() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("findByEmail: found → Optional with user")
    void findByEmail_found() {
        when(userRepository.findByEmail("alice@example.com"))
            .thenReturn(Optional.of(existingUser));

        Optional<User> result = userService.findByEmail("alice@example.com");

        assertTrue(result.isPresent());
        assertEquals("alice@example.com", result.get().getEmail());
    }

    @Test
    @DisplayName("findByEmail: not found → empty Optional")
    void findByEmail_notFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        Optional<User> result = userService.findByEmail("unknown@example.com");

        assertFalse(result.isPresent());
    }

    // ─── findByUsernameOrEmail() ──────────────────────────────────────────────

    @Test
    @DisplayName("findByUsernameOrEmail: match by username → found")
    void findByUsernameOrEmail_foundByUsername() {
        when(userRepository.findByUsernameOrEmail("alice", "alice"))
            .thenReturn(Optional.of(existingUser));

        Optional<User> result = userService.findByUsernameOrEmail("alice");

        assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("findByUsernameOrEmail: match by email → found")
    void findByUsernameOrEmail_foundByEmail() {
        when(userRepository.findByUsernameOrEmail("alice@example.com", "alice@example.com"))
            .thenReturn(Optional.of(existingUser));

        Optional<User> result = userService.findByUsernameOrEmail("alice@example.com");

        assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("findByUsernameOrEmail: not found → empty Optional")
    void findByUsernameOrEmail_notFound() {
        when(userRepository.findByUsernameOrEmail(anyString(), anyString()))
            .thenReturn(Optional.empty());

        Optional<User> result = userService.findByUsernameOrEmail("nobody");

        assertFalse(result.isPresent());
    }

    // ─── getUsers() ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getUsers: returns page of users with descending createdAt sort")
    void getUsers_returnsPage() {
        Page<User> page = new PageImpl<>(List.of(existingUser),
            PageRequest.of(0, 10, Sort.by("createdAt").descending()), 1);
        when(userRepository.findAll(any(PageRequest.class))).thenReturn(page);

        Page<User> result = userService.getUsers(0, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("alice", result.getContent().get(0).getUsername());
    }

    @Test
    @DisplayName("getUsers: page 2 still delegates to repository")
    void getUsers_secondPage() {
        Page<User> emptyPage = new PageImpl<>(Collections.emptyList(),
            PageRequest.of(1, 5, Sort.by("createdAt").descending()), 0);
        when(userRepository.findAll(any(PageRequest.class))).thenReturn(emptyPage);

        Page<User> result = userService.getUsers(1, 5);

        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
    }

    // ─── updateUser() ────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateUser: user not found → ResourceNotFoundException")
    void updateUser_notFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> userService.updateUser(99L, User.builder().build()));
    }

    @Test
    @DisplayName("updateUser: new username already taken → ValidationException")
    void updateUser_duplicateUsername() {
        User update = User.builder().username("takenname").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByUsername("takenname")).thenReturn(true);

        assertThrows(ValidationException.class, () -> userService.updateUser(1L, update));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateUser: same username as current → no duplicate check, no exception")
    void updateUser_sameUsername_noConflict() {
        User update = User.builder().username("alice").build(); // same as existing

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        User result = userService.updateUser(1L, update);

        assertNotNull(result);
        verify(userRepository, never()).existsByUsername(anyString());
    }

    @Test
    @DisplayName("updateUser: new email already taken → ValidationException")
    void updateUser_duplicateEmail() {
        User update = User.builder().email("taken@example.com").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThrows(ValidationException.class, () -> userService.updateUser(1L, update));
    }

    @Test
    @DisplayName("updateUser: same email as current → no duplicate check, no exception")
    void updateUser_sameEmail_noConflict() {
        User update = User.builder().email("alice@example.com").build(); // same as existing

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        User result = userService.updateUser(1L, update);

        assertNotNull(result);
        verify(userRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("updateUser: password update → encodes and sets new password")
    void updateUser_passwordChange() {
        User update = User.builder().password("newPlain").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.encode("newPlain")).thenReturn("newHashed");
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        userService.updateUser(1L, update);

        assertEquals("newHashed", existingUser.getPassword());
        verify(passwordEncoder).encode("newPlain");
    }

    @Test
    @DisplayName("updateUser: firstName update → sets first name")
    void updateUser_firstNameChange() {
        User update = User.builder().firstName("Alicia").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        userService.updateUser(1L, update);

        assertEquals("Alicia", existingUser.getFirstName());
    }

    @Test
    @DisplayName("updateUser: lastName update → sets last name")
    void updateUser_lastNameChange() {
        User update = User.builder().lastName("Jones").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        userService.updateUser(1L, update);

        assertEquals("Jones", existingUser.getLastName());
    }

    @Test
    @DisplayName("updateUser: isActive update → deactivates user")
    void updateUser_deactivateUser() {
        User update = User.builder().isActive(false).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        userService.updateUser(1L, update);

        assertFalse(existingUser.getIsActive());
    }

    @Test
    @DisplayName("updateUser: isVerified update → verifies user")
    void updateUser_verifyUser() {
        User update = User.builder().isVerified(true).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        userService.updateUser(1L, update);

        assertTrue(existingUser.getIsVerified());
    }

    @Test
    @DisplayName("updateUser: all null fields → only saves, changes nothing")
    void updateUser_noFieldsChanged() {
        User update = User.builder().build(); // all null

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        User result = userService.updateUser(1L, update);

        assertNotNull(result);
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("updateUser: new username not taken → updates username")
    void updateUser_changeUsername_available() {
        User update = User.builder().username("alice_new").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByUsername("alice_new")).thenReturn(false);
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        userService.updateUser(1L, update);

        assertEquals("alice_new", existingUser.getUsername());
    }

    @Test
    @DisplayName("updateUser: new email not taken → updates email")
    void updateUser_changeEmail_available() {
        User update = User.builder().email("alice_new@example.com").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmail("alice_new@example.com")).thenReturn(false);
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        userService.updateUser(1L, update);

        assertEquals("alice_new@example.com", existingUser.getEmail());
    }

    // ─── deleteUser() ────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteUser: success → deleteById called")
    void deleteUser_success() {
        when(userRepository.existsById(1L)).thenReturn(true);

        userService.deleteUser(1L);

        verify(userRepository).deleteById(1L);
    }

    @Test
    @DisplayName("deleteUser: user not found → ResourceNotFoundException, no delete")
    void deleteUser_notFound() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> userService.deleteUser(99L));
        verify(userRepository, never()).deleteById(anyLong());
    }

    // ─── existsByUsername() / existsByEmail() ─────────────────────────────────

    @Test
    @DisplayName("existsByUsername: returns true when exists")
    void existsByUsername_true() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);
        assertTrue(userService.existsByUsername("alice"));
    }

    @Test
    @DisplayName("existsByUsername: returns false when not exists")
    void existsByUsername_false() {
        when(userRepository.existsByUsername("ghost")).thenReturn(false);
        assertFalse(userService.existsByUsername("ghost"));
    }

    @Test
    @DisplayName("existsByEmail: returns true when exists")
    void existsByEmail_true() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);
        assertTrue(userService.existsByEmail("alice@example.com"));
    }

    @Test
    @DisplayName("existsByEmail: returns false when not exists")
    void existsByEmail_false() {
        when(userRepository.existsByEmail("no@example.com")).thenReturn(false);
        assertFalse(userService.existsByEmail("no@example.com"));
    }
}
