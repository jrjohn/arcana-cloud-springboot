package com.arcana.cloud.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRoleTest {

    @Test
    void testUserRoleValues() {
        UserRole[] roles = UserRole.values();

        assertEquals(3, roles.length);
        assertEquals(UserRole.USER, roles[0]);
        assertEquals(UserRole.ADMIN, roles[1]);
        assertEquals(UserRole.MODERATOR, roles[2]);
    }

    @Test
    void testUserRoleValueOf() {
        assertEquals(UserRole.USER, UserRole.valueOf("USER"));
        assertEquals(UserRole.ADMIN, UserRole.valueOf("ADMIN"));
    }

    @Test
    void testUserRoleName() {
        assertEquals("USER", UserRole.USER.name());
        assertEquals("ADMIN", UserRole.ADMIN.name());
    }

    @Test
    void testUserRoleOrdinal() {
        assertEquals(0, UserRole.USER.ordinal());
        assertEquals(1, UserRole.ADMIN.ordinal());
        assertEquals(2, UserRole.MODERATOR.ordinal());
    }

    @Test
    void testUserRoleToString() {
        assertNotNull(UserRole.USER.toString());
        assertNotNull(UserRole.ADMIN.toString());
        assertEquals("USER", UserRole.USER.toString());
        assertEquals("ADMIN", UserRole.ADMIN.toString());
    }

    @Test
    void testUserRoleEquality() {
        UserRole role1 = UserRole.USER;
        UserRole role2 = UserRole.USER;

        assertEquals(role1, role2);
        assertTrue(role1 == role2);
    }

    @Test
    void testUserRoleWithUser() {
        User user = User.builder()
            .username("testuser")
            .email("test@example.com")
            .password("password")
            .role(UserRole.USER)
            .build();

        assertEquals(UserRole.USER, user.getRole());
    }

    @Test
    void testUserRoleWithAdmin() {
        User admin = User.builder()
            .username("admin")
            .email("admin@example.com")
            .password("password")
            .role(UserRole.ADMIN)
            .build();

        assertEquals(UserRole.ADMIN, admin.getRole());
    }
}
