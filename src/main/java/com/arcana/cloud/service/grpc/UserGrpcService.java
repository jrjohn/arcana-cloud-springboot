package com.arcana.cloud.service.grpc;

import com.arcana.cloud.entity.User;
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
import com.arcana.cloud.service.interfaces.UserService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * gRPC server for UserService.
 * Active in: monolithic mode OR service layer of layered mode.
 * Exposes UserService operations over gRPC for controller layer to consume.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnExpression("'${deployment.layer:}' == '' or '${deployment.layer:}' == 'service'")
@SuppressWarnings("java:S1068")
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private static final String USER_NOT_FOUND_MSG = "User not found";
    private static final String INTERNAL_ERROR_MSG = "Internal error";


    private final UserService userService;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public void getUser(GetUserRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            log.debug("gRPC: Getting user by id: {}", request.getUserId());
            User user = userService.getUserById(request.getUserId());
            responseObserver.onNext(toGrpcResponse(user));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC: Error getting user", e);
            responseObserver.onError(Status.NOT_FOUND
                .withDescription(USER_NOT_FOUND_MSG)
                .asRuntimeException());
        }
    }

    @Override
    public void getUserByUsername(GetUserByUsernameRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            log.debug("gRPC: Getting user by username: {}", request.getUsername());
            Optional<User> user = userService.findByUsername(request.getUsername());
            if (user.isPresent()) {
                responseObserver.onNext(toGrpcResponse(user.get()));
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(Status.NOT_FOUND
                    .withDescription(USER_NOT_FOUND_MSG)
                    .asRuntimeException());
            }
        } catch (Exception e) {
            log.error("gRPC: Error getting user by username", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription(INTERNAL_ERROR_MSG)
                .asRuntimeException());
        }
    }

    @Override
    public void getUserByEmail(GetUserByEmailRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            log.debug("gRPC: Getting user by email: {}", request.getEmail());
            Optional<User> user = userService.findByEmail(request.getEmail());
            if (user.isPresent()) {
                responseObserver.onNext(toGrpcResponse(user.get()));
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(Status.NOT_FOUND
                    .withDescription(USER_NOT_FOUND_MSG)
                    .asRuntimeException());
            }
        } catch (Exception e) {
            log.error("gRPC: Error getting user by email", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription(INTERNAL_ERROR_MSG)
                .asRuntimeException());
        }
    }

    @Override
    public void createUser(CreateUserRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            log.debug("gRPC: Creating user: {}", request.getUsername());
            User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(request.getPassword())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .build();

            User createdUser = userService.createUser(user);
            responseObserver.onNext(toGrpcResponse(createdUser));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC: Error creating user", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Failed to create user: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void updateUser(UpdateUserRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            log.debug("gRPC: Updating user: {}", request.getUserId());
            User userUpdate = User.builder()
                .username(request.hasUsername() ? request.getUsername() : null)
                .email(request.hasEmail() ? request.getEmail() : null)
                .password(request.hasPassword() ? request.getPassword() : null)
                .firstName(request.hasFirstName() ? request.getFirstName() : null)
                .lastName(request.hasLastName() ? request.getLastName() : null)
                .isActive(request.hasIsActive() ? request.getIsActive() : null)
                .isVerified(request.hasIsVerified() ? request.getIsVerified() : null)
                .build();

            User updatedUser = userService.updateUser(request.getUserId(), userUpdate);
            responseObserver.onNext(toGrpcResponse(updatedUser));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC: Error updating user", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Failed to update user: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void deleteUser(DeleteUserRequest request, StreamObserver<DeleteUserResponse> responseObserver) {
        try {
            log.debug("gRPC: Deleting user: {}", request.getUserId());
            userService.deleteUser(request.getUserId());
            responseObserver.onNext(DeleteUserResponse.newBuilder()
                .setSuccess(true)
                .setMessage("User deleted successfully")
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC: Error deleting user", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Failed to delete user: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void listUsers(ListUsersRequest request, StreamObserver<ListUsersResponse> responseObserver) {
        try {
            log.debug("gRPC: Listing users page: {}, size: {}", request.getPage(), request.getSize());
            Page<User> usersPage = userService.getUsers(request.getPage(), request.getSize());

            ListUsersResponse.Builder responseBuilder = ListUsersResponse.newBuilder();
            usersPage.getContent().forEach(user -> responseBuilder.addUsers(toGrpcResponse(user)));

            responseBuilder.setPageInfo(PageInfo.newBuilder()
                .setPage(request.getPage())
                .setSize(request.getSize())
                .setTotalElements(usersPage.getTotalElements())
                .setTotalPages(usersPage.getTotalPages())
                .build());

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC: Error listing users", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Failed to list users")
                .asRuntimeException());
        }
    }

    @Override
    public void existsByUsername(ExistsByUsernameRequest request, StreamObserver<ExistsResponse> responseObserver) {
        try {
            boolean exists = userService.existsByUsername(request.getUsername());
            responseObserver.onNext(ExistsResponse.newBuilder().setExists(exists).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC: Error checking username existence", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription(INTERNAL_ERROR_MSG)
                .asRuntimeException());
        }
    }

    @Override
    public void existsByEmail(ExistsByEmailRequest request, StreamObserver<ExistsResponse> responseObserver) {
        try {
            boolean exists = userService.existsByEmail(request.getEmail());
            responseObserver.onNext(ExistsResponse.newBuilder().setExists(exists).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("gRPC: Error checking email existence", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription(INTERNAL_ERROR_MSG)
                .asRuntimeException());
        }
    }

    private UserResponse toGrpcResponse(User user) {
        return UserResponse.newBuilder()
            .setId(user.getId())
            .setUsername(user.getUsername())
            .setEmail(user.getEmail())
            .setFirstName(user.getFirstName() != null ? user.getFirstName() : "")
            .setLastName(user.getLastName() != null ? user.getLastName() : "")
            .setRole(user.getRole().name())
            .setIsActive(user.getIsActive() != null && user.getIsActive())
            .setIsVerified(user.getIsVerified() != null && user.getIsVerified())
            .setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().format(FORMATTER) : "")
            .setUpdatedAt(user.getUpdatedAt() != null ? user.getUpdatedAt().format(FORMATTER) : "")
            .build();
    }
}
