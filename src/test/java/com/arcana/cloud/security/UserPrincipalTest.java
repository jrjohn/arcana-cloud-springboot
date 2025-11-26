package com.arcana.cloud.security;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPrincipalTest {

    private User user;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        user = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .password("encoded_password")
            .role(UserRole.USER)
            .isActive(true)
            .build();

        userPrincipal = UserPrincipal.create(user);
    }

    @Test
    void testCreateFromUser() {
        assertNotNull(userPrincipal);
        assertEquals(1L, userPrincipal.getId());
        assertEquals("testuser", userPrincipal.getUsername());
        assertEquals("test@example.com", userPrincipal.getEmail());
        assertEquals("encoded_password", userPrincipal.getPassword());
        assertEquals(UserRole.USER, userPrincipal.getRole());
        assertTrue(userPrincipal.getIsActive());
    }

    @Test
    void testGetAuthorities_UserRole() {
        Collection<? extends GrantedAuthority> authorities = userPrincipal.getAuthorities();

        assertNotNull(authorities);
        assertEquals(1, authorities.size());
        assertTrue(authorities.stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void testGetAuthorities_AdminRole() {
        User adminUser = User.builder()
            .id(2L)
            .username("admin")
            .email("admin@example.com")
            .password("encoded_password")
            .role(UserRole.ADMIN)
            .isActive(true)
            .build();

        UserPrincipal adminPrincipal = UserPrincipal.create(adminUser);
        Collection<? extends GrantedAuthority> authorities = adminPrincipal.getAuthorities();

        assertNotNull(authorities);
        assertEquals(1, authorities.size());
        assertTrue(authorities.stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void testIsEnabled_ActiveUser() {
        assertTrue(userPrincipal.isEnabled());
    }

    @Test
    void testIsEnabled_InactiveUser() {
        User inactiveUser = User.builder()
            .id(3L)
            .username("inactive")
            .email("inactive@example.com")
            .password("encoded_password")
            .role(UserRole.USER)
            .isActive(false)
            .build();

        UserPrincipal inactivePrincipal = UserPrincipal.create(inactiveUser);

        assertFalse(inactivePrincipal.isEnabled());
    }

    @Test
    void testIsEnabled_NullIsActive() {
        User nullActiveUser = User.builder()
            .id(4L)
            .username("nullactive")
            .email("nullactive@example.com")
            .password("encoded_password")
            .role(UserRole.USER)
            .isActive(null)
            .build();

        UserPrincipal nullActivePrincipal = UserPrincipal.create(nullActiveUser);

        assertFalse(nullActivePrincipal.isEnabled());
    }

    @Test
    void testIsAccountNonExpired() {
        assertTrue(userPrincipal.isAccountNonExpired());
    }

    @Test
    void testIsAccountNonLocked() {
        assertTrue(userPrincipal.isAccountNonLocked());
    }

    @Test
    void testIsCredentialsNonExpired() {
        assertTrue(userPrincipal.isCredentialsNonExpired());
    }

    @Test
    void testBuilder() {
        UserPrincipal builtPrincipal = UserPrincipal.builder()
            .id(5L)
            .username("builtuser")
            .email("built@example.com")
            .password("password")
            .role(UserRole.USER)
            .isActive(true)
            .build();

        assertNotNull(builtPrincipal);
        assertEquals(5L, builtPrincipal.getId());
        assertEquals("builtuser", builtPrincipal.getUsername());
    }

    @Test
    void testNoArgsConstructor() {
        UserPrincipal emptyPrincipal = new UserPrincipal();

        assertNotNull(emptyPrincipal);
    }

    @Test
    void testSetters() {
        userPrincipal.setId(10L);
        userPrincipal.setUsername("newusername");
        userPrincipal.setEmail("new@example.com");
        userPrincipal.setPassword("newpassword");
        userPrincipal.setRole(UserRole.ADMIN);
        userPrincipal.setIsActive(false);

        assertEquals(10L, userPrincipal.getId());
        assertEquals("newusername", userPrincipal.getUsername());
        assertEquals("new@example.com", userPrincipal.getEmail());
        assertEquals("newpassword", userPrincipal.getPassword());
        assertEquals(UserRole.ADMIN, userPrincipal.getRole());
        assertFalse(userPrincipal.getIsActive());
    }

    @Test
    void testEquality() {
        User sameUser = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .password("encoded_password")
            .role(UserRole.USER)
            .isActive(true)
            .build();

        UserPrincipal samePrincipal = UserPrincipal.create(sameUser);

        assertEquals(userPrincipal, samePrincipal);
    }

    @Test
    void testToString() {
        String principalString = userPrincipal.toString();

        assertNotNull(principalString);
        assertTrue(principalString.contains("testuser"));
        assertTrue(principalString.contains("test@example.com"));
    }

    @Test
    void testUserDetailsInterface() {
        // UserPrincipal should implement UserDetails
        assertTrue(org.springframework.security.core.userdetails.UserDetails.class
            .isAssignableFrom(UserPrincipal.class));
    }
}
