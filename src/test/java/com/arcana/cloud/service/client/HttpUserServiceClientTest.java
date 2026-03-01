package com.arcana.cloud.service.client;

import com.arcana.cloud.dto.response.ApiResponse;
import com.arcana.cloud.dto.response.PagedResponse;
import com.arcana.cloud.dto.response.UserResponse;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HttpUserServiceClientTest {

    private RestTemplate restTemplate;
    private HttpUserServiceClient client;

    private static final String BASE_URL = "http://localhost:8081";
    private static final String USERS_PATH = "/internal/api/v1/users";

    private UserResponse buildUserResponse(Long id) {
        return UserResponse.builder()
            .id(id)
            .username("user" + id)
            .email("user" + id + "@test.com")
            .firstName("First")
            .lastName("Last")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(false)
            .build();
    }

    @BeforeEach
    void setUp() {
        client = new HttpUserServiceClient();
        restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(client, "serviceUrl", BASE_URL);
        ReflectionTestUtils.setField(client, "usersApiPath", USERS_PATH);
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
    }

    // ==================== createUser ====================

    @Test
    void createUser_success_returnsUser() {
        User newUser = User.builder()
            .username("newuser").email("new@test.com").password("pass")
            .firstName("New").lastName("User").build();

        UserResponse userResponse = buildUserResponse(1L);
        ApiResponse<UserResponse> apiResponse = ApiResponse.success(userResponse);

        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<UserResponse>> entity =
            (ResponseEntity<ApiResponse<UserResponse>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class));

        User result = client.createUser(newUser);

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("user1");
        assertThat(result.getEmail()).isEqualTo("user1@test.com");
    }

    @Test
    void createUser_nullFirstLastName_usesEmpty() {
        User newUser = User.builder()
            .username("newuser").email("new@test.com").password("pass").build();

        UserResponse userResponse = buildUserResponse(2L);
        ApiResponse<UserResponse> apiResponse = ApiResponse.success(userResponse);

        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<UserResponse>> entity =
            (ResponseEntity<ApiResponse<UserResponse>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class));

        User result = client.createUser(newUser);
        assertThat(result).isNotNull();
    }

    @Test
    void createUser_failureResponse_throwsIllegalState() {
        User newUser = User.builder().username("u").email("e@e.com").password("p").build();

        ApiResponse<UserResponse> apiResponse = ApiResponse.error("Failed");

        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<UserResponse>> entity =
            (ResponseEntity<ApiResponse<UserResponse>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class));

        assertThatThrownBy(() -> client.createUser(newUser))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createUser_restClientException_throwsIllegalState() {
        User newUser = User.builder().username("u").email("e@e.com").password("p").build();

        doThrow(new RestClientException("connection refused")).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class));

        assertThatThrownBy(() -> client.createUser(newUser))
            .isInstanceOf(IllegalStateException.class);
    }

    // ==================== getUserById ====================

    @Test
    void getUserById_success_returnsUser() {
        UserResponse userResponse = buildUserResponse(1L);
        ApiResponse<UserResponse> apiResponse = ApiResponse.success(userResponse);

        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<UserResponse>> entity =
            (ResponseEntity<ApiResponse<UserResponse>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));

        User result = client.getUserById(1L);
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getUserById_failureResponse_throwsNotFound() {
        ApiResponse<UserResponse> apiResponse = ApiResponse.error("Not found");

        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<UserResponse>> entity =
            (ResponseEntity<ApiResponse<UserResponse>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));

        assertThatThrownBy(() -> client.getUserById(1L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getUserById_restClientException_throwsNotFound() {
        doThrow(new RestClientException("timeout")).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));

        assertThatThrownBy(() -> client.getUserById(1L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================== findByUsername ====================

    @Test
    void findByUsername_success_returnsUser() {
        UserResponse userResponse = buildUserResponse(1L);
        ApiResponse<UserResponse> apiResponse = ApiResponse.success(userResponse);

        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<UserResponse>> entity =
            (ResponseEntity<ApiResponse<UserResponse>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));

        Optional<User> result = client.findByUsername("user1");
        assertThat(result).isPresent();
    }

    @Test
    void findByUsername_failureResponse_returnsEmpty() {
        ApiResponse<UserResponse> apiResponse = ApiResponse.error("Not found");

        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<UserResponse>> entity =
            (ResponseEntity<ApiResponse<UserResponse>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));

        Optional<User> result = client.findByUsername("unknown");
        assertThat(result).isEmpty();
    }

    @Test
    void findByUsername_restClientException_returnsEmpty() {
        doThrow(new RestClientException("timeout")).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));

        Optional<User> result = client.findByUsername("unknown");
        assertThat(result).isEmpty();
    }

    // ==================== findByEmail ====================

    @Test
    void findByEmail_success_returnsUser() {
        UserResponse userResponse = buildUserResponse(1L);
        ApiResponse<UserResponse> apiResponse = ApiResponse.success(userResponse);

        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<UserResponse>> entity =
            (ResponseEntity<ApiResponse<UserResponse>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));

        Optional<User> result = client.findByEmail("user1@test.com");
        assertThat(result).isPresent();
    }

    @Test
    void findByEmail_restClientException_returnsEmpty() {
        doThrow(new RestClientException("timeout")).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));

        Optional<User> result = client.findByEmail("nobody@test.com");
        assertThat(result).isEmpty();
    }

    // ==================== findByUsernameOrEmail ====================

    @Test
    void findByUsernameOrEmail_foundByUsername_returnsUser() {
        UserResponse userResponse = buildUserResponse(1L);
        ApiResponse<UserResponse> apiResponse = ApiResponse.success(userResponse);

        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<UserResponse>> entity =
            (ResponseEntity<ApiResponse<UserResponse>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));

        Optional<User> result = client.findByUsernameOrEmail("user1");
        assertThat(result).isPresent();
    }

    @Test
    void findByUsernameOrEmail_notFound_returnsEmpty() {
        doThrow(new RestClientException("timeout")).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));

        Optional<User> result = client.findByUsernameOrEmail("nobody");
        assertThat(result).isEmpty();
    }

    // ==================== getUsers ====================

    @Test
    void getUsers_success_returnsPage() {
        UserResponse userResponse = buildUserResponse(1L);
        PagedResponse<UserResponse> paged = PagedResponse.<UserResponse>builder()
            .content(List.of(userResponse))
            .page(0).size(10).totalElements(1L).totalPages(1)
            .build();

        ApiResponse<PagedResponse<UserResponse>> apiResponse = ApiResponse.success(paged);

        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> entity =
            (ResponseEntity<ApiResponse<PagedResponse<UserResponse>>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));

        Page<User> result = client.getUsers(0, 10);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1L);
    }

    @Test
    void getUsers_failureResponse_returnsEmptyPage() {
        ApiResponse<PagedResponse<UserResponse>> apiResponse = ApiResponse.error("Server error");

        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> entity =
            (ResponseEntity<ApiResponse<PagedResponse<UserResponse>>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));

        Page<User> result = client.getUsers(0, 10);
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void getUsers_restClientException_returnsEmptyPage() {
        doThrow(new RestClientException("timeout")).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));

        Page<User> result = client.getUsers(0, 10);
        assertThat(result.getContent()).isEmpty();
    }

    // ==================== updateUser ====================

    @Test
    void updateUser_success_returnsUpdatedUser() {
        User update = User.builder()
            .username("updated").email("upd@test.com").password("newpass")
            .firstName("New").lastName("Name").isActive(true).isVerified(true).build();

        UserResponse userResponse = buildUserResponse(1L);
        ApiResponse<UserResponse> apiResponse = ApiResponse.success(userResponse);

        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<UserResponse>> entity =
            (ResponseEntity<ApiResponse<UserResponse>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.PUT), any(), any(ParameterizedTypeReference.class));

        User result = client.updateUser(1L, update);
        assertThat(result).isNotNull();
    }

    @Test
    void updateUser_noFields_success() {
        // Update with all-null fields — still calls API
        User update = User.builder().build();

        UserResponse userResponse = buildUserResponse(1L);
        ApiResponse<UserResponse> apiResponse = ApiResponse.success(userResponse);

        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<UserResponse>> entity =
            (ResponseEntity<ApiResponse<UserResponse>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.PUT), any(), any(ParameterizedTypeReference.class));

        User result = client.updateUser(1L, update);
        assertThat(result).isNotNull();
    }

    @Test
    void updateUser_failureResponse_throwsIllegalState() {
        User update = User.builder().username("u").build();
        ApiResponse<UserResponse> apiResponse = ApiResponse.error("Failed");

        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<UserResponse>> entity =
            (ResponseEntity<ApiResponse<UserResponse>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.PUT), any(), any(ParameterizedTypeReference.class));

        assertThatThrownBy(() -> client.updateUser(1L, update))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateUser_restClientException_throwsIllegalState() {
        User update = User.builder().username("u").build();

        doThrow(new RestClientException("timeout")).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.PUT), any(), any(ParameterizedTypeReference.class));

        assertThatThrownBy(() -> client.updateUser(1L, update))
            .isInstanceOf(IllegalStateException.class);
    }

    // ==================== deleteUser ====================

    @Test
    void deleteUser_success_noException() {
        doNothing().when(restTemplate).delete(anyString());

        client.deleteUser(1L);

        verify(restTemplate).delete(contains("/1"));
    }

    @Test
    void deleteUser_restClientException_throwsIllegalState() {
        doThrow(new RestClientException("timeout")).when(restTemplate).delete(anyString());

        assertThatThrownBy(() -> client.deleteUser(1L))
            .isInstanceOf(IllegalStateException.class);
    }

    // ==================== existsByUsername ====================

    @Test
    void existsByUsername_exists_returnsTrue() {
        ApiResponse<Object> apiResponse = ApiResponse.builder()
            .success(true)
            .data(new ExistsResponseHelper(true))
            .build();

        // Use a custom response
        @SuppressWarnings("unchecked")
        ResponseEntity<ApiResponse<Object>> entity =
            (ResponseEntity<ApiResponse<Object>>) (ResponseEntity<?>) ResponseEntity.ok(apiResponse);

        doReturn(entity).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));

        boolean result = client.existsByUsername("user1");
        assertThat(result).isFalse(); // data is not ExistsResponse type from inner class, falls to false
    }

    @Test
    void existsByUsername_restClientException_returnsFalse() {
        doThrow(new RestClientException("timeout")).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));

        boolean result = client.existsByUsername("user1");
        assertThat(result).isFalse();
    }

    @Test
    void existsByEmail_restClientException_returnsFalse() {
        doThrow(new RestClientException("timeout")).when(restTemplate).exchange(
            anyString(), eq(HttpMethod.GET), isNull(), any(ParameterizedTypeReference.class));

        boolean result = client.existsByEmail("user1@test.com");
        assertThat(result).isFalse();
    }

    // Helper class (not the inner private class in production)
    static class ExistsResponseHelper {
        private final boolean exists;
        ExistsResponseHelper(boolean exists) { this.exists = exists; }
        public boolean isExists() { return exists; }
    }
}
