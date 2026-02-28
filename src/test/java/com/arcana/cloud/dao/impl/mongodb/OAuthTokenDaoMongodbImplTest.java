package com.arcana.cloud.dao.impl.mongodb;

import com.arcana.cloud.dao.UserDao;
import com.arcana.cloud.document.OAuthTokenDocument;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

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
@DisplayName("OAuthTokenDaoMongodbImpl Unit Tests")
class OAuthTokenDaoMongodbImplTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private UserDao userDao;

    private OAuthTokenDaoMongodbImpl tokenDao;

    private OAuthToken testToken;
    private OAuthTokenDocument testDocument;
    private User testUser;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        tokenDao = new OAuthTokenDaoMongodbImpl(mongoTemplate, userDao);
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

        testDocument = OAuthTokenDocument.builder()
                .id("mongo-id-123")
                .legacyId(1L)
                .userLegacyId(1L)
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
        @DisplayName("Should save new token and generate legacy ID")
        void save_NewToken_ShouldGenerateLegacyId() {
            OAuthToken newToken = OAuthToken.builder()
                    .user(testUser)
                    .accessToken("new-access-token")
                    .refreshToken("new-refresh-token")
                    .build();

            OAuthTokenDocument savedDoc = OAuthTokenDocument.builder()
                    .id("new-mongo-id")
                    .legacyId(1000001L)
                    .userLegacyId(1L)
                    .accessToken("new-access-token")
                    .refreshToken("new-refresh-token")
                    .build();

            when(mongoTemplate.save(any(OAuthTokenDocument.class))).thenReturn(savedDoc);
            when(userDao.findById(1L)).thenReturn(Optional.of(testUser));

            OAuthToken result = tokenDao.save(newToken);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1000001L);
            verify(mongoTemplate).save(any(OAuthTokenDocument.class));
        }

        @Test
        @DisplayName("Should update existing token")
        void save_ExistingToken_ShouldUpdate() {
            when(mongoTemplate.findOne(any(Query.class), eq(OAuthTokenDocument.class))).thenReturn(testDocument);
            when(mongoTemplate.save(any(OAuthTokenDocument.class))).thenReturn(testDocument);
            when(userDao.findById(1L)).thenReturn(Optional.of(testUser));

            OAuthToken result = tokenDao.save(testToken);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            verify(mongoTemplate).save(any(OAuthTokenDocument.class));
        }

        @Test
        @DisplayName("Should save all tokens")
        void saveAll_ShouldSaveAllTokens() {
            OAuthToken token2 = OAuthToken.builder().user(testUser).accessToken("token2").build();
            List<OAuthToken> tokens = Arrays.asList(testToken, token2);

            OAuthTokenDocument savedDoc = OAuthTokenDocument.builder()
                    .id("new-mongo-id")
                    .legacyId(1000001L)
                    .userLegacyId(1L)
                    .accessToken("token2")
                    .build();

            when(mongoTemplate.findOne(any(Query.class), eq(OAuthTokenDocument.class))).thenReturn(testDocument);
            when(mongoTemplate.save(any(OAuthTokenDocument.class))).thenReturn(testDocument).thenReturn(savedDoc);
            when(userDao.findById(1L)).thenReturn(Optional.of(testUser));

            List<OAuthToken> result = tokenDao.saveAll(tokens);

            assertThat(result).hasSize(2);
            verify(mongoTemplate, times(2)).save(any(OAuthTokenDocument.class));
        }
    }

    @Nested
    @DisplayName("Find Operations")
    class FindOperations {

        @Test
        @DisplayName("Should find token by ID")
        void findById_ExistingToken_ShouldReturnToken() {
            when(mongoTemplate.findOne(any(Query.class), eq(OAuthTokenDocument.class))).thenReturn(testDocument);
            when(userDao.findById(1L)).thenReturn(Optional.of(testUser));

            Optional<OAuthToken> result = tokenDao.findById(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getAccessToken()).isEqualTo("access-token-123");
        }

        @Test
        @DisplayName("Should return empty when token not found by ID")
        void findById_NonExistingToken_ShouldReturnEmpty() {
            when(mongoTemplate.findOne(any(Query.class), eq(OAuthTokenDocument.class))).thenReturn(null);

            Optional<OAuthToken> result = tokenDao.findById(999L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should find token by access token")
        void findByAccessToken_ShouldReturnToken() {
            when(mongoTemplate.findOne(any(Query.class), eq(OAuthTokenDocument.class))).thenReturn(testDocument);
            when(userDao.findById(1L)).thenReturn(Optional.of(testUser));

            Optional<OAuthToken> result = tokenDao.findByAccessToken("access-token-123");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("Should return empty when token not found by access token")
        void findByAccessToken_NonExisting_ShouldReturnEmpty() {
            when(mongoTemplate.findOne(any(Query.class), eq(OAuthTokenDocument.class))).thenReturn(null);

            Optional<OAuthToken> result = tokenDao.findByAccessToken("nonexistent");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should find token by refresh token")
        void findByRefreshToken_ShouldReturnToken() {
            when(mongoTemplate.findOne(any(Query.class), eq(OAuthTokenDocument.class))).thenReturn(testDocument);
            when(userDao.findById(1L)).thenReturn(Optional.of(testUser));

            Optional<OAuthToken> result = tokenDao.findByRefreshToken("refresh-token-456");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("Should return empty when token not found by refresh token")
        void findByRefreshToken_NonExisting_ShouldReturnEmpty() {
            when(mongoTemplate.findOne(any(Query.class), eq(OAuthTokenDocument.class))).thenReturn(null);

            Optional<OAuthToken> result = tokenDao.findByRefreshToken("nonexistent");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should find non-revoked tokens by user")
        void findByUserAndIsRevokedFalse_ShouldReturnTokens() {
            when(mongoTemplate.find(any(Query.class), eq(OAuthTokenDocument.class)))
                    .thenReturn(Collections.singletonList(testDocument));

            List<OAuthToken> result = tokenDao.findByUserAndIsRevokedFalse(testUser);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Should find valid tokens by user")
        void findValidTokensByUser_ShouldReturnValidTokens() {
            when(mongoTemplate.find(any(Query.class), eq(OAuthTokenDocument.class)))
                    .thenReturn(Collections.singletonList(testDocument));

            List<OAuthToken> result = tokenDao.findValidTokensByUser(testUser, now);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Should find all tokens")
        void findAll_ShouldReturnAllTokens() {
            OAuthTokenDocument doc2 = OAuthTokenDocument.builder().id("mongo-id-2").legacyId(2L).build();
            when(mongoTemplate.findAll(OAuthTokenDocument.class)).thenReturn(Arrays.asList(testDocument, doc2));
            when(userDao.findById(any())).thenReturn(Optional.of(testUser));

            List<OAuthToken> result = tokenDao.findAll();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Should find all tokens with pagination")
        void findAll_WithPagination_ShouldReturnPage() {
            Pageable pageable = PageRequest.of(0, 10);
            doReturn(15L).when(mongoTemplate).count(any(Query.class), eq(OAuthTokenDocument.class));
            doReturn(Collections.singletonList(testDocument)).when(mongoTemplate).find(any(Query.class), eq(OAuthTokenDocument.class));
            doReturn(Optional.of(testUser)).when(userDao).findById(any());

            Page<OAuthToken> result = tokenDao.findAll(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("Exists Operations")
    class ExistsOperations {

        @Test
        @DisplayName("Should return true when token exists by ID")
        void existsById_ExistingToken_ShouldReturnTrue() {
            when(mongoTemplate.exists(any(Query.class), eq(OAuthTokenDocument.class))).thenReturn(true);

            boolean result = tokenDao.existsById(1L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when token does not exist by ID")
        void existsById_NonExistingToken_ShouldReturnFalse() {
            when(mongoTemplate.exists(any(Query.class), eq(OAuthTokenDocument.class))).thenReturn(false);

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
            when(mongoTemplate.count(any(Query.class), eq(OAuthTokenDocument.class))).thenReturn(10L);

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
            when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(OAuthTokenDocument.class)))
                    .thenReturn(mock(com.mongodb.client.result.UpdateResult.class));

            tokenDao.revokeAllTokensByUser(testUser);

            verify(mongoTemplate).updateMulti(any(Query.class), any(Update.class), eq(OAuthTokenDocument.class));
        }

        @Test
        @DisplayName("Should revoke token by access token")
        void revokeByAccessToken_ShouldRevokeToken() {
            when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(OAuthTokenDocument.class)))
                    .thenReturn(mock(com.mongodb.client.result.UpdateResult.class));

            tokenDao.revokeByAccessToken("access-token-123");

            verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(OAuthTokenDocument.class));
        }
    }

    @Nested
    @DisplayName("Delete Operations")
    class DeleteOperations {

        @Test
        @DisplayName("Should delete token by ID")
        void deleteById_ShouldDeleteToken() {
            when(mongoTemplate.remove(any(Query.class), eq(OAuthTokenDocument.class)))
                    .thenReturn(mock(com.mongodb.client.result.DeleteResult.class));

            tokenDao.deleteById(1L);

            verify(mongoTemplate).remove(any(Query.class), eq(OAuthTokenDocument.class));
        }

        @Test
        @DisplayName("Should delete token entity")
        void delete_ShouldDeleteToken() {
            when(mongoTemplate.remove(any(Query.class), eq(OAuthTokenDocument.class)))
                    .thenReturn(mock(com.mongodb.client.result.DeleteResult.class));

            tokenDao.delete(testToken);

            verify(mongoTemplate).remove(any(Query.class), eq(OAuthTokenDocument.class));
        }

        @Test
        @DisplayName("Should not delete when token ID is null")
        void delete_NullId_ShouldNotDelete() {
            OAuthToken tokenWithoutId = OAuthToken.builder().accessToken("test").build();

            tokenDao.delete(tokenWithoutId);

            verify(mongoTemplate, never()).remove(any(Query.class), eq(OAuthTokenDocument.class));
        }

        @Test
        @DisplayName("Should delete all tokens")
        void deleteAll_ShouldDeleteAllTokens() {
            when(mongoTemplate.remove(any(Query.class), eq(OAuthTokenDocument.class)))
                    .thenReturn(mock(com.mongodb.client.result.DeleteResult.class));

            tokenDao.deleteAll();

            verify(mongoTemplate).remove(any(Query.class), eq(OAuthTokenDocument.class));
        }

        @Test
        @DisplayName("Should delete all specified tokens")
        void deleteAll_WithIterable_ShouldDeleteAllSpecifiedTokens() {
            List<OAuthToken> tokens = Arrays.asList(testToken, OAuthToken.builder().id(2L).build());
            when(mongoTemplate.remove(any(Query.class), eq(OAuthTokenDocument.class)))
                    .thenReturn(mock(com.mongodb.client.result.DeleteResult.class));

            tokenDao.deleteAll(tokens);

            verify(mongoTemplate, times(2)).remove(any(Query.class), eq(OAuthTokenDocument.class));
        }

        @Test
        @DisplayName("Should delete expired or revoked tokens")
        void deleteExpiredOrRevokedTokens_ShouldDeleteTokens() {
            when(mongoTemplate.remove(any(Query.class), eq(OAuthTokenDocument.class)))
                    .thenReturn(mock(com.mongodb.client.result.DeleteResult.class));

            tokenDao.deleteExpiredOrRevokedTokens(now);

            verify(mongoTemplate).remove(any(Query.class), eq(OAuthTokenDocument.class));
        }
    }
}
