package com.arcana.cloud.dao.impl.mongodb;

import com.arcana.cloud.document.OAuthTokenDocument;
import com.arcana.cloud.document.UserDocument;
import com.arcana.cloud.entity.OAuthToken;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
@DisplayName("OAuthTokenDaoMongodbImpl Integration Tests with MongoDB")
class OAuthTokenDaoMongodbIntegrationTest {

    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:6.0");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongodb::getReplicaSetUrl);
        registry.add("database.type", () -> "mongodb");
    }

    @Autowired
    private OAuthTokenDaoMongodbImpl tokenDao;

    @Autowired
    private UserDaoMongodbImpl userDao;

    @Autowired
    private MongoTemplate mongoTemplate;

    private User testUser;
    private OAuthToken testToken;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(OAuthTokenDocument.class);
        mongoTemplate.dropCollection(UserDocument.class);

        now = LocalDateTime.now();

        testUser = User.builder()
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
        testUser = userDao.save(testUser);

        testToken = OAuthToken.builder()
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
        @DisplayName("Should save new token")
        void save_NewToken_ShouldSave() {
            OAuthToken result = tokenDao.save(testToken);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getAccessToken()).isEqualTo("access-token-123");
        }

        @Test
        @DisplayName("Should save all tokens")
        void saveAll_ShouldSaveAllTokens() {
            OAuthToken token2 = OAuthToken.builder()
                    .user(testUser)
                    .accessToken("access-token-789")
                    .refreshToken("refresh-token-012")
                    .tokenType("Bearer")
                    .expiresAt(now.plusHours(1))
                    .isRevoked(false)
                    .createdAt(now)
                    .build();

            List<OAuthToken> result = tokenDao.saveAll(Arrays.asList(testToken, token2));

            assertThat(result).hasSize(2);
            assertThat(tokenDao.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Find Operations")
    class FindOperations {

        @Test
        @DisplayName("Should find token by ID")
        void findById_ExistingToken_ShouldReturnToken() {
            OAuthToken saved = tokenDao.save(testToken);

            Optional<OAuthToken> result = tokenDao.findById(saved.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getAccessToken()).isEqualTo("access-token-123");
        }

        @Test
        @DisplayName("Should return empty when token not found")
        void findById_NonExisting_ShouldReturnEmpty() {
            Optional<OAuthToken> result = tokenDao.findById(999L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should find token by access token")
        void findByAccessToken_ShouldReturnToken() {
            tokenDao.save(testToken);

            Optional<OAuthToken> result = tokenDao.findByAccessToken("access-token-123");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("Should find non-revoked tokens by user")
        void findByUserAndIsRevokedFalse_ShouldReturnTokens() {
            tokenDao.save(testToken);
            OAuthToken revokedToken = OAuthToken.builder()
                    .user(testUser)
                    .accessToken("revoked-access")
                    .refreshToken("revoked-refresh")
                    .tokenType("Bearer")
                    .expiresAt(now.plusHours(1))
                    .isRevoked(true)
                    .createdAt(now)
                    .build();
            tokenDao.save(revokedToken);

            List<OAuthToken> result = tokenDao.findByUserAndIsRevokedFalse(testUser);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIsRevoked()).isFalse();
        }

        @Test
        @DisplayName("Should find all tokens with pagination")
        void findAll_WithPagination_ShouldReturnPage() {
            for (int i = 0; i < 15; i++) {
                OAuthToken token = OAuthToken.builder()
                        .user(testUser)
                        .accessToken("access-token-" + i)
                        .refreshToken("refresh-token-" + i)
                        .tokenType("Bearer")
                        .expiresAt(now.plusHours(1))
                        .isRevoked(false)
                        .createdAt(now)
                        .build();
                tokenDao.save(token);
            }

            Page<OAuthToken> result = tokenDao.findAll(PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(10);
            assertThat(result.getTotalElements()).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("Revoke Operations")
    class RevokeOperations {

        @Test
        @DisplayName("Should revoke all tokens by user")
        void revokeAllTokensByUser_ShouldRevokeAllUserTokens() {
            tokenDao.save(testToken);
            OAuthToken token2 = OAuthToken.builder()
                    .user(testUser)
                    .accessToken("access-token-789")
                    .refreshToken("refresh-token-012")
                    .tokenType("Bearer")
                    .expiresAt(now.plusHours(1))
                    .isRevoked(false)
                    .createdAt(now)
                    .build();
            tokenDao.save(token2);

            tokenDao.revokeAllTokensByUser(testUser);

            List<OAuthToken> nonRevoked = tokenDao.findByUserAndIsRevokedFalse(testUser);
            assertThat(nonRevoked).isEmpty();
        }
    }

    @Nested
    @DisplayName("Delete Operations")
    class DeleteOperations {

        @Test
        @DisplayName("Should delete token by ID")
        void deleteById_ShouldDeleteToken() {
            OAuthToken saved = tokenDao.save(testToken);

            tokenDao.deleteById(saved.getId());

            assertThat(tokenDao.existsById(saved.getId())).isFalse();
        }

        @Test
        @DisplayName("Should delete all tokens")
        void deleteAll_ShouldDeleteAllTokens() {
            tokenDao.save(testToken);

            tokenDao.deleteAll();

            assertThat(tokenDao.count()).isZero();
        }
    }
}
