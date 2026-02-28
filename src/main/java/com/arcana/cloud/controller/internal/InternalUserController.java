package com.arcana.cloud.controller.internal;

import com.arcana.cloud.dto.response.ApiResponse;
import com.arcana.cloud.dto.response.PagedResponse;
import com.arcana.cloud.dto.response.UserResponse;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.mapper.UserMapper;
import com.arcana.cloud.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal REST controller for UserService.
 * Active in: service layer of layered mode when using HTTP communication.
 * Provides HTTP endpoints for controller layer to consume (alternative to gRPC).
 */
@RestController
@RequestMapping("/internal/api/v1/users")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnExpression(
    "'${deployment.layer:}' == 'service' and '${communication.protocol:grpc}' == 'http'"
)
public class InternalUserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long id) {
        log.debug("Internal HTTP: Getting user by id: {}", id);
        User user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(userMapper.toResponse(user), "User retrieved"));
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByUsername(
            @PathVariable String username) {
        log.debug("Internal HTTP: Getting user by username: {}", username);
        return userService.findByUsername(username)
            .map(user -> ResponseEntity.ok(
                ApiResponse.success(userMapper.toResponse(user), "User found")))
            .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("User not found")));
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByEmail(@PathVariable String email) {
        log.debug("Internal HTTP: Getting user by email: {}", email);
        return userService.findByEmail(email)
            .map(user -> ResponseEntity.ok(
                ApiResponse.success(userMapper.toResponse(user), "User found")))
            .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("User not found")));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.debug("Internal HTTP: Listing users page: {}, size: {}", page, size);
        Page<User> usersPage = userService.getUsers(page, size);
        List<UserResponse> userResponses = usersPage.getContent().stream()
            .map(userMapper::toResponse)
            .toList();

        PagedResponse<UserResponse> pagedResponse = PagedResponse.<UserResponse>builder()
            .content(userResponses)
            .page(page)
            .size(size)
            .totalElements(usersPage.getTotalElements())
            .totalPages(usersPage.getTotalPages())
            .build();

        return ResponseEntity.ok(ApiResponse.success(pagedResponse, "Users retrieved"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@RequestBody UserCreateDto request) {
        log.debug("Internal HTTP: Creating user: {}", request.getUsername());
        User user = User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .password(request.getPassword())
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .build();

        User createdUser = userService.createUser(user);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(userMapper.toResponse(createdUser), "User created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id,
            @RequestBody UserUpdateDto request) {
        log.debug("Internal HTTP: Updating user: {}", id);
        User userUpdate = User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .password(request.getPassword())
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .isActive(request.getIsActive())
            .isVerified(request.getIsVerified())
            .build();

        User updatedUser = userService.updateUser(id, userUpdate);
        return ResponseEntity.ok(ApiResponse.success(userMapper.toResponse(updatedUser), "User updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        log.debug("Internal HTTP: Deleting user: {}", id);
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success(null, "User deleted"));
    }

    @GetMapping("/exists/username/{username}")
    public ResponseEntity<ApiResponse<ExistsResponse>> existsByUsername(
            @PathVariable String username) {
        boolean exists = userService.existsByUsername(username);
        return ResponseEntity.ok(
            ApiResponse.success(new ExistsResponse(exists), "Check complete"));
    }

    @GetMapping("/exists/email/{email}")
    public ResponseEntity<ApiResponse<ExistsResponse>> existsByEmail(@PathVariable String email) {
        boolean exists = userService.existsByEmail(email);
        return ResponseEntity.ok(
            ApiResponse.success(new ExistsResponse(exists), "Check complete"));
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserCreateDto {
        private String username;
        private String email;
        private String password;
        private String firstName;
        private String lastName;
    }

    @lombok.Data
    public static class UserUpdateDto {
        private String username;
        private String email;
        private String password;
        private String firstName;
        private String lastName;
        private Boolean isActive;
        private Boolean isVerified;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ExistsResponse {
        private boolean exists;
    }
}
