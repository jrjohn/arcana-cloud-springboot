package com.arcana.cloud.service.grpc;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.exception.ResourceNotFoundException;
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
import com.arcana.cloud.grpc.UpdateUserRequest;
import com.arcana.cloud.grpc.UserResponse;
import com.arcana.cloud.service.UserService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserGrpcServiceTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserGrpcService userGrpcService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .firstName("Test")
            .lastName("User")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(false)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    // ===== getUser =====

    @Test
    void testGetUser_Success() {
        GetUserRequest request = GetUserRequest.newBuilder()
            .setUserId(1L)
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<UserResponse> responseObserver = mock(StreamObserver.class);

        when(userService.getUserById(1L)).thenReturn(testUser);

        userGrpcService.getUser(request, responseObserver);

        ArgumentCaptor<UserResponse> captor = ArgumentCaptor.forClass(UserResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        UserResponse response = captor.getValue();
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("testuser", response.getUsername());
        assertEquals("test@example.com", response.getEmail());
    }

    @Test
    void testGetUser_NotFound() {
        GetUserRequest request = GetUserRequest.newBuilder()
            .setUserId(999L)
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<UserResponse> responseObserver = mock(StreamObserver.class);

        when(userService.getUserById(999L)).thenThrow(new ResourceNotFoundException("User", "id", 999L));

        userGrpcService.getUser(request, responseObserver);

        verify(responseObserver).onError(any());
    }

    // ===== getUserByUsername =====

    @Test
    void testGetUserByUsername_Found() {
        GetUserByUsernameRequest request = GetUserByUsernameRequest.newBuilder()
            .setUsername("testuser")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<UserResponse> responseObserver = mock(StreamObserver.class);

        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        userGrpcService.getUserByUsername(request, responseObserver);

        ArgumentCaptor<UserResponse> captor = ArgumentCaptor.forClass(UserResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertEquals("testuser", captor.getValue().getUsername());
    }

    @Test
    void testGetUserByUsername_NotFound() {
        GetUserByUsernameRequest request = GetUserByUsernameRequest.newBuilder()
            .setUsername("unknown")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<UserResponse> responseObserver = mock(StreamObserver.class);

        when(userService.findByUsername("unknown")).thenReturn(Optional.empty());

        userGrpcService.getUserByUsername(request, responseObserver);

        verify(responseObserver).onError(any());
    }

    @Test
    void testGetUserByUsername_Exception() {
        GetUserByUsernameRequest request = GetUserByUsernameRequest.newBuilder()
            .setUsername("testuser")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<UserResponse> responseObserver = mock(StreamObserver.class);

        when(userService.findByUsername("testuser")).thenThrow(new RuntimeException("DB error"));

        userGrpcService.getUserByUsername(request, responseObserver);

        verify(responseObserver).onError(any());
    }

    // ===== getUserByEmail =====

    @Test
    void testGetUserByEmail_Found() {
        GetUserByEmailRequest request = GetUserByEmailRequest.newBuilder()
            .setEmail("test@example.com")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<UserResponse> responseObserver = mock(StreamObserver.class);

        when(userService.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        userGrpcService.getUserByEmail(request, responseObserver);

        ArgumentCaptor<UserResponse> captor = ArgumentCaptor.forClass(UserResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertEquals("test@example.com", captor.getValue().getEmail());
    }

    @Test
    void testGetUserByEmail_NotFound() {
        GetUserByEmailRequest request = GetUserByEmailRequest.newBuilder()
            .setEmail("unknown@example.com")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<UserResponse> responseObserver = mock(StreamObserver.class);

        when(userService.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        userGrpcService.getUserByEmail(request, responseObserver);

        verify(responseObserver).onError(any());
    }

    @Test
    void testGetUserByEmail_Exception() {
        GetUserByEmailRequest request = GetUserByEmailRequest.newBuilder()
            .setEmail("test@example.com")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<UserResponse> responseObserver = mock(StreamObserver.class);

        when(userService.findByEmail("test@example.com")).thenThrow(new RuntimeException("DB error"));

        userGrpcService.getUserByEmail(request, responseObserver);

        verify(responseObserver).onError(any());
    }

    // ===== createUser =====

    @Test
    void testCreateUser_Success() {
        CreateUserRequest request = CreateUserRequest.newBuilder()
            .setUsername("newuser")
            .setEmail("new@example.com")
            .setPassword("password")
            .setFirstName("New")
            .setLastName("User")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<UserResponse> responseObserver = mock(StreamObserver.class);

        when(userService.createUser(any(User.class))).thenReturn(testUser);

        userGrpcService.createUser(request, responseObserver);

        ArgumentCaptor<UserResponse> captor = ArgumentCaptor.forClass(UserResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertNotNull(captor.getValue());
    }

    @Test
    void testCreateUser_Error() {
        CreateUserRequest request = CreateUserRequest.newBuilder()
            .setUsername("existinguser")
            .setEmail("existing@example.com")
            .setPassword("password")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<UserResponse> responseObserver = mock(StreamObserver.class);

        when(userService.createUser(any(User.class))).thenThrow(new RuntimeException("Username exists"));

        userGrpcService.createUser(request, responseObserver);

        verify(responseObserver).onError(any());
    }

    // ===== updateUser =====

    @Test
    void testUpdateUser_Success() {
        UpdateUserRequest request = UpdateUserRequest.newBuilder()
            .setUserId(1L)
            .setUsername("updateduser")
            .setEmail("updated@example.com")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<UserResponse> responseObserver = mock(StreamObserver.class);

        when(userService.updateUser(anyLong(), any(User.class))).thenReturn(testUser);

        userGrpcService.updateUser(request, responseObserver);

        ArgumentCaptor<UserResponse> captor = ArgumentCaptor.forClass(UserResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertNotNull(captor.getValue());
    }

    @Test
    void testUpdateUser_Error() {
        UpdateUserRequest request = UpdateUserRequest.newBuilder()
            .setUserId(999L)
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<UserResponse> responseObserver = mock(StreamObserver.class);

        when(userService.updateUser(anyLong(), any(User.class)))
            .thenThrow(new ResourceNotFoundException("User", "id", 999L));

        userGrpcService.updateUser(request, responseObserver);

        verify(responseObserver).onError(any());
    }

    // ===== deleteUser =====

    @Test
    void testDeleteUser_Success() {
        DeleteUserRequest request = DeleteUserRequest.newBuilder()
            .setUserId(1L)
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<DeleteUserResponse> responseObserver = mock(StreamObserver.class);

        userGrpcService.deleteUser(request, responseObserver);

        ArgumentCaptor<DeleteUserResponse> captor = ArgumentCaptor.forClass(DeleteUserResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertTrue(captor.getValue().getSuccess());
        assertEquals("User deleted successfully", captor.getValue().getMessage());
    }

    @Test
    void testDeleteUser_Error() {
        DeleteUserRequest request = DeleteUserRequest.newBuilder()
            .setUserId(999L)
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<DeleteUserResponse> responseObserver = mock(StreamObserver.class);

        org.mockito.Mockito.doThrow(new ResourceNotFoundException("User", "id", 999L))
            .when(userService).deleteUser(999L);

        userGrpcService.deleteUser(request, responseObserver);

        verify(responseObserver).onError(any());
    }

    // ===== listUsers =====

    @Test
    void testListUsers_Success() {
        ListUsersRequest request = ListUsersRequest.newBuilder()
            .setPage(0)
            .setSize(10)
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<ListUsersResponse> responseObserver = mock(StreamObserver.class);

        Page<User> usersPage = new PageImpl<>(List.of(testUser), PageRequest.of(0, 10), 1);
        when(userService.getUsers(0, 10)).thenReturn(usersPage);

        userGrpcService.listUsers(request, responseObserver);

        ArgumentCaptor<ListUsersResponse> captor = ArgumentCaptor.forClass(ListUsersResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        ListUsersResponse response = captor.getValue();
        assertNotNull(response);
        assertEquals(1, response.getUsersCount());
        assertEquals(1, response.getPageInfo().getTotalElements());
    }

    @Test
    void testListUsers_Empty() {
        ListUsersRequest request = ListUsersRequest.newBuilder()
            .setPage(0)
            .setSize(10)
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<ListUsersResponse> responseObserver = mock(StreamObserver.class);

        Page<User> usersPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(userService.getUsers(0, 10)).thenReturn(usersPage);

        userGrpcService.listUsers(request, responseObserver);

        ArgumentCaptor<ListUsersResponse> captor = ArgumentCaptor.forClass(ListUsersResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertEquals(0, captor.getValue().getUsersCount());
    }

    @Test
    void testListUsers_Error() {
        ListUsersRequest request = ListUsersRequest.newBuilder()
            .setPage(0)
            .setSize(10)
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<ListUsersResponse> responseObserver = mock(StreamObserver.class);

        when(userService.getUsers(anyInt(), anyInt())).thenThrow(new RuntimeException("DB error"));

        userGrpcService.listUsers(request, responseObserver);

        verify(responseObserver).onError(any());
    }

    // ===== existsByUsername =====

    @Test
    void testExistsByUsername_True() {
        ExistsByUsernameRequest request = ExistsByUsernameRequest.newBuilder()
            .setUsername("testuser")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<ExistsResponse> responseObserver = mock(StreamObserver.class);

        when(userService.existsByUsername("testuser")).thenReturn(true);

        userGrpcService.existsByUsername(request, responseObserver);

        ArgumentCaptor<ExistsResponse> captor = ArgumentCaptor.forClass(ExistsResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertTrue(captor.getValue().getExists());
    }

    @Test
    void testExistsByUsername_False() {
        ExistsByUsernameRequest request = ExistsByUsernameRequest.newBuilder()
            .setUsername("unknown")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<ExistsResponse> responseObserver = mock(StreamObserver.class);

        when(userService.existsByUsername("unknown")).thenReturn(false);

        userGrpcService.existsByUsername(request, responseObserver);

        ArgumentCaptor<ExistsResponse> captor = ArgumentCaptor.forClass(ExistsResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertFalse(captor.getValue().getExists());
    }

    @Test
    void testExistsByUsername_Exception() {
        ExistsByUsernameRequest request = ExistsByUsernameRequest.newBuilder()
            .setUsername("testuser")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<ExistsResponse> responseObserver = mock(StreamObserver.class);

        when(userService.existsByUsername("testuser")).thenThrow(new RuntimeException("DB error"));

        userGrpcService.existsByUsername(request, responseObserver);

        verify(responseObserver).onError(any());
    }

    // ===== existsByEmail =====

    @Test
    void testExistsByEmail_True() {
        ExistsByEmailRequest request = ExistsByEmailRequest.newBuilder()
            .setEmail("test@example.com")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<ExistsResponse> responseObserver = mock(StreamObserver.class);

        when(userService.existsByEmail("test@example.com")).thenReturn(true);

        userGrpcService.existsByEmail(request, responseObserver);

        ArgumentCaptor<ExistsResponse> captor = ArgumentCaptor.forClass(ExistsResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertTrue(captor.getValue().getExists());
    }

    @Test
    void testExistsByEmail_False() {
        ExistsByEmailRequest request = ExistsByEmailRequest.newBuilder()
            .setEmail("unknown@example.com")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<ExistsResponse> responseObserver = mock(StreamObserver.class);

        when(userService.existsByEmail("unknown@example.com")).thenReturn(false);

        userGrpcService.existsByEmail(request, responseObserver);

        ArgumentCaptor<ExistsResponse> captor = ArgumentCaptor.forClass(ExistsResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        assertFalse(captor.getValue().getExists());
    }

    @Test
    void testExistsByEmail_Exception() {
        ExistsByEmailRequest request = ExistsByEmailRequest.newBuilder()
            .setEmail("test@example.com")
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<ExistsResponse> responseObserver = mock(StreamObserver.class);

        when(userService.existsByEmail("test@example.com")).thenThrow(new RuntimeException("DB error"));

        userGrpcService.existsByEmail(request, responseObserver);

        verify(responseObserver).onError(any());
    }

    @Test
    void testGetUserResponseWithNullTimestamps() {
        User userWithNullTimestamps = User.builder()
            .id(2L)
            .username("user2")
            .email("user2@example.com")
            .firstName(null)
            .lastName(null)
            .role(UserRole.USER)
            .isActive(null)
            .isVerified(null)
            .createdAt(null)
            .updatedAt(null)
            .build();

        GetUserRequest request = GetUserRequest.newBuilder()
            .setUserId(2L)
            .build();

        @SuppressWarnings("unchecked")
        StreamObserver<UserResponse> responseObserver = mock(StreamObserver.class);

        when(userService.getUserById(2L)).thenReturn(userWithNullTimestamps);

        userGrpcService.getUser(request, responseObserver);

        ArgumentCaptor<UserResponse> captor = ArgumentCaptor.forClass(UserResponse.class);
        verify(responseObserver).onNext(captor.capture());
        verify(responseObserver).onCompleted();

        UserResponse response = captor.getValue();
        assertEquals("", response.getFirstName());
        assertEquals("", response.getLastName());
        assertEquals("", response.getCreatedAt());
        assertEquals("", response.getUpdatedAt());
        assertFalse(response.getIsActive());
        assertFalse(response.getIsVerified());
    }
}
