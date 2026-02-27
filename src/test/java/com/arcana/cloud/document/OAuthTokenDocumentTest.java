package com.arcana.cloud.document;

import com.arcana.cloud.entity.OAuthToken;
import com.arcana.cloud.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuthTokenDocument Unit Tests")
class OAuthTokenDocumentTest {

    @Nested
    @DisplayName("toEntity() method")
    class ToEntityTests {

        @Test
        @DisplayName("Should convert document to entity with all fields")
        void toEntity_AllFields_ShouldConvertCorrectly() {
            LocalDateTime now = LocalDateTime.now();
            User user = User.builder()
                    .id(1L)
                    .username("testuser")
                    .email("test@example.com")
                    .build();

            OAuthTokenDocument document = OAuthTokenDocument.builder()
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

            OAuthToken entity = document.toEntity(user);

            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getUser()).isEqualTo(user);
            assertThat(entity.getAccessToken()).isEqualTo("access-token-123");
            assertThat(entity.getRefreshToken()).isEqualTo("refresh-token-456");
            assertThat(entity.getTokenType()).isEqualTo("Bearer");
            assertThat(entity.getExpiresAt()).isEqualTo(now.plusHours(1));
            assertThat(entity.getRefreshExpiresAt()).isEqualTo(now.plusDays(30));
            assertThat(entity.getIsRevoked()).isFalse();
            assertThat(entity.getCreatedAt()).isEqualTo(now);
            assertThat(entity.getClientIp()).isEqualTo("127.0.0.1");
            assertThat(entity.getUserAgent()).isEqualTo("TestAgent");
        }

        @Test
        @DisplayName("Should use default values for null fields")
        void toEntity_NullFields_ShouldUseDefaults() {
            OAuthTokenDocument document = OAuthTokenDocument.builder()
                    .accessToken("access-token-123")
                    .refreshToken("refresh-token-456")
                    .build();

            OAuthToken entity = document.toEntity(null);

            assertThat(entity.getTokenType()).isEqualTo("Bearer");
            assertThat(entity.getIsRevoked()).isFalse();
            assertThat(entity.getUser()).isNull();
        }

        @Test
        @DisplayName("Should handle null legacyId")
        void toEntity_NullLegacyId_ShouldSetNullId() {
            OAuthTokenDocument document = OAuthTokenDocument.builder()
                    .accessToken("access-token-123")
                    .build();

            OAuthToken entity = document.toEntity(null);

            assertThat(entity.getId()).isNull();
        }

        @Test
        @DisplayName("Should handle revoked token")
        void toEntity_RevokedToken_ShouldSetIsRevokedTrue() {
            OAuthTokenDocument document = OAuthTokenDocument.builder()
                    .accessToken("access-token-123")
                    .isRevoked(true)
                    .build();

            OAuthToken entity = document.toEntity(null);

            assertThat(entity.getIsRevoked()).isTrue();
        }
    }

    @Nested
    @DisplayName("fromEntity() method")
    class FromEntityTests {

        @Test
        @DisplayName("Should convert entity to document with all fields")
        void fromEntity_AllFields_ShouldConvertCorrectly() {
            LocalDateTime now = LocalDateTime.now();
            User user = User.builder()
                    .id(1L)
                    .username("testuser")
                    .build();

            OAuthToken entity = OAuthToken.builder()
                    .id(1L)
                    .user(user)
                    .accessToken("access-token-123")
                    .refreshToken("refresh-token-456")
                    .tokenType("Bearer")
                    .expiresAt(now.plusHours(1))
                    .refreshExpiresAt(now.plusDays(30))
                    .isRevoked(true)
                    .createdAt(now)
                    .clientIp("192.168.1.1")
                    .userAgent("Mozilla/5.0")
                    .build();

            OAuthTokenDocument document = OAuthTokenDocument.fromEntity(entity);

            assertThat(document.getLegacyId()).isEqualTo(1L);
            assertThat(document.getUserLegacyId()).isEqualTo(1L);
            assertThat(document.getAccessToken()).isEqualTo("access-token-123");
            assertThat(document.getRefreshToken()).isEqualTo("refresh-token-456");
            assertThat(document.getTokenType()).isEqualTo("Bearer");
            assertThat(document.getExpiresAt()).isEqualTo(now.plusHours(1));
            assertThat(document.getRefreshExpiresAt()).isEqualTo(now.plusDays(30));
            assertThat(document.getIsRevoked()).isTrue();
            assertThat(document.getCreatedAt()).isEqualTo(now);
            assertThat(document.getClientIp()).isEqualTo("192.168.1.1");
            assertThat(document.getUserAgent()).isEqualTo("Mozilla/5.0");
        }

        @Test
        @DisplayName("Should handle null user")
        void fromEntity_NullUser_ShouldSetNullUserLegacyId() {
            OAuthToken entity = OAuthToken.builder()
                    .id(1L)
                    .user(null)
                    .accessToken("access-token-123")
                    .build();

            OAuthTokenDocument document = OAuthTokenDocument.fromEntity(entity);

            assertThat(document.getUserLegacyId()).isNull();
        }

        @Test
        @DisplayName("Should handle null entity ID")
        void fromEntity_NullId_ShouldSetNullLegacyId() {
            User user = User.builder().id(1L).build();
            OAuthToken entity = OAuthToken.builder()
                    .user(user)
                    .accessToken("access-token-123")
                    .build();

            OAuthTokenDocument document = OAuthTokenDocument.fromEntity(entity);

            assertThat(document.getLegacyId()).isNull();
            assertThat(document.getId()).isNull();
        }
    }

    @Nested
    @DisplayName("Bidirectional conversion")
    class BidirectionalConversionTests {

        @Test
        @DisplayName("Entity -> Document -> Entity should preserve data")
        void entityToDocumentToEntity_ShouldPreserveData() {
            LocalDateTime now = LocalDateTime.now();
            User user = User.builder()
                    .id(1L)
                    .username("testuser")
                    .email("test@example.com")
                    .build();

            OAuthToken originalEntity = OAuthToken.builder()
                    .id(1L)
                    .user(user)
                    .accessToken("access-token-123")
                    .refreshToken("refresh-token-456")
                    .tokenType("Bearer")
                    .expiresAt(now.plusHours(1))
                    .refreshExpiresAt(now.plusDays(30))
                    .isRevoked(false)
                    .createdAt(now)
                    .clientIp("10.0.0.1")
                    .userAgent("Chrome/120")
                    .build();

            OAuthTokenDocument document = OAuthTokenDocument.fromEntity(originalEntity);
            OAuthToken convertedEntity = document.toEntity(user);

            assertThat(convertedEntity.getId()).isEqualTo(originalEntity.getId());
            assertThat(convertedEntity.getUser()).isEqualTo(originalEntity.getUser());
            assertThat(convertedEntity.getAccessToken()).isEqualTo(originalEntity.getAccessToken());
            assertThat(convertedEntity.getRefreshToken()).isEqualTo(originalEntity.getRefreshToken());
            assertThat(convertedEntity.getTokenType()).isEqualTo(originalEntity.getTokenType());
            assertThat(convertedEntity.getExpiresAt()).isEqualTo(originalEntity.getExpiresAt());
            assertThat(convertedEntity.getRefreshExpiresAt()).isEqualTo(originalEntity.getRefreshExpiresAt());
            assertThat(convertedEntity.getIsRevoked()).isEqualTo(originalEntity.getIsRevoked());
            assertThat(convertedEntity.getCreatedAt()).isEqualTo(originalEntity.getCreatedAt());
            assertThat(convertedEntity.getClientIp()).isEqualTo(originalEntity.getClientIp());
            assertThat(convertedEntity.getUserAgent()).isEqualTo(originalEntity.getUserAgent());
        }

        @Test
        @DisplayName("Should preserve data with different user reference")
        void entityToDocumentToEntity_WithDifferentUser_ShouldUseNewUser() {
            User originalUser = User.builder().id(1L).username("original").build();
            User newUser = User.builder().id(1L).username("new").build();

            OAuthToken originalEntity = OAuthToken.builder()
                    .id(1L)
                    .user(originalUser)
                    .accessToken("access-token")
                    .build();

            OAuthTokenDocument document = OAuthTokenDocument.fromEntity(originalEntity);
            OAuthToken convertedEntity = document.toEntity(newUser);

            assertThat(convertedEntity.getUser()).isEqualTo(newUser);
            assertThat(convertedEntity.getUser().getUsername()).isEqualTo("new");
        }
    }
}
