package com.arcana.cloud.service.grpc;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.grpc.CreateUserRequest;
import com.arcana.cloud.grpc.DeleteUserRequest;
import com.arcana.cloud.grpc.DeleteUserResponse;
import com.arcana.cloud.grpc.ExistsByEmailRequest;
import com.arcana.cloud.grpc.GetUserRequest;
import com.arcana.cloud.grpc.ListUsersRequest;
import com.arcana.cloud.grpc.ListUsersResponse;
import com.arcana.cloud.grpc.UserResponse;
import com.arcana.cloud.grpc.UserServiceGrpc;
import com.arcana.cloud.repository.UserRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Real in-process gRPC tests for the repository-layer UserService server.
 *
 * <p>The server now reads through {@link UserRepository} (not UserDao directly), and refuses to
 * start with repository.mode=grpc, where UserRepository would be the gRPC client of this same
 * server.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserGrpcRepositoryService — Real In-Process gRPC Protocol Tests")
class UserGrpcRepositoryServiceRealTest {

    @Mock private UserRepository userRepository;

    private Server grpcServer;
    private ManagedChannel channel;
    private UserServiceGrpc.UserServiceBlockingStub stub;

    private final User alice = User.builder()
            .id(7L).username("alice").email("alice@example.com").password("hash")
            .firstName("Alice").role(UserRole.ADMIN).isActive(true).isVerified(true)
            .build();

    @BeforeEach
    void setUp() throws Exception {
        String name = InProcessServerBuilder.generateName();
        grpcServer = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new UserGrpcRepositoryService(userRepository))
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        stub = UserServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws Exception {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        grpcServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("getUser reads through UserRepository and maps the entity")
    void getUserReadsThroughRepository() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(alice));

        UserResponse resp = stub.getUser(GetUserRequest.newBuilder().setUserId(7L).build());

        assertEquals("alice", resp.getUsername());
        assertEquals("ADMIN", resp.getRole());
        assertTrue(resp.getIsActive());
        verify(userRepository).findById(7L);
    }

    @Test
    @DisplayName("getUser for a missing id returns NOT_FOUND")
    void getUserMissingIsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                () -> stub.getUser(GetUserRequest.newBuilder().setUserId(99L).build()));

        assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
    }

    @Test
    @DisplayName("listUsers pages through UserRepository.findAll(Pageable)")
    void listUsersPagesThroughRepository() {
        when(userRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(alice), PageRequest.of(0, 5), 1));

        ListUsersResponse resp = stub.listUsers(
                ListUsersRequest.newBuilder().setPage(0).setSize(5).build());

        assertEquals(1, resp.getUsersCount());
        assertEquals(1, resp.getPageInfo().getTotalElements());
        verify(userRepository).findAll(PageRequest.of(0, 5));
    }

    @Test
    @DisplayName("createUser saves through UserRepository")
    void createUserSavesThroughRepository() {
        when(userRepository.save(any(User.class))).thenReturn(alice);

        UserResponse resp = stub.createUser(CreateUserRequest.newBuilder()
                .setUsername("alice").setEmail("alice@example.com").setPassword("hash").build());

        assertEquals(7L, resp.getId());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("existsByEmail and deleteUser go through UserRepository")
    void existsAndDeleteThroughRepository() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertTrue(stub.existsByEmail(
                ExistsByEmailRequest.newBuilder().setEmail("alice@example.com").build()).getExists());
        DeleteUserResponse del = stub.deleteUser(DeleteUserRequest.newBuilder().setUserId(7L).build());

        assertTrue(del.getSuccess());
        verify(userRepository).deleteById(7L);
    }

    @Test
    @DisplayName("refuses repository.mode=grpc on the repository layer (would call itself)")
    void refusesGrpcRepositoryMode() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> UserGrpcRepositoryService.requireDirectRepository("grpc"));
        assertTrue(ex.getMessage().contains("loop"));
        assertDoesNotThrow(() -> UserGrpcRepositoryService.requireDirectRepository("direct"));
        assertDoesNotThrow(() -> UserGrpcRepositoryService.requireDirectRepository(null));
    }
}
