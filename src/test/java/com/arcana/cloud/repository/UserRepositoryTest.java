package com.arcana.cloud.repository;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(com.arcana.cloud.config.TestCacheConfig.class)
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .username("testuser")
            .email("test@example.com")
            .password("encoded_password")
            .firstName("Test")
            .lastName("User")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(false)
            .build();

        entityManager.persist(testUser);
        entityManager.flush();
    }

    @Test
    void testFindByUsername_Found() {
        Optional<User> found = userRepository.findByUsername("testuser");

        assertTrue(found.isPresent());
        assertEquals("testuser", found.get().getUsername());
    }

    @Test
    void testFindByUsername_NotFound() {
        Optional<User> found = userRepository.findByUsername("nonexistent");

        assertFalse(found.isPresent());
    }

    @Test
    void testFindByEmail_Found() {
        Optional<User> found = userRepository.findByEmail("test@example.com");

        assertTrue(found.isPresent());
        assertEquals("test@example.com", found.get().getEmail());
    }

    @Test
    void testFindByEmail_NotFound() {
        Optional<User> found = userRepository.findByEmail("nonexistent@example.com");

        assertFalse(found.isPresent());
    }

    @Test
    void testFindByUsernameOrEmail_ByUsername() {
        Optional<User> found = userRepository.findByUsernameOrEmail("testuser", "testuser");

        assertTrue(found.isPresent());
        assertEquals("testuser", found.get().getUsername());
    }

    @Test
    void testFindByUsernameOrEmail_ByEmail() {
        Optional<User> found = userRepository.findByUsernameOrEmail("test@example.com", "test@example.com");

        assertTrue(found.isPresent());
        assertEquals("test@example.com", found.get().getEmail());
    }

    @Test
    void testExistsByUsername_True() {
        boolean exists = userRepository.existsByUsername("testuser");

        assertTrue(exists);
    }

    @Test
    void testExistsByUsername_False() {
        boolean exists = userRepository.existsByUsername("nonexistent");

        assertFalse(exists);
    }

    @Test
    void testExistsByEmail_True() {
        boolean exists = userRepository.existsByEmail("test@example.com");

        assertTrue(exists);
    }

    @Test
    void testExistsByEmail_False() {
        boolean exists = userRepository.existsByEmail("nonexistent@example.com");

        assertFalse(exists);
    }

    @Test
    void testFindActiveUsersByRole() {
        User adminUser = User.builder()
            .username("admin")
            .email("admin@example.com")
            .password("encoded_password")
            .role(UserRole.ADMIN)
            .isActive(true)
            .build();

        entityManager.persist(adminUser);
        entityManager.flush();

        List<User> activeAdmins = userRepository.findActiveUsersByRole(UserRole.ADMIN);

        assertEquals(1, activeAdmins.size());
        assertEquals("admin", activeAdmins.get(0).getUsername());
    }

    @Test
    void testFindAllActiveUsers() {
        User inactiveUser = User.builder()
            .username("inactive")
            .email("inactive@example.com")
            .password("encoded_password")
            .role(UserRole.USER)
            .isActive(false)
            .build();

        entityManager.persist(inactiveUser);
        entityManager.flush();

        List<User> activeUsers = userRepository.findAllActiveUsers();

        assertEquals(1, activeUsers.size());
        assertEquals("testuser", activeUsers.get(0).getUsername());
    }
}
