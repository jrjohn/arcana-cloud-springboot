package com.arcana.cloud.controller.internal;

import com.arcana.cloud.dto.response.ApiResponse;
import com.arcana.cloud.dto.response.PagedResponse;
import com.arcana.cloud.dto.response.UserResponse;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.exception.ResourceNotFoundException;
import com.arcana.cloud.mapper.UserMapper;
import com.arcana.cloud.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalUserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private InternalUserController internalUserController;

    private User testUser;
    private UserResponse testUserResponse;

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
            .build();

        testUserResponse = UserResponse.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .firstName("Test")
            .lastName("User")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(false)
            .build();
    }

    @Test
    void testGetUser_Success() {
        when(userService.getUserById(1L)).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

        ResponseEntity<ApiResponse<UserResponse>> response = internalUserController.getUser(1L);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("testuser", response.getBody().getData().getUsername());
    }

    @Test
    void testGetUserByUsername_Found() {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

        ResponseEntity<ApiResponse<UserResponse>> response =
            internalUserController.getUserByUsername("testuser");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("testuser", response.getBody().getData().getUsername());
    }

    @Test
    void testGetUserByUsername_NotFound() {
        when(userService.findByUsername("unknown")).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<UserResponse>> response =
            internalUserController.getUserByUsername("unknown");

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("User not found", response.getBody().getMessage());
    }

    @Test
    void testGetUserByEmail_Found() {
        when(userService.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

        ResponseEntity<ApiResponse<UserResponse>> response =
            internalUserController.getUserByEmail("test@example.com");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("test@example.com", response.getBody().getData().getEmail());
    }

    @Test
    void testGetUserByEmail_NotFound() {
        when(userService.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<UserResponse>> response =
            internalUserController.getUserByEmail("unknown@example.com");

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void testListUsers_Success() {
        Page<User> usersPage = new PageImpl<>(List.of(testUser), PageRequest.of(0, 10), 1);
        when(userService.getUsers(0, 10)).thenReturn(usersPage);
        when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

        ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> response =
            internalUserController.listUsers(0, 10);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());

        PagedResponse<UserResponse> pagedResponse = response.getBody().getData();
        assertEquals(1, pagedResponse.getContent().size());
        assertEquals(1L, pagedResponse.getTotalElements());
        assertEquals(0, pagedResponse.getPage());
        assertEquals(10, pagedResponse.getSize());
    }

    @Test
    void testListUsers_Empty() {
        Page<User> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(userService.getUsers(0, 10)).thenReturn(emptyPage);

        ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> response =
            internalUserController.listUsers(0, 10);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals(0, response.getBody().getData().getContent().size());
        assertEquals(0L, response.getBody().getData().getTotalElements());
    }

    @Test
    void testCreateUser_Success() {
        InternalUserController.UserCreateDto request = new InternalUserController.UserCreateDto(
            "newuser", "new@example.com", "password", "New", "User"
        );

        when(userService.createUser(any(User.class))).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

        ResponseEntity<ApiResponse<UserResponse>> response = internalUserController.createUser(request);

        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("User created", response.getBody().getMessage());
        verify(userService).createUser(any(User.class));
    }

    @Test
    void testUpdateUser_Success() {
        InternalUserController.UserUpdateDto request = new InternalUserController.UserUpdateDto();
        request.setUsername("updateduser");
        request.setEmail("updated@example.com");
        request.setFirstName("Updated");
        request.setLastName("User");
        request.setIsActive(true);
        request.setIsVerified(true);

        when(userService.updateUser(anyLong(), any(User.class))).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

        ResponseEntity<ApiResponse<UserResponse>> response =
            internalUserController.updateUser(1L, request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("User updated", response.getBody().getMessage());
        verify(userService).updateUser(anyLong(), any(User.class));
    }

    @Test
    void testDeleteUser_Success() {
        doNothing().when(userService).deleteUser(1L);

        ResponseEntity<ApiResponse<Void>> response = internalUserController.deleteUser(1L);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("User deleted", response.getBody().getMessage());
        assertNull(response.getBody().getData());
        verify(userService).deleteUser(1L);
    }

    @Test
    void testExistsByUsername_True() {
        when(userService.existsByUsername("testuser")).thenReturn(true);

        ResponseEntity<ApiResponse<InternalUserController.ExistsResponse>> response =
            internalUserController.existsByUsername("testuser");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertTrue(response.getBody().getData().isExists());
    }

    @Test
    void testExistsByUsername_False() {
        when(userService.existsByUsername("unknown")).thenReturn(false);

        ResponseEntity<ApiResponse<InternalUserController.ExistsResponse>> response =
            internalUserController.existsByUsername("unknown");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody().getData().isExists());
    }

    @Test
    void testExistsByEmail_True() {
        when(userService.existsByEmail("test@example.com")).thenReturn(true);

        ResponseEntity<ApiResponse<InternalUserController.ExistsResponse>> response =
            internalUserController.existsByEmail("test@example.com");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getData().isExists());
    }

    @Test
    void testExistsByEmail_False() {
        when(userService.existsByEmail("unknown@example.com")).thenReturn(false);

        ResponseEntity<ApiResponse<InternalUserController.ExistsResponse>> response =
            internalUserController.existsByEmail("unknown@example.com");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody().getData().isExists());
    }

    @Test
    void testListUsers_WithPagination() {
        Page<User> usersPage = new PageImpl<>(List.of(testUser), PageRequest.of(2, 5), 15);
        when(userService.getUsers(2, 5)).thenReturn(usersPage);
        when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

        ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> response =
            internalUserController.listUsers(2, 5);

        assertNotNull(response);
        PagedResponse<UserResponse> pagedResponse = response.getBody().getData();
        assertEquals(2, pagedResponse.getPage());
        assertEquals(5, pagedResponse.getSize());
        assertEquals(15L, pagedResponse.getTotalElements());
    }

    @Test
    void testGetUser_ResponseMessage() {
        when(userService.getUserById(1L)).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

        ResponseEntity<ApiResponse<UserResponse>> response = internalUserController.getUser(1L);
        assertEquals("User retrieved", response.getBody().getMessage());
    }

    @Test
    void testGetUserByUsername_FoundMessage() {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(testUserResponse);

        ResponseEntity<ApiResponse<UserResponse>> response =
            internalUserController.getUserByUsername("testuser");
        assertEquals("User found", response.getBody().getMessage());
    }

    @Test
    void testListUsers_ResponseMessage() {
        Page<User> usersPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(userService.getUsers(0, 10)).thenReturn(usersPage);

        ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> response =
            internalUserController.listUsers(0, 10);
        assertEquals("Users retrieved", response.getBody().getMessage());
    }
}
