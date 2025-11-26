package com.arcana.cloud.mapper;

import com.arcana.cloud.dto.request.UserCreateRequest;
import com.arcana.cloud.dto.request.UserUpdateRequest;
import com.arcana.cloud.dto.response.UserResponse;
import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles("test")
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void testToResponse() {
        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .password("encoded_password")
            .firstName("Test")
            .lastName("User")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(false)
            .createdAt(now)
            .updatedAt(now)
            .build();

        UserResponse response = userMapper.toResponse(user);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("testuser", response.getUsername());
        assertEquals("test@example.com", response.getEmail());
        assertEquals("Test", response.getFirstName());
        assertEquals("User", response.getLastName());
        assertEquals(UserRole.USER, response.getRole());
        assertEquals(true, response.getIsActive());
        assertEquals(false, response.getIsVerified());
        assertEquals(now, response.getCreatedAt());
        assertEquals(now, response.getUpdatedAt());
    }

    @Test
    void testToResponse_AdminUser() {
        User adminUser = User.builder()
            .id(2L)
            .username("admin")
            .email("admin@example.com")
            .password("encoded_password")
            .role(UserRole.ADMIN)
            .isActive(true)
            .isVerified(true)
            .build();

        UserResponse response = userMapper.toResponse(adminUser);

        assertNotNull(response);
        assertEquals(UserRole.ADMIN, response.getRole());
        assertEquals(true, response.getIsVerified());
    }

    @Test
    void testToResponse_NullFields() {
        User userWithNulls = User.builder()
            .id(3L)
            .username("minimal")
            .email("minimal@example.com")
            .password("password")
            .role(UserRole.USER)
            .build();

        UserResponse response = userMapper.toResponse(userWithNulls);

        assertNotNull(response);
        assertNull(response.getFirstName());
        assertNull(response.getLastName());
        assertNull(response.getCreatedAt());
        assertNull(response.getUpdatedAt());
    }

    @Test
    void testFromCreateRequest() {
        UserCreateRequest request = UserCreateRequest.builder()
            .username("newuser")
            .email("new@example.com")
            .password("Password123")
            .firstName("New")
            .lastName("User")
            .build();

        User user = userMapper.fromCreateRequest(request);

        assertNotNull(user);
        assertEquals("newuser", user.getUsername());
        assertEquals("new@example.com", user.getEmail());
        assertEquals("Password123", user.getPassword());
        assertEquals("New", user.getFirstName());
        assertEquals("User", user.getLastName());
        // ID should be null (ignored by mapper)
        assertNull(user.getId());
        // Role, isActive, isVerified may have default values from @Builder.Default
    }

    @Test
    void testFromCreateRequest_MinimalFields() {
        UserCreateRequest request = UserCreateRequest.builder()
            .username("minimaluser")
            .email("minimal@example.com")
            .password("Password123")
            .build();

        User user = userMapper.fromCreateRequest(request);

        assertNotNull(user);
        assertEquals("minimaluser", user.getUsername());
        assertNull(user.getFirstName());
        assertNull(user.getLastName());
    }

    @Test
    void testFromUpdateRequest() {
        UserUpdateRequest request = UserUpdateRequest.builder()
            .username("updateduser")
            .email("updated@example.com")
            .firstName("Updated")
            .lastName("Name")
            .isActive(false)
            .isVerified(true)
            .build();

        User user = userMapper.fromUpdateRequest(request);

        assertNotNull(user);
        assertEquals("updateduser", user.getUsername());
        assertEquals("updated@example.com", user.getEmail());
        assertEquals("Updated", user.getFirstName());
        assertEquals("Name", user.getLastName());
        // ID should be null (ignored by mapper)
        assertNull(user.getId());
        // Note: isActive/isVerified behavior depends on mapping
    }

    @Test
    void testUpdateUserFromRequest() {
        User existingUser = User.builder()
            .id(1L)
            .username("existinguser")
            .email("existing@example.com")
            .password("encoded_password")
            .firstName("Existing")
            .lastName("User")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(false)
            .createdAt(LocalDateTime.now().minusDays(1))
            .build();

        UserUpdateRequest request = UserUpdateRequest.builder()
            .firstName("Updated")
            .lastName("Name")
            .build();

        userMapper.updateUserFromRequest(request, existingUser);

        // Updated fields
        assertEquals("Updated", existingUser.getFirstName());
        assertEquals("Name", existingUser.getLastName());
        // Preserved fields
        assertEquals(1L, existingUser.getId());
        assertEquals("existinguser", existingUser.getUsername());
        assertEquals("existing@example.com", existingUser.getEmail());
        assertEquals(UserRole.USER, existingUser.getRole());
        assertEquals(true, existingUser.getIsActive());
    }

    @Test
    void testUpdateUserFromRequest_PartialUpdate() {
        User existingUser = User.builder()
            .id(1L)
            .username("existinguser")
            .email("existing@example.com")
            .firstName("Original")
            .lastName("User")
            .build();

        UserUpdateRequest request = UserUpdateRequest.builder()
            .firstName("Updated")
            // lastName not set - should remain unchanged due to IGNORE null value strategy
            .build();

        userMapper.updateUserFromRequest(request, existingUser);

        assertEquals("Updated", existingUser.getFirstName());
        assertEquals("User", existingUser.getLastName()); // Should remain unchanged
    }

    @Test
    void testUpdateUserFromRequest_AllFields() {
        User existingUser = User.builder()
            .id(1L)
            .username("originaluser")
            .email("original@example.com")
            .firstName("Original")
            .lastName("User")
            .isActive(true)
            .isVerified(false)
            .build();

        UserUpdateRequest request = UserUpdateRequest.builder()
            .username("newusername")
            .email("new@example.com")
            .firstName("New")
            .lastName("Name")
            .isActive(false)
            .isVerified(true)
            .build();

        userMapper.updateUserFromRequest(request, existingUser);

        assertEquals("newusername", existingUser.getUsername());
        assertEquals("new@example.com", existingUser.getEmail());
        assertEquals("New", existingUser.getFirstName());
        assertEquals("Name", existingUser.getLastName());
        // ID should not be modified
        assertEquals(1L, existingUser.getId());
    }

    @Test
    void testToResponse_PasswordNotExposed() {
        User user = User.builder()
            .id(1L)
            .username("secureuser")
            .email("secure@example.com")
            .password("super_secret_password")
            .role(UserRole.USER)
            .build();

        UserResponse response = userMapper.toResponse(user);

        assertNotNull(response);
        // UserResponse should not contain password field
        // This is enforced by the DTO structure, not the mapper
    }
}
