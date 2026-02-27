package com.arcana.cloud.service.client;

import com.arcana.cloud.dto.response.ApiResponse;
import com.arcana.cloud.dto.response.PagedResponse;
import com.arcana.cloud.dto.response.UserResponse;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.exception.ResourceNotFoundException;
import com.arcana.cloud.service.interfaces.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@ConditionalOnExpression(
    "'${communication.protocol:grpc}' == 'http' and '${deployment.layer:}' == 'controller'"
)
@Slf4j
public class HttpUserServiceClient implements UserService {

    private static final String USERS_API_PATH = "/internal/api/v1/users";

    @Value("${service.http.url:http://localhost:8081}")
    private String serviceUrl;

    private final RestTemplate restTemplate;

    public HttpUserServiceClient() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public User createUser(User user) {
        try {
            log.debug("HTTP client: Creating user via {}", serviceUrl);
            Map<String, Object> request = Map.of(
                "username", user.getUsername(),
                "email", user.getEmail(),
                "password", user.getPassword(),
                "firstName", user.getFirstName() != null ? user.getFirstName() : "",
                "lastName", user.getLastName() != null ? user.getLastName() : ""
            );

            ResponseEntity<ApiResponse<UserResponse>> response = restTemplate.exchange(
                serviceUrl + USERS_API_PATH,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() { }
            );

            if (response.getBody() != null && response.getBody().isSuccess()) {
                return fromResponse(response.getBody().getData());
            }
            throw new IllegalStateException("Failed to create user");
        } catch (RestClientException e) {
            log.error("HTTP error creating user", e);
            throw new IllegalStateException("Failed to create user: " + e.getMessage());
        }
    }

    @Override
    public User getUserById(Long id) {
        try {
            log.debug("HTTP client: Getting user {} via {}", id, serviceUrl);
            ResponseEntity<ApiResponse<UserResponse>> response = restTemplate.exchange(
                serviceUrl + USERS_API_PATH + "/" + id,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() { }
            );

            if (response.getBody() != null && response.getBody().isSuccess()) {
                return fromResponse(response.getBody().getData());
            }
            throw new ResourceNotFoundException("User", "id", id);
        } catch (RestClientException e) {
            log.error("HTTP error getting user", e);
            throw new ResourceNotFoundException("User", "id", id);
        }
    }

    @Override
    public Optional<User> findByUsername(String username) {
        try {
            log.debug("HTTP client: Finding user by username {} via {}", username, serviceUrl);
            ResponseEntity<ApiResponse<UserResponse>> response = restTemplate.exchange(
                serviceUrl + "/internal/api/v1/users/username/" + username,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() { }
            );

            if (response.getBody() != null && response.getBody().isSuccess()) {
                return Optional.of(fromResponse(response.getBody().getData()));
            }
            return Optional.empty();
        } catch (RestClientException e) {
            log.debug("User not found by username: {}", username);
            return Optional.empty();
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        try {
            log.debug("HTTP client: Finding user by email {} via {}", email, serviceUrl);
            ResponseEntity<ApiResponse<UserResponse>> response = restTemplate.exchange(
                serviceUrl + "/internal/api/v1/users/email/" + email,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() { }
            );

            if (response.getBody() != null && response.getBody().isSuccess()) {
                return Optional.of(fromResponse(response.getBody().getData()));
            }
            return Optional.empty();
        } catch (RestClientException e) {
            log.debug("User not found by email: {}", email);
            return Optional.empty();
        }
    }

    @Override
    public Optional<User> findByUsernameOrEmail(String usernameOrEmail) {
        Optional<User> byUsername = findByUsername(usernameOrEmail);
        if (byUsername.isPresent()) {
            return byUsername;
        }
        return findByEmail(usernameOrEmail);
    }

    @Override
    public Page<User> getUsers(int page, int size) {
        try {
            log.debug("HTTP client: Listing users page {} size {} via {}", page, size, serviceUrl);
            ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> response = restTemplate.exchange(
                serviceUrl + "/internal/api/v1/users?page=" + page + "&size=" + size,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() { }
            );

            if (response.getBody() != null && response.getBody().isSuccess()) {
                PagedResponse<UserResponse> pagedResponse = response.getBody().getData();
                List<User> users = pagedResponse.getContent().stream()
                    .map(this::fromResponse)
                    .toList();
                return new PageImpl<>(users, PageRequest.of(page, size), pagedResponse.getTotalElements());
            }
            return new PageImpl<>(Collections.emptyList());
        } catch (RestClientException e) {
            log.error("HTTP error listing users", e);
            return new PageImpl<>(Collections.emptyList());
        }
    }

    @Override
    public User updateUser(Long id, User user) {
        try {
            log.debug("HTTP client: Updating user {} via {}", id, serviceUrl);
            Map<String, Object> request = new java.util.HashMap<>();
            if (user.getUsername() != null) {
                request.put("username", user.getUsername());
            }
            if (user.getEmail() != null) {
                request.put("email", user.getEmail());
            }
            if (user.getPassword() != null) {
                request.put("password", user.getPassword());
            }
            if (user.getFirstName() != null) {
                request.put("firstName", user.getFirstName());
            }
            if (user.getLastName() != null) {
                request.put("lastName", user.getLastName());
            }
            if (user.getIsActive() != null) {
                request.put("isActive", user.getIsActive());
            }
            if (user.getIsVerified() != null) {
                request.put("isVerified", user.getIsVerified());
            }

            ResponseEntity<ApiResponse<UserResponse>> response = restTemplate.exchange(
                serviceUrl + USERS_API_PATH + "/" + id,
                HttpMethod.PUT,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() { }
            );

            if (response.getBody() != null && response.getBody().isSuccess()) {
                return fromResponse(response.getBody().getData());
            }
            throw new IllegalStateException("Failed to update user");
        } catch (RestClientException e) {
            log.error("HTTP error updating user", e);
            throw new IllegalStateException("Failed to update user: " + e.getMessage());
        }
    }

    @Override
    public void deleteUser(Long id) {
        try {
            log.debug("HTTP client: Deleting user {} via {}", id, serviceUrl);
            restTemplate.delete(serviceUrl + USERS_API_PATH + "/" + id);
        } catch (RestClientException e) {
            log.error("HTTP error deleting user", e);
            throw new IllegalStateException("Failed to delete user: " + e.getMessage());
        }
    }

    @Override
    public boolean existsByUsername(String username) {
        try {
            ResponseEntity<ApiResponse<ExistsResponse>> response = restTemplate.exchange(
                serviceUrl + "/internal/api/v1/users/exists/username/" + username,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() { }
            );
            if (response.getBody() != null && response.getBody().isSuccess()) {
                return response.getBody().getData().isExists();
            }
            return false;
        } catch (RestClientException e) {
            return false;
        }
    }

    @Override
    public boolean existsByEmail(String email) {
        try {
            ResponseEntity<ApiResponse<ExistsResponse>> response = restTemplate.exchange(
                serviceUrl + "/internal/api/v1/users/exists/email/" + email,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() { }
            );
            if (response.getBody() != null && response.getBody().isSuccess()) {
                return response.getBody().getData().isExists();
            }
            return false;
        } catch (RestClientException e) {
            return false;
        }
    }

    @lombok.Data
    private static class ExistsResponse {
        private boolean exists;
    }

    private User fromResponse(UserResponse response) {
        return User.builder()
            .id(response.getId())
            .username(response.getUsername())
            .email(response.getEmail())
            .firstName(response.getFirstName())
            .lastName(response.getLastName())
            .role(response.getRole() != null ? response.getRole() : UserRole.USER)
            .isActive(response.getIsActive())
            .isVerified(response.getIsVerified())
            .createdAt(response.getCreatedAt())
            .updatedAt(response.getUpdatedAt())
            .build();
    }
}
