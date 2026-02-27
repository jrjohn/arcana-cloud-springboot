package com.arcana.cloud.document;

import com.arcana.cloud.entity.OAuthToken;
import com.arcana.cloud.entity.User;
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
 * MongoDB document representation of OAuthToken entity.
 */
@Document(collection = "oauth_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuthTokenDocument {

    @Id
    private String id;

    @Field("legacy_id")
    @Indexed(unique = true, sparse = true)
    private Long legacyId;

    @Field("user_id")
    @Indexed
    private String userId;

    @Field("user_legacy_id")
    @Indexed
    private Long userLegacyId;

    @Field("access_token")
    @Indexed
    private String accessToken;

    @Field("refresh_token")
    @Indexed
    private String refreshToken;

    @Field("token_type")
    private String tokenType;

    @Field("expires_at")
    @Indexed
    private LocalDateTime expiresAt;

    @Field("refresh_expires_at")
    private LocalDateTime refreshExpiresAt;

    @Field("is_revoked")
    @Indexed
    private Boolean isRevoked;

    @Field("created_at")
    private LocalDateTime createdAt;

    @Field("client_ip")
    private String clientIp;

    @Field("user_agent")
    private String userAgent;

    /**
     * Convert to JPA entity.
     * Note: User must be resolved separately.
     */
    public OAuthToken toEntity(User user) {
        return OAuthToken.builder()
                .id(legacyId)
                .user(user)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType(tokenType != null ? tokenType : "Bearer")
                .expiresAt(expiresAt)
                .refreshExpiresAt(refreshExpiresAt)
                .isRevoked(Boolean.TRUE.equals(isRevoked))
                .createdAt(createdAt)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .build();
    }

    /**
     * Create from JPA entity.
     */
    public static OAuthTokenDocument fromEntity(OAuthToken token) {
        return OAuthTokenDocument.builder()
                .legacyId(token.getId())
                .userLegacyId(token.getUser() != null ? token.getUser().getId() : null)
                .accessToken(token.getAccessToken())
                .refreshToken(token.getRefreshToken())
                .tokenType(token.getTokenType())
                .expiresAt(token.getExpiresAt())
                .refreshExpiresAt(token.getRefreshExpiresAt())
                .isRevoked(token.getIsRevoked())
                .createdAt(token.getCreatedAt())
                .clientIp(token.getClientIp())
                .userAgent(token.getUserAgent())
                .build();
    }
}
