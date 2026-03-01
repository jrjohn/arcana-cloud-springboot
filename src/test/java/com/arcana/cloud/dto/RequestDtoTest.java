package com.arcana.cloud.dto;

import com.arcana.cloud.dto.request.LoginRequest;
import com.arcana.cloud.dto.request.RefreshTokenRequest;
import com.arcana.cloud.dto.request.RegisterRequest;
import com.arcana.cloud.dto.request.UserCreateRequest;
import com.arcana.cloud.dto.request.UserUpdateRequest;
import com.arcana.cloud.dto.response.AuthResponse;
import com.arcana.cloud.dto.response.PagedResponse;
import com.arcana.cloud.dto.response.UserResponse;
import com.arcana.cloud.entity.UserRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestDtoTest {

    // ==================== LoginRequest ====================

    @Test
    void loginRequest_builder_getters() {
        LoginRequest req = LoginRequest.builder()
            .usernameOrEmail("john").password("secret").build();

        assertThat(req.getUsernameOrEmail()).isEqualTo("john");
        assertThat(req.getPassword()).isEqualTo("secret");
    }

    @Test
    void loginRequest_noArgsThenSetters() {
        LoginRequest req = new LoginRequest();
        req.setUsernameOrEmail("user@test.com");
        req.setPassword("pass");

        assertThat(req.getUsernameOrEmail()).isEqualTo("user@test.com");
        assertThat(req.getPassword()).isEqualTo("pass");
    }

    @Test
    void loginRequest_equalsHashCodeToString() {
        LoginRequest a = LoginRequest.builder().usernameOrEmail("u").password("p").build();
        LoginRequest b = LoginRequest.builder().usernameOrEmail("u").password("p").build();
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("u");
    }

    // ==================== RegisterRequest ====================

    @Test
    void registerRequest_builder_allFields() {
        RegisterRequest req = RegisterRequest.builder()
            .username("john").email("john@test.com").password("password1")
            .confirmPassword("password1").firstName("John").lastName("Doe").build();

        assertThat(req.getUsername()).isEqualTo("john");
        assertThat(req.getEmail()).isEqualTo("john@test.com");
        assertThat(req.getPassword()).isEqualTo("password1");
        assertThat(req.getConfirmPassword()).isEqualTo("password1");
        assertThat(req.getFirstName()).isEqualTo("John");
        assertThat(req.getLastName()).isEqualTo("Doe");
    }

    @Test
    void registerRequest_noArgs_setters() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("user");
        req.setEmail("user@e.com");
        req.setPassword("pass1234");
        req.setConfirmPassword("pass1234");

        assertThat(req.getUsername()).isEqualTo("user");
        assertThat(req.getEmail()).isEqualTo("user@e.com");
    }

    @Test
    void registerRequest_equalsHashCode() {
        RegisterRequest a = RegisterRequest.builder().username("u").email("e@e.com").password("p").confirmPassword("p").build();
        RegisterRequest b = RegisterRequest.builder().username("u").email("e@e.com").password("p").confirmPassword("p").build();
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    // ==================== RefreshTokenRequest ====================

    @Test
    void refreshTokenRequest_builder_getters() {
        RefreshTokenRequest req = RefreshTokenRequest.builder().refreshToken("rt-token").build();
        assertThat(req.getRefreshToken()).isEqualTo("rt-token");
    }

    @Test
    void refreshTokenRequest_noArgs_setter() {
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken("new-rt");
        assertThat(req.getRefreshToken()).isEqualTo("new-rt");
    }

    @Test
    void refreshTokenRequest_equalsHashCode() {
        RefreshTokenRequest a = RefreshTokenRequest.builder().refreshToken("rt").build();
        RefreshTokenRequest b = RefreshTokenRequest.builder().refreshToken("rt").build();
        assertThat(a).isEqualTo(b);
    }

    // ==================== UserCreateRequest ====================

    @Test
    void userCreateRequest_builder_allFields() {
        UserCreateRequest req = UserCreateRequest.builder()
            .username("newuser").email("new@test.com").password("password1")
            .firstName("New").lastName("User").build();

        assertThat(req.getUsername()).isEqualTo("newuser");
        assertThat(req.getEmail()).isEqualTo("new@test.com");
        assertThat(req.getPassword()).isEqualTo("password1");
        assertThat(req.getFirstName()).isEqualTo("New");
        assertThat(req.getLastName()).isEqualTo("User");
    }

    @Test
    void userCreateRequest_noArgs_setters() {
        UserCreateRequest req = new UserCreateRequest();
        req.setUsername("u");
        req.setEmail("e@e.com");
        req.setPassword("pass1234");

        assertThat(req.getUsername()).isEqualTo("u");
    }

    @Test
    void userCreateRequest_equalsHashCode() {
        UserCreateRequest a = UserCreateRequest.builder().username("u").email("e@e.com").password("p").build();
        UserCreateRequest b = UserCreateRequest.builder().username("u").email("e@e.com").password("p").build();
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("u");
    }

    // ==================== UserUpdateRequest ====================

    @Test
    void userUpdateRequest_builder_allFields() {
        UserUpdateRequest req = UserUpdateRequest.builder()
            .username("updated").email("updated@test.com").password("newpass1")
            .firstName("Updated").lastName("Name").isActive(true).isVerified(true).build();

        assertThat(req.getUsername()).isEqualTo("updated");
        assertThat(req.getEmail()).isEqualTo("updated@test.com");
        assertThat(req.getPassword()).isEqualTo("newpass1");
        assertThat(req.getFirstName()).isEqualTo("Updated");
        assertThat(req.getLastName()).isEqualTo("Name");
        assertThat(req.getIsActive()).isTrue();
        assertThat(req.getIsVerified()).isTrue();
    }

    @Test
    void userUpdateRequest_partialFields() {
        UserUpdateRequest req = UserUpdateRequest.builder()
            .username("newname").build();

        assertThat(req.getUsername()).isEqualTo("newname");
        assertThat(req.getEmail()).isNull();
        assertThat(req.getIsActive()).isNull();
    }

    @Test
    void userUpdateRequest_noArgs_setters() {
        UserUpdateRequest req = new UserUpdateRequest();
        req.setUsername("u");
        req.setIsActive(false);
        req.setIsVerified(false);

        assertThat(req.getUsername()).isEqualTo("u");
        assertThat(req.getIsActive()).isFalse();
    }

    @Test
    void userUpdateRequest_equalsHashCode() {
        UserUpdateRequest a = UserUpdateRequest.builder().username("u").build();
        UserUpdateRequest b = UserUpdateRequest.builder().username("u").build();
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    // ==================== AuthResponse ====================

    @Test
    void authResponse_builder_allFields() {
        UserResponse userResponse = UserResponse.builder().id(1L).username("john").build();
        AuthResponse response = AuthResponse.builder()
            .accessToken("at").refreshToken("rt").tokenType("Bearer")
            .expiresIn(3600L).user(userResponse).build();

        assertThat(response.getAccessToken()).isEqualTo("at");
        assertThat(response.getRefreshToken()).isEqualTo("rt");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getUsername()).isEqualTo("john");
    }

    @Test
    void authResponse_defaultTokenType() {
        AuthResponse response = AuthResponse.builder().accessToken("at").build();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
    }

    @Test
    void authResponse_noArgs_setters() {
        AuthResponse response = new AuthResponse();
        response.setAccessToken("at");
        response.setRefreshToken("rt");
        response.setExpiresIn(7200L);

        assertThat(response.getAccessToken()).isEqualTo("at");
        assertThat(response.getExpiresIn()).isEqualTo(7200L);
    }

    @Test
    void authResponse_equalsHashCode() {
        AuthResponse a = AuthResponse.builder().accessToken("at").refreshToken("rt").build();
        AuthResponse b = AuthResponse.builder().accessToken("at").refreshToken("rt").build();
        assertThat(a).isEqualTo(b);
    }

    // ==================== UserResponse ====================

    @Test
    void userResponse_builder_allFields() {
        LocalDateTime now = LocalDateTime.now();
        UserResponse response = UserResponse.builder()
            .id(1L).username("john").email("john@test.com")
            .firstName("John").lastName("Doe")
            .role(UserRole.USER).isActive(true).isVerified(false)
            .createdAt(now).updatedAt(now).build();

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("john");
        assertThat(response.getEmail()).isEqualTo("john@test.com");
        assertThat(response.getFirstName()).isEqualTo("John");
        assertThat(response.getLastName()).isEqualTo("Doe");
        assertThat(response.getRole()).isEqualTo(UserRole.USER);
        assertThat(response.getIsActive()).isTrue();
        assertThat(response.getIsVerified()).isFalse();
        assertThat(response.getCreatedAt()).isNotNull();
    }

    @Test
    void userResponse_adminRole() {
        UserResponse response = UserResponse.builder().role(UserRole.ADMIN).build();
        assertThat(response.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void userResponse_noArgs_setters() {
        UserResponse response = new UserResponse();
        response.setId(2L);
        response.setUsername("jane");
        response.setRole(UserRole.USER);

        assertThat(response.getId()).isEqualTo(2L);
        assertThat(response.getUsername()).isEqualTo("jane");
    }

    @Test
    void userResponse_equalsHashCode() {
        UserResponse a = UserResponse.builder().id(1L).username("user").build();
        UserResponse b = UserResponse.builder().id(1L).username("user").build();
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("user");
    }

    // ==================== PagedResponse ====================

    @Test
    void pagedResponse_builder_allFields() {
        PagedResponse<String> response = PagedResponse.<String>builder()
            .content(List.of("a", "b", "c"))
            .page(0).size(10).totalElements(3L).totalPages(1)
            .build();

        assertThat(response.getContent()).containsExactly("a", "b", "c");
        assertThat(response.getPage()).isEqualTo(0);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getTotalElements()).isEqualTo(3L);
        assertThat(response.getTotalPages()).isEqualTo(1);
    }

    @Test
    void pagedResponse_noArgs_setters() {
        PagedResponse<Integer> response = new PagedResponse<>();
        response.setContent(List.of(1, 2, 3));
        response.setPage(1);
        response.setSize(5);
        response.setTotalElements(10L);
        response.setTotalPages(2);

        assertThat(response.getContent()).hasSize(3);
        assertThat(response.getTotalElements()).isEqualTo(10L);
    }

    @Test
    void pagedResponse_equalsHashCode() {
        PagedResponse<String> a = PagedResponse.<String>builder()
            .content(List.of("x")).page(0).size(10).totalElements(1L).totalPages(1).build();
        PagedResponse<String> b = PagedResponse.<String>builder()
            .content(List.of("x")).page(0).size(10).totalElements(1L).totalPages(1).build();
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
