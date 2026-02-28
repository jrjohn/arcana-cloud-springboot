package com.arcana.cloud.security;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityServiceTest {

    private SecurityService securityService;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        securityService = new SecurityService();

        User user = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .password("encoded_password")
            .role(UserRole.USER)
            .isActive(true)
            .build();

        userPrincipal = UserPrincipal.create(user);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testIsOwner_WhenAuthenticated_AndOwner_ReturnsTrue() {
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertTrue(securityService.isOwner(1L));
    }

    @Test
    void testIsOwner_WhenAuthenticated_AndNotOwner_ReturnsFalse() {
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertFalse(securityService.isOwner(999L));
    }

    @Test
    void testIsOwner_WhenNotAuthenticated_ReturnsFalse() {
        SecurityContextHolder.clearContext();
        assertFalse(securityService.isOwner(1L));
    }

    @Test
    void testIsOwner_WhenPrincipalIsNotUserPrincipal_ReturnsFalse() {
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken("anonymousUser", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertFalse(securityService.isOwner(1L));
    }

    @Test
    void testGetCurrentUserId_WhenAuthenticated_ReturnsId() {
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        Long userId = securityService.getCurrentUserId();

        assertEquals(1L, userId);
    }

    @Test
    void testGetCurrentUserId_WhenNotAuthenticated_ReturnsNull() {
        SecurityContextHolder.clearContext();
        assertNull(securityService.getCurrentUserId());
    }

    @Test
    void testGetCurrentUserId_WhenPrincipalIsNotUserPrincipal_ReturnsNull() {
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken("anonymousUser", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertNull(securityService.getCurrentUserId());
    }

    @Test
    void testGetCurrentUser_WhenAuthenticated_ReturnsUserPrincipal() {
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        UserPrincipal result = securityService.getCurrentUser();

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("testuser", result.getUsername());
    }

    @Test
    void testGetCurrentUser_WhenNotAuthenticated_ReturnsNull() {
        SecurityContextHolder.clearContext();
        assertNull(securityService.getCurrentUser());
    }

    @Test
    void testGetCurrentUser_WhenPrincipalIsNotUserPrincipal_ReturnsNull() {
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken("anonymousUser", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertNull(securityService.getCurrentUser());
    }
}
