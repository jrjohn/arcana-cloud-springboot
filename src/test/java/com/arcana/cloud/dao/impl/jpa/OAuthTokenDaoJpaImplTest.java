package com.arcana.cloud.dao.impl.jpa;

import com.arcana.cloud.dao.impl.jpa.repository.OAuthTokenJpaRepository;
import com.arcana.cloud.entity.OAuthToken;
import com.arcana.cloud.entity.User;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuthTokenDaoJpaImpl Unit Tests")
class OAuthTokenDaoJpaImplTest {

    @Mock
    private OAuthTokenJpaRepository tokenJpaRepository;

    @InjectMocks
    private OAuthTokenDaoJpaImpl tokenDao;

    private OAuthToken testToken;
    private User testUser;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .build();

        testToken = OAuthToken.builder()
                .id(1L)
                .user(testUser)
                .accessToken("access-token-123")
                .refreshToken("refresh-token-456")
                .tokenType("Bearer")
                .expiresAt(now.plusHours(1))
                .refreshExpiresAt(now.plusDays(30))
                .isRevoked(false)
                .createdAt(now)
                .clientIp("127.0.0.1")
                .userAgent("TestAgent")
                .build();
    }

    @Nested
    @DisplayName("Save Operations")
    class SaveOperations {

        @Test
        @DisplayName("Should save token")
        void save_ShouldSaveToken() {
            when(tokenJpaRepository.save(any(OAuthToken.class))).thenReturn(testToken);

            OAuthToken result = tokenDao.save(testToken);

            assertThat(result).isEqualTo(testToken);
            verify(tokenJpaRepository).save(testToken);
        }

        @Test
        @DisplayName("Should save all tokens")
        void saveAll_ShouldSaveAllTokens() {
            OAuthToken token2 = OAuthToken.builder().id(2L).user(testUser).build();
            List<OAuthToken> tokens = Arrays.asList(testToken, token2);

            when(tokenJpaRepository.saveAll(tokens)).thenReturn(tokens);

            List<OAuthToken> result = tokenDao.saveAll(tokens);

            assertThat(result).hasSize(2);
            verify(tokenJpaRepository).saveAll(tokens);
        }
    }

    @Nested
    @DisplayName("Find Operations")
    class FindOperations {

        @Test
        @DisplayName("Should find token by ID")
        void findById_ExistingToken_ShouldReturnToken() {
            when(tokenJpaRepository.findById(1L)).thenReturn(Optional.of(testToken));

            Optional<OAuthToken> result = tokenDao.findById(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getAccessToken()).isEqualTo("access-token-123");
        }

        @Test
        @DisplayName("Should return empty when token not found by ID")
        void findById_NonExistingToken_ShouldReturnEmpty() {
            when(tokenJpaRepository.findById(999L)).thenReturn(Optional.empty());

            Optional<OAuthToken> result = tokenDao.findById(999L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should find token by access token")
        void findByAccessToken_ShouldReturnToken() {
            when(tokenJpaRepository.findByAccessToken("access-token-123"))
                    .thenReturn(Optional.of(testToken));

            Optional<OAuthToken> result = tokenDao.findByAccessToken("access-token-123");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("Should find token by refresh token")
        void findByRefreshToken_ShouldReturnToken() {
            when(tokenJpaRepository.findByRefreshToken("refresh-token-456"))
                    .thenReturn(Optional.of(testToken));

            Optional<OAuthToken> result = tokenDao.findByRefreshToken("refresh-token-456");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("Should find non-revoked tokens by user")
        void findByUserAndIsRevokedFalse_ShouldReturnTokens() {
            when(tokenJpaRepository.findByUserAndIsRevokedFalse(testUser))
                    .thenReturn(Collections.singletonList(testToken));

            List<OAuthToken> result = tokenDao.findByUserAndIsRevokedFalse(testUser);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Should find valid tokens by user")
        void findValidTokensByUser_ShouldReturnValidTokens() {
            when(tokenJpaRepository.findValidTokensByUser(eq(testUser), any(LocalDateTime.class)))
                    .thenReturn(Collections.singletonList(testToken));

            List<OAuthToken> result = tokenDao.findValidTokensByUser(testUser, now);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Should find all tokens")
        void findAll_ShouldReturnAllTokens() {
            List<OAuthToken> tokens = Arrays.asList(testToken, OAuthToken.builder().id(2L).build());
            when(tokenJpaRepository.findAll()).thenReturn(tokens);

            List<OAuthToken> result = tokenDao.findAll();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Should find all tokens with pagination")
        void findAll_WithPagination_ShouldReturnPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<OAuthToken> page = new PageImpl<>(Collections.singletonList(testToken), pageable, 1);
            when(tokenJpaRepository.findAll(pageable)).thenReturn(page);

            Page<OAuthToken> result = tokenDao.findAll(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Exists Operations")
    class ExistsOperations {

        @Test
        @DisplayName("Should return true when token exists by ID")
        void existsById_ExistingToken_ShouldReturnTrue() {
            when(tokenJpaRepository.existsById(1L)).thenReturn(true);

            boolean result = tokenDao.existsById(1L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when token does not exist by ID")
        void existsById_NonExistingToken_ShouldReturnFalse() {
            when(tokenJpaRepository.existsById(999L)).thenReturn(false);

            boolean result = tokenDao.existsById(999L);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Count Operations")
    class CountOperations {

        @Test
        @DisplayName("Should return count of all tokens")
        void count_ShouldReturnCount() {
            when(tokenJpaRepository.count()).thenReturn(10L);

            long result = tokenDao.count();

            assertThat(result).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("Revoke Operations")
    class RevokeOperations {

        @Test
        @DisplayName("Should revoke all tokens by user")
        void revokeAllTokensByUser_ShouldRevokeAllUserTokens() {
            doNothing().when(tokenJpaRepository).revokeAllTokensByUser(testUser);

            tokenDao.revokeAllTokensByUser(testUser);

            verify(tokenJpaRepository).revokeAllTokensByUser(testUser);
        }

        @Test
        @DisplayName("Should revoke token by access token")
        void revokeByAccessToken_ShouldRevokeToken() {
            doNothing().when(tokenJpaRepository).revokeByAccessToken("access-token-123");

            tokenDao.revokeByAccessToken("access-token-123");

            verify(tokenJpaRepository).revokeByAccessToken("access-token-123");
        }
    }

    @Nested
    @DisplayName("Delete Operations")
    class DeleteOperations {

        @Test
        @DisplayName("Should delete token by ID")
        void deleteById_ShouldDeleteToken() {
            doNothing().when(tokenJpaRepository).deleteById(1L);

            tokenDao.deleteById(1L);

            verify(tokenJpaRepository).deleteById(1L);
        }

        @Test
        @DisplayName("Should delete token entity")
        void delete_ShouldDeleteToken() {
            doNothing().when(tokenJpaRepository).delete(testToken);

            tokenDao.delete(testToken);

            verify(tokenJpaRepository).delete(testToken);
        }

        @Test
        @DisplayName("Should delete all tokens")
        void deleteAll_ShouldDeleteAllTokens() {
            doNothing().when(tokenJpaRepository).deleteAll();

            tokenDao.deleteAll();

            verify(tokenJpaRepository).deleteAll();
        }

        @Test
        @DisplayName("Should delete all specified tokens")
        void deleteAll_WithIterable_ShouldDeleteAllSpecifiedTokens() {
            List<OAuthToken> tokens = Arrays.asList(testToken, OAuthToken.builder().id(2L).build());
            doNothing().when(tokenJpaRepository).deleteAll(tokens);

            tokenDao.deleteAll(tokens);

            verify(tokenJpaRepository).deleteAll(tokens);
        }

        @Test
        @DisplayName("Should delete expired or revoked tokens")
        void deleteExpiredOrRevokedTokens_ShouldDeleteTokens() {
            doNothing().when(tokenJpaRepository).deleteExpiredOrRevokedTokens(now);

            tokenDao.deleteExpiredOrRevokedTokens(now);

            verify(tokenJpaRepository).deleteExpiredOrRevokedTokens(now);
        }
    }
}
