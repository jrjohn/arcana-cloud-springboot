package com.arcana.cloud.dao.impl.mongodb;

import com.arcana.cloud.document.UserDocument;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserDaoMongodbImpl Unit Tests")
class UserDaoMongodbImplTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private UserDaoMongodbImpl userDao;

    private User testUser;
    private UserDocument testDocument;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("password123")
                .firstName("Test")
                .lastName("User")
                .role(UserRole.USER)
                .isActive(true)
                .isVerified(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        testDocument = UserDocument.builder()
                .id("mongo-id-123")
                .legacyId(1L)
                .username("testuser")
                .email("test@example.com")
                .password("password123")
                .firstName("Test")
                .lastName("User")
                .role(UserRole.USER)
                .isActive(true)
                .isVerified(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Nested
    @DisplayName("Save Operations")
    class SaveOperations {

        @Test
        @DisplayName("Should save new user and generate legacy ID")
        void save_NewUser_ShouldGenerateLegacyId() {
            User newUser = User.builder()
                    .username("newuser")
                    .email("new@example.com")
                    .password("password")
                    .build();

            UserDocument savedDoc = UserDocument.builder()
                    .id("new-mongo-id")
                    .legacyId(1000001L)
                    .username("newuser")
                    .email("new@example.com")
                    .password("password")
                    .build();

            when(mongoTemplate.save(any(UserDocument.class))).thenReturn(savedDoc);

            User result = userDao.save(newUser);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1000001L);
            verify(mongoTemplate).save(any(UserDocument.class));
        }

        @Test
        @DisplayName("Should update existing user")
        void save_ExistingUser_ShouldUpdate() {
            when(mongoTemplate.findOne(any(Query.class), eq(UserDocument.class))).thenReturn(testDocument);
            when(mongoTemplate.save(any(UserDocument.class))).thenReturn(testDocument);

            User result = userDao.save(testUser);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            verify(mongoTemplate).save(any(UserDocument.class));
        }

        @Test
        @DisplayName("Should save all users")
        void saveAll_ShouldSaveAllUsers() {
            User user2 = User.builder().username("user2").email("user2@example.com").build();
            List<User> users = Arrays.asList(testUser, user2);

            UserDocument savedDoc = UserDocument.builder()
                    .id("new-mongo-id")
                    .legacyId(1000001L)
                    .username("user2")
                    .email("user2@example.com")
                    .build();

            when(mongoTemplate.findOne(any(Query.class), eq(UserDocument.class))).thenReturn(testDocument);
            when(mongoTemplate.save(any(UserDocument.class))).thenReturn(testDocument).thenReturn(savedDoc);

            List<User> result = userDao.saveAll(users);

            assertThat(result).hasSize(2);
            verify(mongoTemplate, times(2)).save(any(UserDocument.class));
        }
    }

    @Nested
    @DisplayName("Find Operations")
    class FindOperations {

        @Test
        @DisplayName("Should find user by ID")
        void findById_ExistingUser_ShouldReturnUser() {
            when(mongoTemplate.findOne(any(Query.class), eq(UserDocument.class))).thenReturn(testDocument);

            Optional<User> result = userDao.findById(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("Should return empty when user not found by ID")
        void findById_NonExistingUser_ShouldReturnEmpty() {
            when(mongoTemplate.findOne(any(Query.class), eq(UserDocument.class))).thenReturn(null);

            Optional<User> result = userDao.findById(999L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should find user by username")
        void findByUsername_ShouldReturnUser() {
            when(mongoTemplate.findOne(any(Query.class), eq(UserDocument.class))).thenReturn(testDocument);

            Optional<User> result = userDao.findByUsername("testuser");

            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("Should return empty when user not found by username")
        void findByUsername_NonExisting_ShouldReturnEmpty() {
            when(mongoTemplate.findOne(any(Query.class), eq(UserDocument.class))).thenReturn(null);

            Optional<User> result = userDao.findByUsername("nonexistent");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should find user by email")
        void findByEmail_ShouldReturnUser() {
            when(mongoTemplate.findOne(any(Query.class), eq(UserDocument.class))).thenReturn(testDocument);

            Optional<User> result = userDao.findByEmail("test@example.com");

            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("Should return empty when user not found by email")
        void findByEmail_NonExisting_ShouldReturnEmpty() {
            when(mongoTemplate.findOne(any(Query.class), eq(UserDocument.class))).thenReturn(null);

            Optional<User> result = userDao.findByEmail("nonexistent@example.com");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should find user by username or email")
        void findByUsernameOrEmail_ShouldReturnUser() {
            when(mongoTemplate.findOne(any(Query.class), eq(UserDocument.class))).thenReturn(testDocument);

            Optional<User> result = userDao.findByUsernameOrEmail("testuser", "test@example.com");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("Should return empty when user not found by username or email")
        void findByUsernameOrEmail_NonExisting_ShouldReturnEmpty() {
            when(mongoTemplate.findOne(any(Query.class), eq(UserDocument.class))).thenReturn(null);

            Optional<User> result = userDao.findByUsernameOrEmail("nonexistent", "nonexistent@example.com");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should find all users")
        void findAll_ShouldReturnAllUsers() {
            UserDocument doc2 = UserDocument.builder().id("mongo-id-2").legacyId(2L).username("user2").build();
            when(mongoTemplate.findAll(UserDocument.class)).thenReturn(Arrays.asList(testDocument, doc2));

            List<User> result = userDao.findAll();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Should find all users with pagination")
        void findAll_WithPagination_ShouldReturnPage() {
            Pageable pageable = PageRequest.of(0, 10);
            when(mongoTemplate.count(any(Query.class), eq(UserDocument.class))).thenReturn(2L);
            when(mongoTemplate.find(any(Query.class), eq(UserDocument.class)))
                    .thenReturn(Collections.singletonList(testDocument));

            Page<User> result = userDao.findAll(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should find active users by role")
        void findActiveUsersByRole_ShouldReturnUsers() {
            when(mongoTemplate.find(any(Query.class), eq(UserDocument.class)))
                    .thenReturn(Collections.singletonList(testDocument));

            List<User> result = userDao.findActiveUsersByRole(UserRole.ADMIN);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Should find all active users")
        void findAllActiveUsers_ShouldReturnActiveUsers() {
            when(mongoTemplate.find(any(Query.class), eq(UserDocument.class)))
                    .thenReturn(Collections.singletonList(testDocument));

            List<User> result = userDao.findAllActiveUsers();

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Should find unverified users")
        void findUnverifiedUsers_ShouldReturnUnverifiedUsers() {
            when(mongoTemplate.find(any(Query.class), eq(UserDocument.class)))
                    .thenReturn(Collections.singletonList(testDocument));

            List<User> result = userDao.findUnverifiedUsers();

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Exists Operations")
    class ExistsOperations {

        @Test
        @DisplayName("Should return true when user exists by ID")
        void existsById_ExistingUser_ShouldReturnTrue() {
            when(mongoTemplate.exists(any(Query.class), eq(UserDocument.class))).thenReturn(true);

            boolean result = userDao.existsById(1L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when user does not exist by ID")
        void existsById_NonExistingUser_ShouldReturnFalse() {
            when(mongoTemplate.exists(any(Query.class), eq(UserDocument.class))).thenReturn(false);

            boolean result = userDao.existsById(999L);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return true when username exists")
        void existsByUsername_ShouldReturnTrue() {
            when(mongoTemplate.exists(any(Query.class), eq(UserDocument.class))).thenReturn(true);

            boolean result = userDao.existsByUsername("testuser");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return true when email exists")
        void existsByEmail_ShouldReturnTrue() {
            when(mongoTemplate.exists(any(Query.class), eq(UserDocument.class))).thenReturn(true);

            boolean result = userDao.existsByEmail("test@example.com");

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("Count Operations")
    class CountOperations {

        @Test
        @DisplayName("Should return count of all users")
        void count_ShouldReturnCount() {
            when(mongoTemplate.count(any(Query.class), eq(UserDocument.class))).thenReturn(5L);

            long result = userDao.count();

            assertThat(result).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("Delete Operations")
    class DeleteOperations {

        @Test
        @DisplayName("Should delete user by ID")
        void deleteById_ShouldDeleteUser() {
            when(mongoTemplate.remove(any(Query.class), eq(UserDocument.class)))
                    .thenReturn(mock(com.mongodb.client.result.DeleteResult.class));

            userDao.deleteById(1L);

            verify(mongoTemplate).remove(any(Query.class), eq(UserDocument.class));
        }

        @Test
        @DisplayName("Should delete user entity")
        void delete_ShouldDeleteUser() {
            when(mongoTemplate.remove(any(Query.class), eq(UserDocument.class)))
                    .thenReturn(mock(com.mongodb.client.result.DeleteResult.class));

            userDao.delete(testUser);

            verify(mongoTemplate).remove(any(Query.class), eq(UserDocument.class));
        }

        @Test
        @DisplayName("Should not delete when user ID is null")
        void delete_NullId_ShouldNotDelete() {
            User userWithoutId = User.builder().username("test").build();

            userDao.delete(userWithoutId);

            verify(mongoTemplate, never()).remove(any(Query.class), eq(UserDocument.class));
        }

        @Test
        @DisplayName("Should delete all users")
        void deleteAll_ShouldDeleteAllUsers() {
            when(mongoTemplate.remove(any(Query.class), eq(UserDocument.class)))
                    .thenReturn(mock(com.mongodb.client.result.DeleteResult.class));

            userDao.deleteAll();

            verify(mongoTemplate).remove(any(Query.class), eq(UserDocument.class));
        }

        @Test
        @DisplayName("Should delete all specified users")
        void deleteAll_WithIterable_ShouldDeleteAllSpecifiedUsers() {
            List<User> users = Arrays.asList(testUser, User.builder().id(2L).build());
            when(mongoTemplate.remove(any(Query.class), eq(UserDocument.class)))
                    .thenReturn(mock(com.mongodb.client.result.DeleteResult.class));

            userDao.deleteAll(users);

            verify(mongoTemplate, times(2)).remove(any(Query.class), eq(UserDocument.class));
        }
    }
}
