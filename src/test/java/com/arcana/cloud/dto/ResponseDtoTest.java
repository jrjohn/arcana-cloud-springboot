package com.arcana.cloud.dto;

import com.arcana.cloud.dto.response.AuthResponse;
import com.arcana.cloud.dto.response.PagedResponse;
import com.arcana.cloud.dto.response.UserResponse;
import com.arcana.cloud.entity.UserRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for response DTOs (AuthResponse, PagedResponse, UserResponse).
 * Ensures JaCoCo coverage for all Lombok-generated and custom methods.
 */
class ResponseDtoTest {

    // ===================== AuthResponse =====================

    @Test
    void authResponse_builder_allFields() {
        UserResponse user = UserResponse.builder()
            .id(1L).username("testuser").email("test@example.com")
            .role(UserRole.USER).isActive(true).isVerified(false).build();

        AuthResponse response = AuthResponse.builder()
            .accessToken("access-token-123")
            .refreshToken("refresh-token-456")
            .tokenType("Bearer")
            .expiresIn(3600L)
            .user(user)
            .build();

        assertThat(response.getAccessToken()).isEqualTo("access-token-123");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-456");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getUsername()).isEqualTo("testuser");
    }

    @Test
    void authResponse_defaultTokenType() {
        AuthResponse response = new AuthResponse();
        response.setAccessToken("access");
        response.setRefreshToken("refresh");
        response.setExpiresIn(1800L);

        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        assertThat(response.getExpiresIn()).isEqualTo(1800L);
    }

    @Test
    void authResponse_noArgsThenSetters() {
        AuthResponse response = new AuthResponse();
        response.setAccessToken("at");
        response.setRefreshToken("rt");
        response.setTokenType("Bearer");
        response.setExpiresIn(7200L);

        UserResponse user = new UserResponse();
        user.setId(5L);
        user.setUsername("user5");
        response.setUser(user);

        assertThat(response.getAccessToken()).isEqualTo("at");
        assertThat(response.getRefreshToken()).isEqualTo("rt");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(7200L);
        assertThat(response.getUser().getId()).isEqualTo(5L);
    }

    @Test
    void authResponse_equalsHashCode() {
        AuthResponse a = AuthResponse.builder().accessToken("x").refreshToken("y").build();
        AuthResponse b = AuthResponse.builder().accessToken("x").refreshToken("y").build();

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void authResponse_toString() {
        AuthResponse response = AuthResponse.builder().accessToken("tok").build();
        assertThat(response.toString()).contains("tok");
    }

    @Test
    void authResponse_allArgsConstructor() {
        UserResponse user = UserResponse.builder().id(2L).username("u2").build();
        AuthResponse response = new AuthResponse("acc", "ref", "Bearer", 3600L, user);

        assertThat(response.getAccessToken()).isEqualTo("acc");
        assertThat(response.getRefreshToken()).isEqualTo("ref");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
        assertThat(response.getUser().getUsername()).isEqualTo("u2");
    }

    // ===================== PagedResponse =====================

    @Test
    void pagedResponse_builder_allFields() {
        List<String> content = List.of("item1", "item2", "item3");

        PagedResponse<String> response = PagedResponse.<String>builder()
            .content(content)
            .page(0)
            .size(10)
            .totalElements(3L)
            .totalPages(1)
            .build();

        assertThat(response.getContent()).hasSize(3).contains("item1", "item2", "item3");
        assertThat(response.getPage()).isEqualTo(0);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getTotalElements()).isEqualTo(3L);
        assertThat(response.getTotalPages()).isEqualTo(1);
    }

    @Test
    void pagedResponse_noArgsThenSetters() {
        PagedResponse<Integer> response = new PagedResponse<>();
        response.setContent(List.of(1, 2, 3));
        response.setPage(2);
        response.setSize(5);
        response.setTotalElements(20L);
        response.setTotalPages(4);

        assertThat(response.getContent()).containsExactly(1, 2, 3);
        assertThat(response.getPage()).isEqualTo(2);
        assertThat(response.getSize()).isEqualTo(5);
        assertThat(response.getTotalElements()).isEqualTo(20L);
        assertThat(response.getTotalPages()).isEqualTo(4);
    }

    @Test
    void pagedResponse_emptyContent() {
        PagedResponse<String> response = PagedResponse.<String>builder()
            .content(List.of())
            .page(0)
            .size(20)
            .totalElements(0L)
            .totalPages(0)
            .build();

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isEqualTo(0L);
    }

    @Test
    void pagedResponse_equalsHashCode() {
        PagedResponse<String> a = PagedResponse.<String>builder()
            .content(List.of("a")).page(0).size(10).totalElements(1L).totalPages(1).build();
        PagedResponse<String> b = PagedResponse.<String>builder()
            .content(List.of("a")).page(0).size(10).totalElements(1L).totalPages(1).build();

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void pagedResponse_toString() {
        PagedResponse<String> response = PagedResponse.<String>builder()
            .content(List.of("hello")).page(1).size(10).totalElements(15L).totalPages(2).build();

        assertThat(response.toString()).contains("hello");
    }

    @Test
    void pagedResponse_allArgsConstructor() {
        List<String> content = List.of("x", "y");
        PagedResponse<String> response = new PagedResponse<>(content, 1, 5, 12L, 3);

        assertThat(response.getContent()).containsExactly("x", "y");
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(5);
        assertThat(response.getTotalElements()).isEqualTo(12L);
        assertThat(response.getTotalPages()).isEqualTo(3);
    }

    // ===================== UserResponse =====================

    @Test
    void userResponse_builder_allFields() {
        LocalDateTime now = LocalDateTime.now();

        UserResponse response = UserResponse.builder()
            .id(10L)
            .username("johnDoe")
            .email("john@example.com")
            .firstName("John")
            .lastName("Doe")
            .role(UserRole.ADMIN)
            .isActive(true)
            .isVerified(true)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getUsername()).isEqualTo("johnDoe");
        assertThat(response.getEmail()).isEqualTo("john@example.com");
        assertThat(response.getFirstName()).isEqualTo("John");
        assertThat(response.getLastName()).isEqualTo("Doe");
        assertThat(response.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(response.getIsActive()).isTrue();
        assertThat(response.getIsVerified()).isTrue();
        assertThat(response.getCreatedAt()).isEqualTo(now);
        assertThat(response.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void userResponse_noArgsThenSetters() {
        LocalDateTime now = LocalDateTime.now();
        UserResponse response = new UserResponse();
        response.setId(3L);
        response.setUsername("jane");
        response.setEmail("jane@example.com");
        response.setFirstName("Jane");
        response.setLastName("Smith");
        response.setRole(UserRole.USER);
        response.setIsActive(false);
        response.setIsVerified(true);
        response.setCreatedAt(now);
        response.setUpdatedAt(now);

        assertThat(response.getId()).isEqualTo(3L);
        assertThat(response.getUsername()).isEqualTo("jane");
        assertThat(response.getEmail()).isEqualTo("jane@example.com");
        assertThat(response.getFirstName()).isEqualTo("Jane");
        assertThat(response.getLastName()).isEqualTo("Smith");
        assertThat(response.getRole()).isEqualTo(UserRole.USER);
        assertThat(response.getIsActive()).isFalse();
        assertThat(response.getIsVerified()).isTrue();
    }

    @Test
    void userResponse_equalsHashCode() {
        UserResponse a = UserResponse.builder().id(1L).username("u").email("u@e.com").build();
        UserResponse b = UserResponse.builder().id(1L).username("u").email("u@e.com").build();

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void userResponse_toString() {
        UserResponse response = UserResponse.builder().id(1L).username("testuser").build();
        assertThat(response.toString()).contains("testuser");
    }

    @Test
    void userResponse_allArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        UserResponse response = new UserResponse(
            7L, "user7", "user7@example.com",
            "Seven", "User", UserRole.USER,
            true, false, now, now
        );

        assertThat(response.getId()).isEqualTo(7L);
        assertThat(response.getUsername()).isEqualTo("user7");
        assertThat(response.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void userResponse_nullRole() {
        UserResponse response = UserResponse.builder()
            .id(1L)
            .username("noRoleUser")
            .email("norole@example.com")
            .build();

        assertThat(response.getRole()).isNull();
    }

    @Test
    void userResponse_inactiveUnverifiedUser() {
        UserResponse response = UserResponse.builder()
            .id(99L)
            .username("blocked")
            .email("blocked@example.com")
            .isActive(false)
            .isVerified(false)
            .role(UserRole.USER)
            .build();

        assertThat(response.getIsActive()).isFalse();
        assertThat(response.getIsVerified()).isFalse();
    }
}
