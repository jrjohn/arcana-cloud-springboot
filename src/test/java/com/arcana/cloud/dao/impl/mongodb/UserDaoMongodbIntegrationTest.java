package com.arcana.cloud.dao.impl.mongodb;

import com.arcana.cloud.document.UserDocument;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test-mongodb")
@DisplayName("UserDaoMongodbImpl Integration Tests with MongoDB")
class UserDaoMongodbIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:6.0")
            .withStartupTimeout(java.time.Duration.ofMinutes(2));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MongoDB connection is auto-configured via @ServiceConnection
        registry.add("database.type", () -> "mongodb");
        registry.add("database.orm", () -> "mongodb");
        // Disable gRPC server
        registry.add("spring.grpc.server.enabled", () -> "false");
        registry.add("spring.grpc.server.port", () -> "-1");
        registry.add("grpc.server.port", () -> "-1");
    }

    @Autowired
    private UserDaoMongodbImpl userDao;

    @Autowired
    private MongoTemplate mongoTemplate;

    private User testUser;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(UserDocument.class);

        testUser = User.builder()
                .username("testuser")
                .email("test@example.com")
                .password("password123")
                .firstName("Test")
                .lastName("User")
                .role(UserRole.USER)
                .isActive(true)
                .isVerified(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("Save Operations")
    class SaveOperations {

        @Test
        @DisplayName("Should save new user")
        void save_NewUser_ShouldSave() {
            User result = userDao.save(testUser);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("Should update existing user")
        void save_ExistingUser_ShouldUpdate() {
            User saved = userDao.save(testUser);
            saved.setFirstName("Updated");

            User result = userDao.save(saved);

            Optional<User> found = userDao.findById(result.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getFirstName()).isEqualTo("Updated");
        }

        @Test
        @DisplayName("Should save all users")
        void saveAll_ShouldSaveAllUsers() {
            User user2 = User.builder()
                    .username("user2")
                    .email("user2@example.com")
                    .password("password")
                    .role(UserRole.USER)
                    .isActive(true)
                    .isVerified(false)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            List<User> result = userDao.saveAll(Arrays.asList(testUser, user2));

            assertThat(result).hasSize(2);
            assertThat(userDao.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Find Operations")
    class FindOperations {

        @Test
        @DisplayName("Should find user by ID")
        void findById_ExistingUser_ShouldReturnUser() {
            User saved = userDao.save(testUser);

            Optional<User> result = userDao.findById(saved.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("Should return empty when user not found")
        void findById_NonExisting_ShouldReturnEmpty() {
            Optional<User> result = userDao.findById(999L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should find user by username")
        void findByUsername_ShouldReturnUser() {
            userDao.save(testUser);

            Optional<User> result = userDao.findByUsername("testuser");

            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("Should find user by email")
        void findByEmail_ShouldReturnUser() {
            userDao.save(testUser);

            Optional<User> result = userDao.findByEmail("test@example.com");

            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("Should find all users")
        void findAll_ShouldReturnAllUsers() {
            userDao.save(testUser);
            User user2 = User.builder()
                    .username("user2")
                    .email("user2@example.com")
                    .password("password")
                    .role(UserRole.USER)
                    .isActive(true)
                    .isVerified(false)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userDao.save(user2);

            List<User> result = userDao.findAll();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Should find all users with pagination")
        void findAll_WithPagination_ShouldReturnPage() {
            for (int i = 0; i < 15; i++) {
                User user = User.builder()
                        .username("user" + i)
                        .email("user" + i + "@example.com")
                        .password("password")
                        .role(UserRole.USER)
                        .isActive(true)
                        .isVerified(false)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                userDao.save(user);
            }

            Page<User> result = userDao.findAll(PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(10);
            assertThat(result.getTotalElements()).isEqualTo(15);
        }

        @Test
        @DisplayName("Should find active users by role")
        void findActiveUsersByRole_ShouldReturnUsers() {
            testUser.setRole(UserRole.ADMIN);
            userDao.save(testUser);

            List<User> result = userDao.findActiveUsersByRole(UserRole.ADMIN);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getRole()).isEqualTo(UserRole.ADMIN);
        }
    }

    @Nested
    @DisplayName("Exists Operations")
    class ExistsOperations {

        @Test
        @DisplayName("Should return true when user exists by ID")
        void existsById_Existing_ShouldReturnTrue() {
            User saved = userDao.save(testUser);

            boolean result = userDao.existsById(saved.getId());

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when user does not exist by ID")
        void existsById_NonExisting_ShouldReturnFalse() {
            boolean result = userDao.existsById(999L);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Delete Operations")
    class DeleteOperations {

        @Test
        @DisplayName("Should delete user by ID")
        void deleteById_ShouldDeleteUser() {
            User saved = userDao.save(testUser);

            userDao.deleteById(saved.getId());

            assertThat(userDao.existsById(saved.getId())).isFalse();
        }

        @Test
        @DisplayName("Should delete all users")
        void deleteAll_ShouldDeleteAllUsers() {
            userDao.save(testUser);

            userDao.deleteAll();

            assertThat(userDao.count()).isZero();
        }
    }
}
