package com.arcana.cloud.repository.impl;

import com.arcana.cloud.dao.interfaces.UserDao;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserRepositoryImpl Unit Tests")
class UserRepositoryImplTest {

    @Mock
    private UserDao userDao;

    @InjectMocks
    private UserRepositoryImpl userRepository;

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
        @DisplayName("Should delegate save to DAO")
        void save_ShouldDelegateToDao() {
            when(userDao.save(testUser)).thenReturn(testUser);

            User result = userRepository.save(testUser);

            assertThat(result).isEqualTo(testUser);
            verify(userDao).save(testUser);
        }

        @Test
        @DisplayName("Should delegate saveAll to DAO")
        void saveAll_ShouldDelegateToDao() {
            User user2 = User.builder().id(2L).username("user2").build();
            List<User> users = Arrays.asList(testUser, user2);

            when(userDao.saveAll(users)).thenReturn(users);

            List<User> result = userRepository.saveAll(users);

            assertThat(result).hasSize(2);
            verify(userDao).saveAll(users);
        }
    }

    @Nested
    @DisplayName("Find Operations")
    class FindOperations {

        @Test
        @DisplayName("Should delegate findById to DAO")
        void findById_ShouldDelegateToDao() {
            when(userDao.findById(1L)).thenReturn(Optional.of(testUser));

            Optional<User> result = userRepository.findById(1L);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(testUser);
            verify(userDao).findById(1L);
        }

        @Test
        @DisplayName("Should delegate findByUsername to DAO")
        void findByUsername_ShouldDelegateToDao() {
            when(userDao.findByUsername("testuser")).thenReturn(Optional.of(testUser));

            Optional<User> result = userRepository.findByUsername("testuser");

            assertThat(result).isPresent();
            verify(userDao).findByUsername("testuser");
        }

        @Test
        @DisplayName("Should delegate findByEmail to DAO")
        void findByEmail_ShouldDelegateToDao() {
            when(userDao.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

            Optional<User> result = userRepository.findByEmail("test@example.com");

            assertThat(result).isPresent();
            verify(userDao).findByEmail("test@example.com");
        }

        @Test
        @DisplayName("Should delegate findByUsernameOrEmail to DAO")
        void findByUsernameOrEmail_ShouldDelegateToDao() {
            when(userDao.findByUsernameOrEmail("testuser", "test@example.com")).thenReturn(Optional.of(testUser));

            Optional<User> result = userRepository.findByUsernameOrEmail("testuser", "test@example.com");

            assertThat(result).isPresent();
            verify(userDao).findByUsernameOrEmail("testuser", "test@example.com");
        }

        @Test
        @DisplayName("Should delegate findAll to DAO")
        void findAll_ShouldDelegateToDao() {
            List<User> users = Collections.singletonList(testUser);
            when(userDao.findAll()).thenReturn(users);

            List<User> result = userRepository.findAll();

            assertThat(result).hasSize(1);
            verify(userDao).findAll();
        }

        @Test
        @DisplayName("Should delegate findAll with pagination to DAO")
        void findAllWithPagination_ShouldDelegateToDao() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> page = new PageImpl<>(Collections.singletonList(testUser), pageable, 1);
            when(userDao.findAll(pageable)).thenReturn(page);

            Page<User> result = userRepository.findAll(pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(userDao).findAll(pageable);
        }

        @Test
        @DisplayName("Should delegate findActiveUsersByRole to DAO")
        void findActiveUsersByRole_ShouldDelegateToDao() {
            when(userDao.findActiveUsersByRole(UserRole.ADMIN)).thenReturn(Collections.singletonList(testUser));

            List<User> result = userRepository.findActiveUsersByRole(UserRole.ADMIN);

            assertThat(result).hasSize(1);
            verify(userDao).findActiveUsersByRole(UserRole.ADMIN);
        }

        @Test
        @DisplayName("Should delegate findAllActiveUsers to DAO")
        void findAllActiveUsers_ShouldDelegateToDao() {
            when(userDao.findAllActiveUsers()).thenReturn(Collections.singletonList(testUser));

            List<User> result = userRepository.findAllActiveUsers();

            assertThat(result).hasSize(1);
            verify(userDao).findAllActiveUsers();
        }

        @Test
        @DisplayName("Should delegate findUnverifiedUsers to DAO")
        void findUnverifiedUsers_ShouldDelegateToDao() {
            when(userDao.findUnverifiedUsers()).thenReturn(Collections.singletonList(testUser));

            List<User> result = userRepository.findUnverifiedUsers();

            assertThat(result).hasSize(1);
            verify(userDao).findUnverifiedUsers();
        }
    }

    @Nested
    @DisplayName("Exists Operations")
    class ExistsOperations {

        @Test
        @DisplayName("Should delegate existsById to DAO")
        void existsById_ShouldDelegateToDao() {
            when(userDao.existsById(1L)).thenReturn(true);

            boolean result = userRepository.existsById(1L);

            assertThat(result).isTrue();
            verify(userDao).existsById(1L);
        }

        @Test
        @DisplayName("Should delegate existsByUsername to DAO")
        void existsByUsername_ShouldDelegateToDao() {
            when(userDao.existsByUsername("testuser")).thenReturn(true);

            boolean result = userRepository.existsByUsername("testuser");

            assertThat(result).isTrue();
            verify(userDao).existsByUsername("testuser");
        }

        @Test
        @DisplayName("Should delegate existsByEmail to DAO")
        void existsByEmail_ShouldDelegateToDao() {
            when(userDao.existsByEmail("test@example.com")).thenReturn(true);

            boolean result = userRepository.existsByEmail("test@example.com");

            assertThat(result).isTrue();
            verify(userDao).existsByEmail("test@example.com");
        }
    }

    @Nested
    @DisplayName("Count Operations")
    class CountOperations {

        @Test
        @DisplayName("Should delegate count to DAO")
        void count_ShouldDelegateToDao() {
            when(userDao.count()).thenReturn(5L);

            long result = userRepository.count();

            assertThat(result).isEqualTo(5L);
            verify(userDao).count();
        }
    }

    @Nested
    @DisplayName("Delete Operations")
    class DeleteOperations {

        @Test
        @DisplayName("Should delegate deleteById to DAO")
        void deleteById_ShouldDelegateToDao() {
            doNothing().when(userDao).deleteById(1L);

            userRepository.deleteById(1L);

            verify(userDao).deleteById(1L);
        }

        @Test
        @DisplayName("Should delegate delete to DAO")
        void delete_ShouldDelegateToDao() {
            doNothing().when(userDao).delete(testUser);

            userRepository.delete(testUser);

            verify(userDao).delete(testUser);
        }

        @Test
        @DisplayName("Should delegate deleteAll to DAO")
        void deleteAll_ShouldDelegateToDao() {
            doNothing().when(userDao).deleteAll();

            userRepository.deleteAll();

            verify(userDao).deleteAll();
        }
    }
}
