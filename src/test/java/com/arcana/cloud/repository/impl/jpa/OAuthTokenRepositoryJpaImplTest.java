package com.arcana.cloud.repository.impl.jpa;

import com.arcana.cloud.dao.OAuthTokenDao;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuthTokenRepositoryJpaImpl Unit Tests")
class OAuthTokenRepositoryJpaImplTest {

    @Mock
    private OAuthTokenDao tokenDao;

    @InjectMocks
    private OAuthTokenRepositoryJpaImpl tokenRepository;

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
                .build();
    }

    @Nested
    @DisplayName("Save Operations")
    class SaveOperations {

        @Test
        @DisplayName("Should delegate save to DAO")
        void save_ShouldDelegateToDao() {
            when(tokenDao.save(testToken)).thenReturn(testToken);

            OAuthToken result = tokenRepository.save(testToken);

            assertThat(result).isEqualTo(testToken);
            verify(tokenDao).save(testToken);
        }

        @Test
        @DisplayName("Should delegate saveAll to DAO")
        void saveAll_ShouldDelegateToDao() {
            OAuthToken token2 = OAuthToken.builder().id(2L).user(testUser).build();
            List<OAuthToken> tokens = Arrays.asList(testToken, token2);

            when(tokenDao.saveAll(tokens)).thenReturn(tokens);

            List<OAuthToken> result = tokenRepository.saveAll(tokens);

            assertThat(result).hasSize(2);
            verify(tokenDao).saveAll(tokens);
        }
    }

    @Nested
    @DisplayName("Find Operations")
    class FindOperations {

        @Test
        @DisplayName("Should delegate findById to DAO")
        void findById_ShouldDelegateToDao() {
            when(tokenDao.findById(1L)).thenReturn(Optional.of(testToken));

            Optional<OAuthToken> result = tokenRepository.findById(1L);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(testToken);
            verify(tokenDao).findById(1L);
        }

        @Test
        @DisplayName("Should delegate findByAccessToken to DAO")
        void findByAccessToken_ShouldDelegateToDao() {
            when(tokenDao.findByAccessToken("access-token-123")).thenReturn(Optional.of(testToken));

            Optional<OAuthToken> result = tokenRepository.findByAccessToken("access-token-123");

            assertThat(result).isPresent();
            verify(tokenDao).findByAccessToken("access-token-123");
        }

        @Test
        @DisplayName("Should delegate findByRefreshToken to DAO")
        void findByRefreshToken_ShouldDelegateToDao() {
            when(tokenDao.findByRefreshToken("refresh-token-456")).thenReturn(Optional.of(testToken));

            Optional<OAuthToken> result = tokenRepository.findByRefreshToken("refresh-token-456");

            assertThat(result).isPresent();
            verify(tokenDao).findByRefreshToken("refresh-token-456");
        }

        @Test
        @DisplayName("Should delegate findByUserAndIsRevokedFalse to DAO")
        void findByUserAndIsRevokedFalse_ShouldDelegateToDao() {
            when(tokenDao.findByUserAndIsRevokedFalse(testUser)).thenReturn(Collections.singletonList(testToken));

            List<OAuthToken> result = tokenRepository.findByUserAndIsRevokedFalse(testUser);

            assertThat(result).hasSize(1);
            verify(tokenDao).findByUserAndIsRevokedFalse(testUser);
        }

        @Test
        @DisplayName("Should delegate findValidTokensByUser to DAO")
        void findValidTokensByUser_ShouldDelegateToDao() {
            when(tokenDao.findValidTokensByUser(testUser, now)).thenReturn(Collections.singletonList(testToken));

            List<OAuthToken> result = tokenRepository.findValidTokensByUser(testUser, now);

            assertThat(result).hasSize(1);
            verify(tokenDao).findValidTokensByUser(testUser, now);
        }

        @Test
        @DisplayName("Should delegate findAll to DAO")
        void findAll_ShouldDelegateToDao() {
            List<OAuthToken> tokens = Collections.singletonList(testToken);
            when(tokenDao.findAll()).thenReturn(tokens);

            List<OAuthToken> result = tokenRepository.findAll();

            assertThat(result).hasSize(1);
            verify(tokenDao).findAll();
        }

        @Test
        @DisplayName("Should delegate findAll with pagination to DAO")
        void findAllWithPagination_ShouldDelegateToDao() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<OAuthToken> page = new PageImpl<>(Collections.singletonList(testToken), pageable, 1);
            when(tokenDao.findAll(pageable)).thenReturn(page);

            Page<OAuthToken> result = tokenRepository.findAll(pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(tokenDao).findAll(pageable);
        }
    }

    @Nested
    @DisplayName("Exists Operations")
    class ExistsOperations {

        @Test
        @DisplayName("Should delegate existsById to DAO")
        void existsById_ShouldDelegateToDao() {
            when(tokenDao.existsById(1L)).thenReturn(true);

            boolean result = tokenRepository.existsById(1L);

            assertThat(result).isTrue();
            verify(tokenDao).existsById(1L);
        }
    }

    @Nested
    @DisplayName("Count Operations")
    class CountOperations {

        @Test
        @DisplayName("Should delegate count to DAO")
        void count_ShouldDelegateToDao() {
            when(tokenDao.count()).thenReturn(10L);

            long result = tokenRepository.count();

            assertThat(result).isEqualTo(10L);
            verify(tokenDao).count();
        }
    }

    @Nested
    @DisplayName("Revoke Operations")
    class RevokeOperations {

        @Test
        @DisplayName("Should delegate revokeAllTokensByUser to DAO")
        void revokeAllTokensByUser_ShouldDelegateToDao() {
            doNothing().when(tokenDao).revokeAllTokensByUser(testUser);

            tokenRepository.revokeAllTokensByUser(testUser);

            verify(tokenDao).revokeAllTokensByUser(testUser);
        }

        @Test
        @DisplayName("Should delegate revokeByAccessToken to DAO")
        void revokeByAccessToken_ShouldDelegateToDao() {
            doNothing().when(tokenDao).revokeByAccessToken("access-token-123");

            tokenRepository.revokeByAccessToken("access-token-123");

            verify(tokenDao).revokeByAccessToken("access-token-123");
        }
    }

    @Nested
    @DisplayName("Delete Operations")
    class DeleteOperations {

        @Test
        @DisplayName("Should delegate deleteById to DAO")
        void deleteById_ShouldDelegateToDao() {
            doNothing().when(tokenDao).deleteById(1L);

            tokenRepository.deleteById(1L);

            verify(tokenDao).deleteById(1L);
        }

        @Test
        @DisplayName("Should delegate delete to DAO")
        void delete_ShouldDelegateToDao() {
            doNothing().when(tokenDao).delete(testToken);

            tokenRepository.delete(testToken);

            verify(tokenDao).delete(testToken);
        }

        @Test
        @DisplayName("Should delegate deleteAll to DAO")
        void deleteAll_ShouldDelegateToDao() {
            doNothing().when(tokenDao).deleteAll();

            tokenRepository.deleteAll();

            verify(tokenDao).deleteAll();
        }

        @Test
        @DisplayName("Should delegate deleteExpiredOrRevokedTokens to DAO")
        void deleteExpiredOrRevokedTokens_ShouldDelegateToDao() {
            doNothing().when(tokenDao).deleteExpiredOrRevokedTokens(now);

            tokenRepository.deleteExpiredOrRevokedTokens(now);

            verify(tokenDao).deleteExpiredOrRevokedTokens(now);
        }
    }
}
