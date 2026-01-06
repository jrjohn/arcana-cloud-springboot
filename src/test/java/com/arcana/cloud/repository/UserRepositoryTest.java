package com.arcana.cloud.repository;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.repository.interfaces.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test-jpa")
class UserRepositoryTest {

    @SuppressWarnings("resource")
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("arcana_cloud_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("database.type", () -> "mysql");
        registry.add("database.orm", () -> "jpa");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
        // Disable gRPC server
        registry.add("spring.grpc.server.enabled", () -> "false");
        registry.add("spring.grpc.server.port", () -> "-1");
        registry.add("grpc.server.port", () -> "-1");
    }

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        LocalDateTime now = LocalDateTime.now();
        testUser = User.builder()
            .username("testuser")
            .email("test@example.com")
            .password("encoded_password")
            .firstName("Test")
            .lastName("User")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(false)
            .createdAt(now)
            .updatedAt(now)
            .build();

        testUser = userRepository.save(testUser);
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
        LocalDateTime now = LocalDateTime.now();
        User adminUser = User.builder()
            .username("admin")
            .email("admin@example.com")
            .password("encoded_password")
            .role(UserRole.ADMIN)
            .isActive(true)
            .createdAt(now)
            .updatedAt(now)
            .build();

        userRepository.save(adminUser);

        List<User> activeAdmins = userRepository.findActiveUsersByRole(UserRole.ADMIN);

        assertEquals(1, activeAdmins.size());
        assertEquals("admin", activeAdmins.get(0).getUsername());
    }

    @Test
    void testFindAllActiveUsers() {
        LocalDateTime now = LocalDateTime.now();
        User inactiveUser = User.builder()
            .username("inactive")
            .email("inactive@example.com")
            .password("encoded_password")
            .role(UserRole.USER)
            .isActive(false)
            .createdAt(now)
            .updatedAt(now)
            .build();

        userRepository.save(inactiveUser);

        List<User> activeUsers = userRepository.findAllActiveUsers();

        assertEquals(1, activeUsers.size());
        assertEquals("testuser", activeUsers.get(0).getUsername());
    }
}
