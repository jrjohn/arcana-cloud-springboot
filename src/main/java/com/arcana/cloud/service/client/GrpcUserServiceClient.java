package com.arcana.cloud.service.client;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.exception.ResourceNotFoundException;
import com.arcana.cloud.grpc.CreateUserRequest;
import com.arcana.cloud.grpc.GetUserByEmailRequest;
import com.arcana.cloud.grpc.GetUserByUsernameRequest;
import com.arcana.cloud.grpc.GetUserRequest;
import com.arcana.cloud.grpc.ListUsersRequest;
import com.arcana.cloud.grpc.ListUsersResponse;
import com.arcana.cloud.grpc.UpdateUserRequest;
import com.arcana.cloud.grpc.UserResponse;
import com.arcana.cloud.grpc.UserServiceGrpc;
import com.arcana.cloud.grpc.ExistsByUsernameRequest;
import com.arcana.cloud.grpc.ExistsByEmailRequest;
import com.arcana.cloud.grpc.DeleteUserRequest;
import com.arcana.cloud.service.interfaces.UserService;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import io.grpc.ManagedChannelBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnExpression(
    "'${communication.protocol:grpc}' == 'grpc' and '${deployment.layer:}' == 'controller'"
)
@Slf4j
public class GrpcUserServiceClient implements UserService {

    @Value("${service.grpc.url:localhost:9090}")
    private String serviceUrl;

    @Value("${grpc.client.shutdown-timeout-seconds:5}")
    private long shutdownTimeoutSeconds;

    private ManagedChannel channel;
    private UserServiceGrpc.UserServiceBlockingStub stub;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @PostConstruct
    public void init() {
        log.info("Initializing gRPC client for service URL: {}", serviceUrl);
        this.channel = ManagedChannelBuilder.forTarget(serviceUrl)
            .usePlaintext()
            .build();
        this.stub = UserServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdown().awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
        }
    }

    @Override
    public User createUser(User user) {
        try {
            CreateUserRequest request = CreateUserRequest.newBuilder()
                .setUsername(user.getUsername())
                .setEmail(user.getEmail())
                .setPassword(user.getPassword())
                .setFirstName(user.getFirstName() != null ? user.getFirstName() : "")
                .setLastName(user.getLastName() != null ? user.getLastName() : "")
                .build();

            UserResponse response = stub.createUser(request);
            return fromGrpcResponse(response);
        } catch (StatusRuntimeException e) {
            log.error("gRPC error creating user", e);
            throw new RuntimeException("Failed to create user: " + e.getStatus().getDescription());
        }
    }

    @Override
    public User getUserById(Long id) {
        try {
            GetUserRequest request = GetUserRequest.newBuilder()
                .setUserId(id)
                .build();

            UserResponse response = stub.getUser(request);
            return fromGrpcResponse(response);
        } catch (StatusRuntimeException e) {
            log.error("gRPC error getting user", e);
            throw new ResourceNotFoundException("User", "id", id);
        }
    }

    @Override
    public Optional<User> findByUsername(String username) {
        try {
            GetUserByUsernameRequest request = GetUserByUsernameRequest.newBuilder()
                .setUsername(username)
                .build();

            UserResponse response = stub.getUserByUsername(request);
            return Optional.of(fromGrpcResponse(response));
        } catch (StatusRuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        try {
            GetUserByEmailRequest request = GetUserByEmailRequest.newBuilder()
                .setEmail(email)
                .build();

            UserResponse response = stub.getUserByEmail(request);
            return Optional.of(fromGrpcResponse(response));
        } catch (StatusRuntimeException e) {
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
            ListUsersRequest request = ListUsersRequest.newBuilder()
                .setPage(page)
                .setSize(size)
                .build();

            ListUsersResponse response = stub.listUsers(request);
            List<User> users = response.getUsersList().stream()
                .map(this::fromGrpcResponse)
                .toList();

            return new PageImpl<>(users, PageRequest.of(page, size), response.getPageInfo().getTotalElements());
        } catch (StatusRuntimeException e) {
            log.error("gRPC error listing users", e);
            throw new RuntimeException("Failed to list users: " + e.getStatus().getDescription());
        }
    }

    @Override
    public User updateUser(Long id, User user) {
        try {
            UpdateUserRequest.Builder builder = UpdateUserRequest.newBuilder()
                .setUserId(id);

            if (user.getUsername() != null) {
                builder.setUsername(user.getUsername());
            }
            if (user.getEmail() != null) {
                builder.setEmail(user.getEmail());
            }
            if (user.getPassword() != null) {
                builder.setPassword(user.getPassword());
            }
            if (user.getFirstName() != null) {
                builder.setFirstName(user.getFirstName());
            }
            if (user.getLastName() != null) {
                builder.setLastName(user.getLastName());
            }
            if (user.getIsActive() != null) {
                builder.setIsActive(user.getIsActive());
            }
            if (user.getIsVerified() != null) {
                builder.setIsVerified(user.getIsVerified());
            }

            UserResponse response = stub.updateUser(builder.build());
            return fromGrpcResponse(response);
        } catch (StatusRuntimeException e) {
            log.error("gRPC error updating user", e);
            throw new RuntimeException("Failed to update user: " + e.getStatus().getDescription());
        }
    }

    @Override
    public void deleteUser(Long id) {
        try {
            DeleteUserRequest request = DeleteUserRequest.newBuilder()
                .setUserId(id)
                .build();

            stub.deleteUser(request);
        } catch (StatusRuntimeException e) {
            log.error("gRPC error deleting user", e);
            throw new RuntimeException("Failed to delete user: " + e.getStatus().getDescription());
        }
    }

    @Override
    public boolean existsByUsername(String username) {
        try {
            ExistsByUsernameRequest request = ExistsByUsernameRequest.newBuilder()
                .setUsername(username)
                .build();

            return stub.existsByUsername(request).getExists();
        } catch (StatusRuntimeException e) {
            log.error("gRPC error checking username existence", e);
            return false;
        }
    }

    @Override
    public boolean existsByEmail(String email) {
        try {
            ExistsByEmailRequest request = ExistsByEmailRequest.newBuilder()
                .setEmail(email)
                .build();

            return stub.existsByEmail(request).getExists();
        } catch (StatusRuntimeException e) {
            log.error("gRPC error checking email existence", e);
            return false;
        }
    }

    private User fromGrpcResponse(UserResponse response) {
        return User.builder()
            .id(response.getId())
            .username(response.getUsername())
            .email(response.getEmail())
            .firstName(response.getFirstName().isEmpty() ? null : response.getFirstName())
            .lastName(response.getLastName().isEmpty() ? null : response.getLastName())
            .role(UserRole.valueOf(response.getRole()))
            .isActive(response.getIsActive())
            .isVerified(response.getIsVerified())
            .createdAt(response.getCreatedAt().isEmpty()
                ? null : LocalDateTime.parse(response.getCreatedAt(), FORMATTER))
            .updatedAt(response.getUpdatedAt().isEmpty()
                ? null : LocalDateTime.parse(response.getUpdatedAt(), FORMATTER))
            .build();
    }
}
