package com.arcana.cloud.dao.impl.mybatis;

import com.arcana.cloud.dao.impl.mybatis.mapper.OAuthTokenMapper;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuthTokenDaoMybatisImpl Unit Tests")
class OAuthTokenDaoMybatisImplTest {

    @Mock
    private OAuthTokenMapper tokenMapper;

    @InjectMocks
    private OAuthTokenDaoMybatisImpl tokenDao;

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
        @DisplayName("Should insert new token when ID is null")
        void save_NewToken_ShouldInsert() {
            OAuthToken newToken = OAuthToken.builder()
                    .user(testUser)
                    .accessToken("new-access-token")
                    .refreshToken("new-refresh-token")
                    .build();

            when(tokenMapper.insert(any(OAuthToken.class))).thenReturn(1);

            OAuthToken result = tokenDao.save(newToken);

            verify(tokenMapper).insert(any(OAuthToken.class));
            verify(tokenMapper, never()).update(any(OAuthToken.class));
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should update existing token when ID is not null")
        void save_ExistingToken_ShouldUpdate() {
            when(tokenMapper.update(any(OAuthToken.class))).thenReturn(1);

            OAuthToken result = tokenDao.save(testToken);

            verify(tokenMapper).update(testToken);
            verify(tokenMapper, never()).insert(any(OAuthToken.class));
            assertThat(result).isEqualTo(testToken);
        }

        @Test
        @DisplayName("Should save all tokens")
        void saveAll_ShouldSaveAllTokens() {
            OAuthToken token2 = OAuthToken.builder().id(2L).user(testUser).build();
            List<OAuthToken> tokens = Arrays.asList(testToken, token2);

            when(tokenMapper.update(any(OAuthToken.class))).thenReturn(1);

            List<OAuthToken> result = tokenDao.saveAll(tokens);

            assertThat(result).hasSize(2);
            verify(tokenMapper, times(2)).update(any(OAuthToken.class));
        }
    }

    @Nested
    @DisplayName("Find Operations")
    class FindOperations {

        @Test
        @DisplayName("Should find token by ID")
        void findById_ExistingToken_ShouldReturnToken() {
            when(tokenMapper.findById(1L)).thenReturn(Optional.of(testToken));

            Optional<OAuthToken> result = tokenDao.findById(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getAccessToken()).isEqualTo("access-token-123");
        }

        @Test
        @DisplayName("Should return empty when token not found by ID")
        void findById_NonExistingToken_ShouldReturnEmpty() {
            when(tokenMapper.findById(999L)).thenReturn(Optional.empty());

            Optional<OAuthToken> result = tokenDao.findById(999L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should find token by access token")
        void findByAccessToken_ShouldReturnToken() {
            when(tokenMapper.findByAccessToken("access-token-123"))
                    .thenReturn(Optional.of(testToken));

            Optional<OAuthToken> result = tokenDao.findByAccessToken("access-token-123");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("Should find token by refresh token")
        void findByRefreshToken_ShouldReturnToken() {
            when(tokenMapper.findByRefreshToken("refresh-token-456"))
                    .thenReturn(Optional.of(testToken));

            Optional<OAuthToken> result = tokenDao.findByRefreshToken("refresh-token-456");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("Should find non-revoked tokens by user")
        void findByUserAndIsRevokedFalse_ShouldReturnTokens() {
            when(tokenMapper.findByUserIdAndIsRevokedFalse(1L))
                    .thenReturn(Collections.singletonList(testToken));

            List<OAuthToken> result = tokenDao.findByUserAndIsRevokedFalse(testUser);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Should find valid tokens by user")
        void findValidTokensByUser_ShouldReturnValidTokens() {
            when(tokenMapper.findValidTokensByUserId(eq(1L), any(LocalDateTime.class)))
                    .thenReturn(Collections.singletonList(testToken));

            List<OAuthToken> result = tokenDao.findValidTokensByUser(testUser, now);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Should find all tokens")
        void findAll_ShouldReturnAllTokens() {
            List<OAuthToken> tokens = Arrays.asList(testToken, OAuthToken.builder().id(2L).build());
            when(tokenMapper.findAll()).thenReturn(tokens);

            List<OAuthToken> result = tokenDao.findAll();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Should find all tokens with pagination")
        void findAll_WithPagination_ShouldReturnPage() {
            Pageable pageable = PageRequest.of(0, 10);
            when(tokenMapper.count()).thenReturn(2L);
            when(tokenMapper.findAllWithPagination(0L, 10))
                    .thenReturn(Collections.singletonList(testToken));

            Page<OAuthToken> result = tokenDao.findAll(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Exists Operations")
    class ExistsOperations {

        @Test
        @DisplayName("Should return true when token exists by ID")
        void existsById_ExistingToken_ShouldReturnTrue() {
            when(tokenMapper.existsById(1L)).thenReturn(true);

            boolean result = tokenDao.existsById(1L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when token does not exist by ID")
        void existsById_NonExistingToken_ShouldReturnFalse() {
            when(tokenMapper.existsById(999L)).thenReturn(false);

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
            when(tokenMapper.count()).thenReturn(10L);

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
            when(tokenMapper.revokeAllTokensByUserId(1L)).thenReturn(3);

            tokenDao.revokeAllTokensByUser(testUser);

            verify(tokenMapper).revokeAllTokensByUserId(1L);
        }

        @Test
        @DisplayName("Should revoke token by access token")
        void revokeByAccessToken_ShouldRevokeToken() {
            when(tokenMapper.revokeByAccessToken("access-token-123")).thenReturn(1);

            tokenDao.revokeByAccessToken("access-token-123");

            verify(tokenMapper).revokeByAccessToken("access-token-123");
        }
    }

    @Nested
    @DisplayName("Delete Operations")
    class DeleteOperations {

        @Test
        @DisplayName("Should delete token by ID")
        void deleteById_ShouldDeleteToken() {
            when(tokenMapper.deleteById(1L)).thenReturn(1);

            tokenDao.deleteById(1L);

            verify(tokenMapper).deleteById(1L);
        }

        @Test
        @DisplayName("Should delete token entity")
        void delete_ShouldDeleteToken() {
            when(tokenMapper.deleteById(1L)).thenReturn(1);

            tokenDao.delete(testToken);

            verify(tokenMapper).deleteById(1L);
        }

        @Test
        @DisplayName("Should not delete when token ID is null")
        void delete_NullId_ShouldNotDelete() {
            OAuthToken tokenWithoutId = OAuthToken.builder().accessToken("test").build();

            tokenDao.delete(tokenWithoutId);

            verify(tokenMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("Should delete all tokens")
        void deleteAll_ShouldDeleteAllTokens() {
            when(tokenMapper.deleteAll()).thenReturn(10);

            tokenDao.deleteAll();

            verify(tokenMapper).deleteAll();
        }

        @Test
        @DisplayName("Should delete all specified tokens")
        void deleteAll_WithIterable_ShouldDeleteAllSpecifiedTokens() {
            List<OAuthToken> tokens = Arrays.asList(testToken, OAuthToken.builder().id(2L).build());
            when(tokenMapper.deleteById(anyLong())).thenReturn(1);

            tokenDao.deleteAll(tokens);

            verify(tokenMapper, times(2)).deleteById(anyLong());
        }

        @Test
        @DisplayName("Should delete expired or revoked tokens")
        void deleteExpiredOrRevokedTokens_ShouldDeleteTokens() {
            when(tokenMapper.deleteExpiredOrRevokedTokens(any(LocalDateTime.class))).thenReturn(5);

            tokenDao.deleteExpiredOrRevokedTokens(now);

            verify(tokenMapper).deleteExpiredOrRevokedTokens(now);
        }
    }
}
