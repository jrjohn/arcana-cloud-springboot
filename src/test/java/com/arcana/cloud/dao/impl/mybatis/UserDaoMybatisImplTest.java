package com.arcana.cloud.dao.impl.mybatis;

import com.arcana.cloud.dao.impl.mybatis.mapper.UserMapper;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserDaoMybatisImpl Unit Tests")
class UserDaoMybatisImplTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserDaoMybatisImpl userDao;

    private User testUser;

    @BeforeEach
    void setUp() {
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
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("Save Operations")
    class SaveOperations {

        @Test
        @DisplayName("Should insert new user when ID is null")
        void save_NewUser_ShouldInsert() {
            User newUser = User.builder()
                    .username("newuser")
                    .email("new@example.com")
                    .password("password")
                    .build();

            when(userMapper.insert(any(User.class))).thenReturn(1);

            User result = userDao.save(newUser);

            verify(userMapper).insert(any(User.class));
            verify(userMapper, never()).update(any(User.class));
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should update existing user when ID is not null")
        void save_ExistingUser_ShouldUpdate() {
            when(userMapper.update(any(User.class))).thenReturn(1);

            User result = userDao.save(testUser);

            verify(userMapper).update(testUser);
            verify(userMapper, never()).insert(any(User.class));
            assertThat(result).isEqualTo(testUser);
        }

        @Test
        @DisplayName("Should save all users")
        void saveAll_ShouldSaveAllUsers() {
            User user2 = User.builder().id(2L).username("user2").build();
            List<User> users = Arrays.asList(testUser, user2);

            when(userMapper.update(any(User.class))).thenReturn(1);

            List<User> result = userDao.saveAll(users);

            assertThat(result).hasSize(2);
            verify(userMapper, times(2)).update(any(User.class));
        }
    }

    @Nested
    @DisplayName("Find Operations")
    class FindOperations {

        @Test
        @DisplayName("Should find user by ID")
        void findById_ExistingUser_ShouldReturnUser() {
            when(userMapper.findById(1L)).thenReturn(Optional.of(testUser));

            Optional<User> result = userDao.findById(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("Should return empty when user not found by ID")
        void findById_NonExistingUser_ShouldReturnEmpty() {
            when(userMapper.findById(999L)).thenReturn(Optional.empty());

            Optional<User> result = userDao.findById(999L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should find user by username")
        void findByUsername_ShouldReturnUser() {
            when(userMapper.findByUsername("testuser")).thenReturn(Optional.of(testUser));

            Optional<User> result = userDao.findByUsername("testuser");

            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("Should find user by email")
        void findByEmail_ShouldReturnUser() {
            when(userMapper.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            Optional<User> result = userDao.findByEmail("test@example.com");

            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("Should find user by username or email")
        void findByUsernameOrEmail_ShouldReturnUser() {
            when(userMapper.findByUsernameOrEmail("testuser", "test@example.com"))
                    .thenReturn(Optional.of(testUser));

            Optional<User> result = userDao.findByUsernameOrEmail("testuser", "test@example.com");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("Should find all users")
        void findAll_ShouldReturnAllUsers() {
            List<User> users = Arrays.asList(testUser, User.builder().id(2L).build());
            when(userMapper.findAll()).thenReturn(users);

            List<User> result = userDao.findAll();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Should find all users with pagination")
        void findAll_WithPagination_ShouldReturnPage() {
            Pageable pageable = PageRequest.of(0, 10);
            List<User> userList = Arrays.asList(testUser, User.builder().id(2L).build());
            when(userMapper.count()).thenReturn((long) userList.size());
            when(userMapper.findAllWithPagination(anyLong(), anyInt()))
                    .thenReturn(userList);

            Page<User> result = userDao.findAll(pageable);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
            verify(userMapper).count();
            verify(userMapper).findAllWithPagination(0L, 10);
        }

        @Test
        @DisplayName("Should find active users by role")
        void findActiveUsersByRole_ShouldReturnUsers() {
            when(userMapper.findActiveUsersByRole(UserRole.ADMIN))
                    .thenReturn(Collections.singletonList(testUser));

            List<User> result = userDao.findActiveUsersByRole(UserRole.ADMIN);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Should find all active users")
        void findAllActiveUsers_ShouldReturnActiveUsers() {
            when(userMapper.findAllActiveUsers())
                    .thenReturn(Collections.singletonList(testUser));

            List<User> result = userDao.findAllActiveUsers();

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Should find unverified users")
        void findUnverifiedUsers_ShouldReturnUnverifiedUsers() {
            when(userMapper.findUnverifiedUsers())
                    .thenReturn(Collections.singletonList(testUser));

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
            when(userMapper.existsById(1L)).thenReturn(true);

            boolean result = userDao.existsById(1L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when user does not exist by ID")
        void existsById_NonExistingUser_ShouldReturnFalse() {
            when(userMapper.existsById(999L)).thenReturn(false);

            boolean result = userDao.existsById(999L);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return true when username exists")
        void existsByUsername_ShouldReturnTrue() {
            when(userMapper.existsByUsername("testuser")).thenReturn(true);

            boolean result = userDao.existsByUsername("testuser");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return true when email exists")
        void existsByEmail_ShouldReturnTrue() {
            when(userMapper.existsByEmail("test@example.com")).thenReturn(true);

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
            when(userMapper.count()).thenReturn(5L);

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
            when(userMapper.deleteById(1L)).thenReturn(1);

            userDao.deleteById(1L);

            verify(userMapper).deleteById(1L);
        }

        @Test
        @DisplayName("Should delete user entity")
        void delete_ShouldDeleteUser() {
            when(userMapper.deleteById(1L)).thenReturn(1);

            userDao.delete(testUser);

            verify(userMapper).deleteById(1L);
        }

        @Test
        @DisplayName("Should not delete when user ID is null")
        void delete_NullId_ShouldNotDelete() {
            User userWithoutId = User.builder().username("test").build();

            userDao.delete(userWithoutId);

            verify(userMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("Should delete all users")
        void deleteAll_ShouldDeleteAllUsers() {
            when(userMapper.deleteAll()).thenReturn(5);

            userDao.deleteAll();

            verify(userMapper).deleteAll();
        }

        @Test
        @DisplayName("Should delete all specified users")
        void deleteAll_WithIterable_ShouldDeleteAllSpecifiedUsers() {
            List<User> users = Arrays.asList(testUser, User.builder().id(2L).build());
            when(userMapper.deleteById(anyLong())).thenReturn(1);

            userDao.deleteAll(users);

            verify(userMapper, times(2)).deleteById(anyLong());
        }
    }
}
