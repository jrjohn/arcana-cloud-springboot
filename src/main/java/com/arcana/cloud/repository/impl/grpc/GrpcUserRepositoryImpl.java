package com.arcana.cloud.repository.impl.grpc;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.grpc.CreateUserRequest;
import com.arcana.cloud.grpc.DeleteUserRequest;
import com.arcana.cloud.grpc.ExistsByEmailRequest;
import com.arcana.cloud.grpc.ExistsByUsernameRequest;
import com.arcana.cloud.grpc.GetUserByEmailRequest;
import com.arcana.cloud.grpc.GetUserByUsernameRequest;
import com.arcana.cloud.grpc.GetUserRequest;
import com.arcana.cloud.grpc.ListUsersRequest;
import com.arcana.cloud.grpc.ListUsersResponse;
import com.arcana.cloud.grpc.UpdateUserRequest;
import com.arcana.cloud.grpc.UserResponse;
import com.arcana.cloud.grpc.UserServiceGrpc;
import com.arcana.cloud.repository.UserRepository;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * gRPC-backed UserRepository implementation.
 * Active when repository.mode=grpc (service layer in 3-layer deployment).
 * Delegates all user data operations to the repository gRPC server (port 9091).
 */
@Repository
@Slf4j
@ConditionalOnExpression("'${repository.mode:direct}' == 'grpc'")
public class GrpcUserRepositoryImpl implements UserRepository {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int DEFAULT_PAGE_SIZE = 1000;

    private final UserServiceGrpc.UserServiceBlockingStub stub;

    public GrpcUserRepositoryImpl(@Qualifier("repositoryChannel") ManagedChannel repositoryChannel) {
        this.stub = UserServiceGrpc.newBlockingStub(repositoryChannel);
        log.info("gRPC User Repository initialized");
    }

    @Override
    public User save(User user) {
        if (user.getId() != null) {
            log.debug("gRPC Repository: Updating user id={}", user.getId());
            UpdateUserRequest.Builder builder = UpdateUserRequest.newBuilder()
                .setUserId(user.getId());
            if (user.getUsername() != null) builder.setUsername(user.getUsername());
            if (user.getEmail() != null) builder.setEmail(user.getEmail());
            if (user.getPassword() != null) builder.setPassword(user.getPassword());
            if (user.getFirstName() != null) builder.setFirstName(user.getFirstName());
            if (user.getLastName() != null) builder.setLastName(user.getLastName());
            if (user.getIsActive() != null) builder.setIsActive(user.getIsActive());
            if (user.getIsVerified() != null) builder.setIsVerified(user.getIsVerified());
            return mapFromProto(stub.updateUser(builder.build()));
        } else {
            log.debug("gRPC Repository: Creating user username={}", user.getUsername());
            CreateUserRequest request = CreateUserRequest.newBuilder()
                .setUsername(user.getUsername() != null ? user.getUsername() : "")
                .setEmail(user.getEmail() != null ? user.getEmail() : "")
                .setPassword(user.getPassword() != null ? user.getPassword() : "")
                .setFirstName(user.getFirstName() != null ? user.getFirstName() : "")
                .setLastName(user.getLastName() != null ? user.getLastName() : "")
                .build();
            return mapFromProto(stub.createUser(request));
        }
    }

    @Override
    public List<User> saveAll(Iterable<User> users) {
        List<User> results = new ArrayList<>();
        for (User user : users) {
            results.add(save(user));
        }
        return results;
    }

    @Override
    public Optional<User> findById(Long id) {
        log.debug("gRPC Repository: Finding user by id={}", id);
        try {
            UserResponse response = stub.getUser(
                GetUserRequest.newBuilder().setUserId(id).build()
            );
            return Optional.of(mapFromProto(response));
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            log.error("gRPC Repository: Error finding user by id={}", id, e);
            throw new RuntimeException("gRPC error: " + e.getStatus(), e);
        }
    }

    @Override
    public boolean existsById(Long id) {
        return findById(id).isPresent();
    }

    @Override
    public List<User> findAll() {
        log.debug("gRPC Repository: Finding all users");
        ListUsersResponse response = stub.listUsers(
            ListUsersRequest.newBuilder().setPage(0).setSize(DEFAULT_PAGE_SIZE).build()
        );
        return response.getUsersList().stream().map(this::mapFromProto).toList();
    }

    @Override
    public Page<User> findAll(Pageable pageable) {
        log.debug("gRPC Repository: Finding all users page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        ListUsersResponse response = stub.listUsers(
            ListUsersRequest.newBuilder()
                .setPage(pageable.getPageNumber())
                .setSize(pageable.getPageSize())
                .build()
        );
        List<User> users = response.getUsersList().stream().map(this::mapFromProto).toList();
        long total = response.hasPageInfo() ? response.getPageInfo().getTotalElements() : users.size();
        return new PageImpl<>(users, pageable, total);
    }

    @Override
    public long count() {
        ListUsersResponse response = stub.listUsers(
            ListUsersRequest.newBuilder().setPage(0).setSize(1).build()
        );
        return response.hasPageInfo() ? response.getPageInfo().getTotalElements() : 0L;
    }

    @Override
    public void deleteById(Long id) {
        log.info("gRPC Repository: Deleting user id={}", id);
        stub.deleteUser(DeleteUserRequest.newBuilder().setUserId(id).build());
    }

    @Override
    public void delete(User user) {
        if (user.getId() != null) {
            deleteById(user.getId());
        }
    }

    @Override
    public void deleteAll() {
        throw new UnsupportedOperationException("deleteAll is not supported via gRPC repository");
    }

    @Override
    public Optional<User> findByUsername(String username) {
        log.debug("gRPC Repository: Finding user by username={}", username);
        try {
            UserResponse response = stub.getUserByUsername(
                GetUserByUsernameRequest.newBuilder().setUsername(username).build()
            );
            return Optional.of(mapFromProto(response));
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            log.error("gRPC Repository: Error finding user by username={}", username, e);
            throw new RuntimeException("gRPC error: " + e.getStatus(), e);
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        log.debug("gRPC Repository: Finding user by email={}", email);
        try {
            UserResponse response = stub.getUserByEmail(
                GetUserByEmailRequest.newBuilder().setEmail(email).build()
            );
            return Optional.of(mapFromProto(response));
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            log.error("gRPC Repository: Error finding user by email={}", email, e);
            throw new RuntimeException("gRPC error: " + e.getStatus(), e);
        }
    }

    @Override
    public Optional<User> findByUsernameOrEmail(String username, String email) {
        log.debug("gRPC Repository: Finding user by username={} or email={}", username, email);
        Optional<User> byUsername = findByUsername(username);
        if (byUsername.isPresent()) {
            return byUsername;
        }
        return findByEmail(email);
    }

    @Override
    public boolean existsByUsername(String username) {
        try {
            return stub.existsByUsername(
                ExistsByUsernameRequest.newBuilder().setUsername(username).build()
            ).getExists();
        } catch (StatusRuntimeException e) {
            log.error("gRPC Repository: Error checking username existence", e);
            return false;
        }
    }

    @Override
    public boolean existsByEmail(String email) {
        try {
            return stub.existsByEmail(
                ExistsByEmailRequest.newBuilder().setEmail(email).build()
            ).getExists();
        } catch (StatusRuntimeException e) {
            log.error("gRPC Repository: Error checking email existence", e);
            return false;
        }
    }

    @Override
    public List<User> findActiveUsersByRole(com.arcana.cloud.entity.UserRole role) {
        log.warn("gRPC Repository: findActiveUsersByRole is not directly supported; fetching all users and filtering");
        return findAll().stream()
            .filter(u -> Boolean.TRUE.equals(u.getIsActive()) && role.equals(u.getRole()))
            .toList();
    }

    @Override
    public List<User> findAllActiveUsers() {
        log.debug("gRPC Repository: findAllActiveUsers - fetching all users and filtering");
        return findAll().stream()
            .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
            .toList();
    }

    @Override
    public List<User> findUnverifiedUsers() {
        log.debug("gRPC Repository: findUnverifiedUsers - fetching all users and filtering");
        return findAll().stream()
            .filter(u -> !Boolean.TRUE.equals(u.getIsVerified()))
            .toList();
    }

    private User mapFromProto(UserResponse r) {
        return User.builder()
            .id(r.getId())
            .username(r.getUsername())
            .email(r.getEmail())
            .firstName(r.getFirstName().isEmpty() ? null : r.getFirstName())
            .lastName(r.getLastName().isEmpty() ? null : r.getLastName())
            .role(r.getRole().isEmpty() ? UserRole.USER : UserRole.valueOf(r.getRole()))
            .isActive(r.getIsActive())
            .isVerified(r.getIsVerified())
            .createdAt(r.getCreatedAt().isEmpty() ? null : LocalDateTime.parse(r.getCreatedAt(), FORMATTER))
            .updatedAt(r.getUpdatedAt().isEmpty() ? null : LocalDateTime.parse(r.getUpdatedAt(), FORMATTER))
            .build();
    }
}
