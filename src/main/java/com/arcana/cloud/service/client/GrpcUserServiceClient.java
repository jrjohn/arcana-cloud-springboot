package com.arcana.cloud.service.client;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.exception.ResourceNotFoundException;
import com.arcana.cloud.exception.ServiceUnavailableException;
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
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.function.Supplier;

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

    @Autowired(required = false)
    private CircuitBreaker userServiceCircuitBreaker;

    @PostConstruct
    public void init() {
        log.info("Initializing gRPC client for service URL: {}", serviceUrl);
        this.channel = ManagedChannelBuilder.forTarget(serviceUrl)
            .usePlaintext()
            .build();
        this.stub = UserServiceGrpc.newBlockingStub(channel);

        if (userServiceCircuitBreaker != null) {
            log.info("Circuit Breaker enabled for User Service");
        }
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
        return executeWithCircuitBreaker(() -> {
            CreateUserRequest request = CreateUserRequest.newBuilder()
                .setUsername(user.getUsername())
                .setEmail(user.getEmail())
                .setPassword(user.getPassword())
                .setFirstName(user.getFirstName() != null ? user.getFirstName() : "")
                .setLastName(user.getLastName() != null ? user.getLastName() : "")
                .build();

            UserResponse response = stub.createUser(request);
            return fromGrpcResponse(response);
        }, "createUser");
    }

    @Override
    public User getUserById(Long id) {
        return executeWithCircuitBreaker(() -> {
            GetUserRequest request = GetUserRequest.newBuilder()
                .setUserId(id)
                .build();

            UserResponse response = stub.getUser(request);
            return fromGrpcResponse(response);
        }, "getUserById", () -> {
            throw new ResourceNotFoundException("User", "id", id);
        });
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return executeWithCircuitBreaker(() -> {
            GetUserByUsernameRequest request = GetUserByUsernameRequest.newBuilder()
                .setUsername(username)
                .build();

            UserResponse response = stub.getUserByUsername(request);
            return Optional.of(fromGrpcResponse(response));
        }, "findByUsername", Optional::empty);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return executeWithCircuitBreaker(() -> {
            GetUserByEmailRequest request = GetUserByEmailRequest.newBuilder()
                .setEmail(email)
                .build();

            UserResponse response = stub.getUserByEmail(request);
            return Optional.of(fromGrpcResponse(response));
        }, "findByEmail", Optional::empty);
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
        return executeWithCircuitBreaker(() -> {
            ListUsersRequest request = ListUsersRequest.newBuilder()
                .setPage(page)
                .setSize(size)
                .build();

            ListUsersResponse response = stub.listUsers(request);
            List<User> users = response.getUsersList().stream()
                .map(this::fromGrpcResponse)
                .toList();

            return new PageImpl<>(users, PageRequest.of(page, size), response.getPageInfo().getTotalElements());
        }, "getUsers");
    }

    @Override
    public User updateUser(Long id, User user) {
        return executeWithCircuitBreaker(() -> {
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
        }, "updateUser");
    }

    @Override
    public void deleteUser(Long id) {
        executeWithCircuitBreaker(() -> {
            DeleteUserRequest request = DeleteUserRequest.newBuilder()
                .setUserId(id)
                .build();

            stub.deleteUser(request);
            return null;
        }, "deleteUser");
    }

    @Override
    public boolean existsByUsername(String username) {
        return executeWithCircuitBreaker(() -> {
            ExistsByUsernameRequest request = ExistsByUsernameRequest.newBuilder()
                .setUsername(username)
                .build();

            return stub.existsByUsername(request).getExists();
        }, "existsByUsername", () -> false);
    }

    @Override
    public boolean existsByEmail(String email) {
        return executeWithCircuitBreaker(() -> {
            ExistsByEmailRequest request = ExistsByEmailRequest.newBuilder()
                .setEmail(email)
                .build();

            return stub.existsByEmail(request).getExists();
        }, "existsByEmail", () -> false);
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

    /**
     * Executes a gRPC call with circuit breaker protection.
     * Throws ServiceUnavailableException if circuit is open.
     */
    private <T> T executeWithCircuitBreaker(Supplier<T> supplier, String operation) {
        return executeWithCircuitBreaker(supplier, operation, () -> {
            throw new ServiceUnavailableException("User service is unavailable");
        });
    }

    /**
     * Executes a gRPC call with circuit breaker protection and fallback.
     */
    private <T> T executeWithCircuitBreaker(Supplier<T> supplier, String operation, Supplier<T> fallback) {
        try {
            if (userServiceCircuitBreaker != null) {
                return userServiceCircuitBreaker.executeSupplier(() -> executeGrpcCall(supplier, operation));
            } else {
                return executeGrpcCall(supplier, operation);
            }
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker OPEN for User Service, operation: {}", operation);
            return fallback.get();
        }
    }

    /**
     * Executes a gRPC call with proper error handling and categorization.
     */
    private <T> T executeGrpcCall(Supplier<T> supplier, String operation) {
        try {
            return supplier.get();
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();

            // Categorize error by status code for better debugging
            switch (code) {
                case NOT_FOUND:
                    log.debug("Resource not found in {}: {}", operation, e.getStatus().getDescription());
                    throw new ResourceNotFoundException("Resource", operation, e.getStatus().getDescription());
                case UNAVAILABLE, DEADLINE_EXCEEDED:
                    log.error("Service unavailable in {}: {} ({})", operation, e.getStatus().getDescription(), code);
                    throw new ServiceUnavailableException("User service unavailable: " + e.getStatus().getDescription());
                case PERMISSION_DENIED, UNAUTHENTICATED:
                    log.error("Permission denied in {}: {}", operation, e.getStatus().getDescription());
                    throw new UnauthorizedException("Permission denied: " + e.getStatus().getDescription());
                case INVALID_ARGUMENT:
                    log.error("Invalid argument in {}: {}", operation, e.getStatus().getDescription());
                    throw new IllegalArgumentException("Invalid argument: " + e.getStatus().getDescription());
                default:
                    log.error("gRPC error in {}: {} ({})", operation, e.getStatus().getDescription(), code);
                    throw new ServiceUnavailableException("Service error: " + e.getStatus().getDescription());
            }
        }
    }
}
