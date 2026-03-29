package com.arcana.cloud.service.grpc;

import com.arcana.cloud.dao.UserDao;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.grpc.CreateUserRequest;
import com.arcana.cloud.grpc.DeleteUserRequest;
import com.arcana.cloud.grpc.DeleteUserResponse;
import com.arcana.cloud.grpc.ExistsByEmailRequest;
import com.arcana.cloud.grpc.ExistsByUsernameRequest;
import com.arcana.cloud.grpc.ExistsResponse;
import com.arcana.cloud.grpc.GetUserByEmailRequest;
import com.arcana.cloud.grpc.GetUserByUsernameRequest;
import com.arcana.cloud.grpc.GetUserRequest;
import com.arcana.cloud.grpc.ListUsersRequest;
import com.arcana.cloud.grpc.ListUsersResponse;
import com.arcana.cloud.grpc.PageInfo;
import com.arcana.cloud.grpc.UpdateUserRequest;
import com.arcana.cloud.grpc.UserResponse;
import com.arcana.cloud.grpc.UserServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * gRPC server for UserService — Repository layer.
 * Active only in the repository layer of the 3-layer deployment (deployment.layer=repository).
 * Exposes raw data-access operations over gRPC for the service layer to consume.
 * Backed directly by UserDao (JPA/MyBatis).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnExpression("'${deployment.layer:}' == 'repository'")
@SuppressWarnings("java:S1068")
public class UserGrpcRepositoryService extends UserServiceGrpc.UserServiceImplBase {

    private static final String USER_NOT_FOUND_MSG = "User not found";
    private static final String INTERNAL_ERROR_MSG = "Internal error";

    private final UserDao userDao;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public void getUser(GetUserRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            log.debug("gRPC Repository: Getting user by id={}", request.getUserId());
            userDao.findById(request.getUserId())
                .map(this::toGrpcResponse)
                .ifPresentOrElse(
                    resp -> {
                        responseObserver.onNext(resp);
                        responseObserver.onCompleted();
                    },
                    () -> responseObserver.onError(
                        Status.NOT_FOUND.withDescription(USER_NOT_FOUND_MSG).asRuntimeException()
                    )
                );
        } catch (Exception e) {
            log.error("gRPC Repository: Error getting user by id", e);
            responseObserver.onError(
                Status.INTERNAL.withDescription(INTERNAL_ERROR_MSG).asRuntimeException()
            );
        }
    }

    @Override
    public void getUserByUsername(GetUserByUsernameRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            log.debug("gRPC Repository: Getting user by username={}", request.getUsername());
            userDao.findByUsername(request.getUsername())
                .map(this::toGrpcResponse)
                .ifPresentOrElse(
                    resp -> {
                        responseObserver.onNext(resp);
                        responseObserver.onCompleted();
                    },
                    () -> responseObserver.onError(
                        Status.NOT_FOUND.withDescription(USER_NOT_FOUND_MSG).asRuntimeException()
                    )
                );
        } catch (Exception e) {
            log.error("gRPC Repository: Error getting user by username", e);
            responseObserver.onError(
                Status.INTERNAL.withDescription(INTERNAL_ERROR_MSG).asRuntimeException()
            );
        }
    }

    @Override
    public void getUserByEmail(GetUserByEmailRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            log.debug("gRPC Repository: Getting user by email={}", request.getEmail());
            userDao.findByEmail(request.getEmail())
                .map(this::toGrpcResponse)
                .ifPresentOrElse(
                    resp -> {
                        responseObserver.onNext(resp);
                        responseObserver.onCompleted();
                    },
                    () -> responseObserver.onError(
                        Status.NOT_FOUND.withDescription(USER_NOT_FOUND_MSG).asRuntimeException()
                    )
                );
        } catch (Exception e) {
            log.error("gRPC Repository: Error getting user by email", e);
            responseObserver.onError(
                Status.INTERNAL.withDescription(INTERNAL_ERROR_MSG).asRuntimeException()
            );
        }
    }

    @Override
    public void createUser(CreateUserRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            log.debug("gRPC Repository: Creating user username={}", request.getUsername());
            User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(request.getPassword())
                .firstName(request.getFirstName().isEmpty() ? null : request.getFirstName())
                .lastName(request.getLastName().isEmpty() ? null : request.getLastName())
                .build();
            User saved = userDao.save(user);
            responseObserver.onNext(toGrpcResponse(saved));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC Repository: Error creating user", e);
            responseObserver.onError(
                Status.INTERNAL.withDescription("Failed to create user: " + e.getMessage()).asRuntimeException()
            );
        }
    }

    @Override
    public void updateUser(UpdateUserRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            log.debug("gRPC Repository: Updating user id={}", request.getUserId());
            User existing = userDao.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException(USER_NOT_FOUND_MSG));

            if (request.hasUsername()) existing.setUsername(request.getUsername());
            if (request.hasEmail()) existing.setEmail(request.getEmail());
            if (request.hasPassword()) existing.setPassword(request.getPassword());
            if (request.hasFirstName()) existing.setFirstName(request.getFirstName());
            if (request.hasLastName()) existing.setLastName(request.getLastName());
            if (request.hasIsActive()) existing.setIsActive(request.getIsActive());
            if (request.hasIsVerified()) existing.setIsVerified(request.getIsVerified());

            User saved = userDao.save(existing);
            responseObserver.onNext(toGrpcResponse(saved));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC Repository: Error updating user", e);
            responseObserver.onError(
                Status.INTERNAL.withDescription("Failed to update user: " + e.getMessage()).asRuntimeException()
            );
        }
    }

    @Override
    public void deleteUser(DeleteUserRequest request, StreamObserver<DeleteUserResponse> responseObserver) {
        try {
            log.debug("gRPC Repository: Deleting user id={}", request.getUserId());
            userDao.deleteById(request.getUserId());
            responseObserver.onNext(DeleteUserResponse.newBuilder()
                .setSuccess(true)
                .setMessage("User deleted successfully")
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC Repository: Error deleting user", e);
            responseObserver.onError(
                Status.INTERNAL.withDescription("Failed to delete user: " + e.getMessage()).asRuntimeException()
            );
        }
    }

    @Override
    public void listUsers(ListUsersRequest request, StreamObserver<ListUsersResponse> responseObserver) {
        try {
            log.debug("gRPC Repository: Listing users page={}, size={}", request.getPage(), request.getSize());
            int size = request.getSize() > 0 ? request.getSize() : 20;
            PageRequest pageRequest = PageRequest.of(request.getPage(), size);
            Page<User> usersPage = userDao.findAll(pageRequest);

            ListUsersResponse.Builder builder = ListUsersResponse.newBuilder();
            usersPage.getContent().forEach(user -> builder.addUsers(toGrpcResponse(user)));
            builder.setPageInfo(PageInfo.newBuilder()
                .setPage(request.getPage())
                .setSize(size)
                .setTotalElements(usersPage.getTotalElements())
                .setTotalPages(usersPage.getTotalPages())
                .build());

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC Repository: Error listing users", e);
            responseObserver.onError(
                Status.INTERNAL.withDescription("Failed to list users").asRuntimeException()
            );
        }
    }

    @Override
    public void existsByUsername(ExistsByUsernameRequest request, StreamObserver<ExistsResponse> responseObserver) {
        try {
            boolean exists = userDao.existsByUsername(request.getUsername());
            responseObserver.onNext(ExistsResponse.newBuilder().setExists(exists).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC Repository: Error checking username existence", e);
            responseObserver.onError(
                Status.INTERNAL.withDescription(INTERNAL_ERROR_MSG).asRuntimeException()
            );
        }
    }

    @Override
    public void existsByEmail(ExistsByEmailRequest request, StreamObserver<ExistsResponse> responseObserver) {
        try {
            boolean exists = userDao.existsByEmail(request.getEmail());
            responseObserver.onNext(ExistsResponse.newBuilder().setExists(exists).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC Repository: Error checking email existence", e);
            responseObserver.onError(
                Status.INTERNAL.withDescription(INTERNAL_ERROR_MSG).asRuntimeException()
            );
        }
    }

    private UserResponse toGrpcResponse(User user) {
        return UserResponse.newBuilder()
            .setId(user.getId())
            .setUsername(user.getUsername())
            .setEmail(user.getEmail())
            .setPassword(user.getPassword() != null ? user.getPassword() : "")
            .setFirstName(user.getFirstName() != null ? user.getFirstName() : "")
            .setLastName(user.getLastName() != null ? user.getLastName() : "")
            .setRole(user.getRole() != null ? user.getRole().name() : UserRole.USER.name())
            .setIsActive(user.getIsActive() != null && user.getIsActive())
            .setIsVerified(user.getIsVerified() != null && user.getIsVerified())
            .setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().format(FORMATTER) : "")
            .setUpdatedAt(user.getUpdatedAt() != null ? user.getUpdatedAt().format(FORMATTER) : "")
            .build();
    }
}
