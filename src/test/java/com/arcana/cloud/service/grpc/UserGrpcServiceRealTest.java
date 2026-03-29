package com.arcana.cloud.service.grpc;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Real gRPC integration tests for UserGrpcService.
 *
 * <p>Uses {@link InProcessServerBuilder} — requests travel through actual protobuf
 * serialization and gRPC transport, validating the full wire protocol.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserGrpcService — Real In-Process gRPC Protocol Tests")
class UserGrpcServiceRealTest {

    @Mock private com.arcana.cloud.service.UserService userService;

    private Server grpcServer;
    private ManagedChannel channel;
    private UserServiceGrpc.UserServiceBlockingStub stub;

    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        testUser = User.builder()
                .id(1L).username("testuser").email("test@example.com")
                .firstName("Test").lastName("User")
                .role(UserRole.USER).isActive(true).isVerified(false)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        UserGrpcService impl = new UserGrpcService(userService);
        String serverName = InProcessServerBuilder.generateName();
        grpcServer = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(impl)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        stub = UserServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws Exception {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        grpcServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    // ─── getUser ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getUser: found — user fields survive real protobuf wire round-trip")
    void getUser_found_realWire() {
        when(userService.getUserById(1L)).thenReturn(testUser);

        UserResponse resp = stub.getUser(
                GetUserRequest.newBuilder().setUserId(1L).build()
        );

        assertEquals(1L, resp.getId());
        assertEquals("testuser", resp.getUsername());
        assertEquals("test@example.com", resp.getEmail());
        assertEquals("Test", resp.getFirstName());
        assertTrue(resp.getIsActive());
    }

    @Test
    @DisplayName("getUser: exception → gRPC NOT_FOUND status over wire")
    void getUser_notFound_grpcStatus() {
        when(userService.getUserById(999L)).thenThrow(new RuntimeException("User not found"));

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                stub.getUser(GetUserRequest.newBuilder().setUserId(999L).build())
        );

        assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
    }

    // ─── getUserByUsername ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getUserByUsername: found — username field round-trips correctly over wire")
    void getUserByUsername_found_realWire() {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        UserResponse resp = stub.getUserByUsername(
                GetUserByUsernameRequest.newBuilder().setUsername("testuser").build()
        );

        assertEquals("testuser", resp.getUsername());
        assertEquals("test@example.com", resp.getEmail());
    }

    @Test
    @DisplayName("getUserByUsername: not found → gRPC NOT_FOUND status over wire")
    void getUserByUsername_notFound_grpcStatus() {
        when(userService.findByUsername("unknown")).thenReturn(Optional.empty());

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                stub.getUserByUsername(
                        GetUserByUsernameRequest.newBuilder().setUsername("unknown").build())
        );

        assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
    }

    // ─── createUser ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createUser: success — created user fields serialized over real gRPC wire")
    void createUser_success_realWire() {
        when(userService.createUser(any(User.class))).thenReturn(testUser);

        UserResponse resp = stub.createUser(
                CreateUserRequest.newBuilder()
                        .setUsername("testuser").setEmail("test@example.com")
                        .setPassword("password").setFirstName("Test").setLastName("User")
                        .build()
        );

        assertEquals("testuser", resp.getUsername());
        assertEquals("test@example.com", resp.getEmail());
    }

    @Test
    @DisplayName("createUser: exception → gRPC INTERNAL status over wire")
    void createUser_error_grpcStatus() {
        when(userService.createUser(any(User.class)))
                .thenThrow(new RuntimeException("Duplicate username"));

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                stub.createUser(CreateUserRequest.newBuilder()
                        .setUsername("testuser").setEmail("test@example.com")
                        .setPassword("password").build())
        );

        // UserGrpcService maps all createUser exceptions to INTERNAL
        assertEquals(Status.Code.INTERNAL, ex.getStatus().getCode());
    }

    // ─── deleteUser ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteUser: success — success=true serialized over real gRPC wire")
    void deleteUser_success_realWire() {
        doNothing().when(userService).deleteUser(1L);

        DeleteUserResponse resp = stub.deleteUser(
                DeleteUserRequest.newBuilder().setUserId(1L).build()
        );

        assertTrue(resp.getSuccess());
        assertEquals("User deleted successfully", resp.getMessage());
        verify(userService).deleteUser(1L);
    }

    @Test
    @DisplayName("deleteUser: exception → gRPC INTERNAL status over wire")
    void deleteUser_error_grpcStatus() {
        doThrow(new RuntimeException("User not found"))
                .when(userService).deleteUser(999L);

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                stub.deleteUser(DeleteUserRequest.newBuilder().setUserId(999L).build())
        );

        assertEquals(Status.Code.INTERNAL, ex.getStatus().getCode());
    }

    // ─── listUsers ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listUsers: returns page — pagination fields serialized over real gRPC wire")
    void listUsers_pagination_realWire() {
        Page<User> page = new PageImpl<>(
                List.of(testUser), PageRequest.of(0, 10), 1L
        );
        when(userService.getUsers(0, 10)).thenReturn(page);

        ListUsersResponse resp = stub.listUsers(
                ListUsersRequest.newBuilder().setPage(0).setSize(10).build()
        );

        assertEquals(1, resp.getUsersCount());
        assertEquals("testuser", resp.getUsers(0).getUsername());
        assertTrue(resp.hasPageInfo());
        assertEquals(1L, resp.getPageInfo().getTotalElements());
        assertEquals(1, resp.getPageInfo().getTotalPages());
    }

    // ─── existsByUsername / existsByEmail ──────────────────────────────────────

    @Test
    @DisplayName("existsByUsername: true — boolean serialized correctly over real gRPC wire")
    void existsByUsername_true_realWire() {
        when(userService.existsByUsername("testuser")).thenReturn(true);

        ExistsResponse resp = stub.existsByUsername(
                ExistsByUsernameRequest.newBuilder().setUsername("testuser").build()
        );

        assertTrue(resp.getExists());
    }

    @Test
    @DisplayName("existsByEmail: false — boolean=false serialized correctly over real gRPC wire")
    void existsByEmail_false_realWire() {
        when(userService.existsByEmail("unknown@example.com")).thenReturn(false);

        ExistsResponse resp = stub.existsByEmail(
                ExistsByEmailRequest.newBuilder().setEmail("unknown@example.com").build()
        );

        assertFalse(resp.getExists());
    }
}
