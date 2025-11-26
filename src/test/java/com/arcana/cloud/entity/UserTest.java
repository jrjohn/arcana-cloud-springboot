package com.arcana.cloud.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .password("encoded_password")
            .firstName("Test")
            .lastName("User")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(false)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    @Test
    void testUserBuilder() {
        assertNotNull(user);
        assertEquals(1L, user.getId());
        assertEquals("testuser", user.getUsername());
        assertEquals("test@example.com", user.getEmail());
        assertEquals("encoded_password", user.getPassword());
        assertEquals("Test", user.getFirstName());
        assertEquals("User", user.getLastName());
        assertEquals(UserRole.USER, user.getRole());
        assertTrue(user.getIsActive());
        assertFalse(user.getIsVerified());
    }

    @Test
    void testUserDefaultValues() {
        User defaultUser = User.builder()
            .username("default")
            .email("default@example.com")
            .password("password")
            .build();

        assertEquals(UserRole.USER, defaultUser.getRole());
        assertTrue(defaultUser.getIsActive());
        assertFalse(defaultUser.getIsVerified());
    }

    @Test
    void testUserWithNullOptionalFields() {
        User minimalUser = User.builder()
            .username("minimal")
            .email("minimal@example.com")
            .password("password")
            .build();

        assertNull(minimalUser.getId());
        assertNull(minimalUser.getFirstName());
        assertNull(minimalUser.getLastName());
        assertNull(minimalUser.getCreatedAt());
        assertNull(minimalUser.getUpdatedAt());
    }

    @Test
    void testUserRoleAdmin() {
        User adminUser = User.builder()
            .username("admin")
            .email("admin@example.com")
            .password("password")
            .role(UserRole.ADMIN)
            .build();

        assertEquals(UserRole.ADMIN, adminUser.getRole());
    }

    @Test
    void testUserSetters() {
        user.setUsername("updateduser");
        user.setEmail("updated@example.com");
        user.setFirstName("Updated");
        user.setLastName("Name");
        user.setIsActive(false);
        user.setIsVerified(true);

        assertEquals("updateduser", user.getUsername());
        assertEquals("updated@example.com", user.getEmail());
        assertEquals("Updated", user.getFirstName());
        assertEquals("Name", user.getLastName());
        assertFalse(user.getIsActive());
        assertTrue(user.getIsVerified());
    }

    @Test
    void testUserEquality() {
        User user1 = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .password("password")
            .build();

        User user2 = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .password("password")
            .build();

        assertEquals(user1, user2);
        assertEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    void testUserInequality() {
        User user1 = User.builder()
            .id(1L)
            .username("testuser1")
            .email("test1@example.com")
            .password("password")
            .build();

        User user2 = User.builder()
            .id(2L)
            .username("testuser2")
            .email("test2@example.com")
            .password("password")
            .build();

        assertNotEquals(user1, user2);
    }

    @Test
    void testUserToString() {
        String userString = user.toString();

        assertTrue(userString.contains("testuser"));
        assertTrue(userString.contains("test@example.com"));
        assertTrue(userString.contains("Test"));
        assertTrue(userString.contains("User"));
    }

    @Test
    void testUserNoArgsConstructor() {
        User emptyUser = new User();

        assertNull(emptyUser.getId());
        assertNull(emptyUser.getUsername());
        assertNull(emptyUser.getEmail());
        assertNull(emptyUser.getPassword());
    }

    @Test
    void testUserAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        User fullUser = new User(
            1L, "fulluser", "full@example.com", "password",
            "Full", "User", UserRole.ADMIN, true, true, now, now
        );

        assertEquals(1L, fullUser.getId());
        assertEquals("fulluser", fullUser.getUsername());
        assertEquals("full@example.com", fullUser.getEmail());
        assertEquals(UserRole.ADMIN, fullUser.getRole());
        assertTrue(fullUser.getIsVerified());
    }

    @Test
    void testUserSerialization() {
        // User implements Serializable
        assertTrue(java.io.Serializable.class.isAssignableFrom(User.class));
    }
}
