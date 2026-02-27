package com.arcana.cloud.document;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserDocument Unit Tests")
@Disabled("Empty test class - placeholder for future tests")
class UserDocumentTest {

    @Nested
    @DisplayName("toEntity() method")
    class ToEntityTests {

        @Test
        @DisplayName("Should convert document to entity with all fields")
        void toEntity_AllFields_ShouldConvertCorrectly() {
            LocalDateTime now = LocalDateTime.now();
            UserDocument document = UserDocument.builder()
                    .id("mongo-id-123")
                    .legacyId(1L)
                    .username("testuser")
                    .email("test@example.com")
                    .password("password123")
                    .firstName("Test")
                    .lastName("User")
                    .role(UserRole.ADMIN)
                    .isActive(true)
                    .isVerified(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            User entity = document.toEntity();

            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getUsername()).isEqualTo("testuser");
            assertThat(entity.getEmail()).isEqualTo("test@example.com");
            assertThat(entity.getPassword()).isEqualTo("password123");
            assertThat(entity.getFirstName()).isEqualTo("Test");
            assertThat(entity.getLastName()).isEqualTo("User");
            assertThat(entity.getRole()).isEqualTo(UserRole.ADMIN);
            assertThat(entity.getIsActive()).isTrue();
            assertThat(entity.getIsVerified()).isTrue();
            assertThat(entity.getCreatedAt()).isEqualTo(now);
            assertThat(entity.getUpdatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("Should use default values for null fields")
        void toEntity_NullFields_ShouldUseDefaults() {
            UserDocument document = UserDocument.builder()
                    .username("testuser")
                    .email("test@example.com")
                    .password("password123")
                    .build();

            User entity = document.toEntity();

            assertThat(entity.getRole()).isEqualTo(UserRole.USER);
            assertThat(entity.getIsActive()).isTrue();
            assertThat(entity.getIsVerified()).isFalse();
        }

        @Test
        @DisplayName("Should handle null legacyId")
        void toEntity_NullLegacyId_ShouldSetNullId() {
            UserDocument document = UserDocument.builder()
                    .username("testuser")
                    .email("test@example.com")
                    .build();

            User entity = document.toEntity();

            assertThat(entity.getId()).isNull();
        }
    }

    @Nested
    @DisplayName("fromEntity() method")
    class FromEntityTests {

        @Test
        @DisplayName("Should convert entity to document with all fields")
        void fromEntity_AllFields_ShouldConvertCorrectly() {
            LocalDateTime now = LocalDateTime.now();
            User entity = User.builder()
                    .id(1L)
                    .username("testuser")
                    .email("test@example.com")
                    .password("password123")
                    .firstName("Test")
                    .lastName("User")
                    .role(UserRole.MODERATOR)
                    .isActive(false)
                    .isVerified(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            UserDocument document = UserDocument.fromEntity(entity);

            assertThat(document.getLegacyId()).isEqualTo(1L);
            assertThat(document.getUsername()).isEqualTo("testuser");
            assertThat(document.getEmail()).isEqualTo("test@example.com");
            assertThat(document.getPassword()).isEqualTo("password123");
            assertThat(document.getFirstName()).isEqualTo("Test");
            assertThat(document.getLastName()).isEqualTo("User");
            assertThat(document.getRole()).isEqualTo(UserRole.MODERATOR);
            assertThat(document.getIsActive()).isFalse();
            assertThat(document.getIsVerified()).isTrue();
            assertThat(document.getCreatedAt()).isEqualTo(now);
            assertThat(document.getUpdatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("Should handle null entity ID")
        void fromEntity_NullId_ShouldSetNullLegacyId() {
            User entity = User.builder()
                    .username("testuser")
                    .email("test@example.com")
                    .build();

            UserDocument document = UserDocument.fromEntity(entity);

            assertThat(document.getLegacyId()).isNull();
            assertThat(document.getId()).isNull();
        }

        @Test
        @DisplayName("Should preserve null optional fields")
        void fromEntity_NullOptionalFields_ShouldPreserveNulls() {
            User entity = User.builder()
                    .username("testuser")
                    .email("test@example.com")
                    .build();

            UserDocument document = UserDocument.fromEntity(entity);

            assertThat(document.getFirstName()).isNull();
            assertThat(document.getLastName()).isNull();
        }
    }

    @Nested
    @DisplayName("Bidirectional conversion")
    class BidirectionalConversionTests {

        @Test
        @DisplayName("Entity -> Document -> Entity should preserve data")
        void entityToDocumentToEntity_ShouldPreserveData() {
            LocalDateTime now = LocalDateTime.now();
            User originalEntity = User.builder()
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

            UserDocument document = UserDocument.fromEntity(originalEntity);
            User convertedEntity = document.toEntity();

            assertThat(convertedEntity.getId()).isEqualTo(originalEntity.getId());
            assertThat(convertedEntity.getUsername()).isEqualTo(originalEntity.getUsername());
            assertThat(convertedEntity.getEmail()).isEqualTo(originalEntity.getEmail());
            assertThat(convertedEntity.getPassword()).isEqualTo(originalEntity.getPassword());
            assertThat(convertedEntity.getFirstName()).isEqualTo(originalEntity.getFirstName());
            assertThat(convertedEntity.getLastName()).isEqualTo(originalEntity.getLastName());
            assertThat(convertedEntity.getRole()).isEqualTo(originalEntity.getRole());
            assertThat(convertedEntity.getIsActive()).isEqualTo(originalEntity.getIsActive());
            assertThat(convertedEntity.getIsVerified()).isEqualTo(originalEntity.getIsVerified());
            assertThat(convertedEntity.getCreatedAt()).isEqualTo(originalEntity.getCreatedAt());
            assertThat(convertedEntity.getUpdatedAt()).isEqualTo(originalEntity.getUpdatedAt());
        }
    }
}
