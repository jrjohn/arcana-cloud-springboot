package com.arcana.cloud.document;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * MongoDB document representation of User entity.
 */
@Document(collection = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDocument {

    @Id
    private String id;

    @Field("legacy_id")
    @Indexed(unique = true, sparse = true)
    private Long legacyId;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    private String password;

    @Field("first_name")
    private String firstName;

    @Field("last_name")
    private String lastName;

    @Indexed
    private UserRole role;

    @Field("is_active")
    @Indexed
    private Boolean isActive;

    @Field("is_verified")
    private Boolean isVerified;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("updated_at")
    private LocalDateTime updatedAt;

    /**
     * Convert to JPA entity for service layer compatibility.
     */
    public User toEntity() {
        return User.builder()
                .id(legacyId)
                .username(username)
                .email(email)
                .password(password)
                .firstName(firstName)
                .lastName(lastName)
                .role(role != null ? role : UserRole.USER)
                .isActive(isActive != null ? isActive : true)
                .isVerified(isVerified != null ? isVerified : false)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    /**
     * Create from JPA entity.
     */
    public static UserDocument fromEntity(User user) {
        return UserDocument.builder()
                .legacyId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .password(user.getPassword())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .isVerified(user.getIsVerified())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
