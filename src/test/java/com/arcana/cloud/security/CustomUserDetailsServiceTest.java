package com.arcana.cloud.security;

import com.arcana.cloud.entity.User;
import com.arcana.cloud.entity.UserRole;
import com.arcana.cloud.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .password("encoded_password")
            .role(UserRole.USER)
            .isActive(true)
            .isVerified(true)
            .build();
    }

    @Test
    void testLoadUserByUsername_WithUserId_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("1");

        assertNotNull(userDetails);
        assertEquals("testuser", userDetails.getUsername());
    }

    @Test
    void testLoadUserByUsername_WithUsername_Success() {
        when(userRepository.findByUsernameOrEmail("testuser", "testuser"))
            .thenReturn(Optional.of(testUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("testuser");

        assertNotNull(userDetails);
        assertEquals("testuser", userDetails.getUsername());
    }

    @Test
    void testLoadUserByUsername_WithEmail_Success() {
        when(userRepository.findByUsernameOrEmail("test@example.com", "test@example.com"))
            .thenReturn(Optional.of(testUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("test@example.com");

        assertNotNull(userDetails);
        assertEquals("testuser", userDetails.getUsername());
    }

    @Test
    void testLoadUserByUsername_WithUserId_NotFound() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
            () -> customUserDetailsService.loadUserByUsername("999"));
    }

    @Test
    void testLoadUserByUsername_WithUsername_NotFound() {
        when(userRepository.findByUsernameOrEmail(anyString(), anyString()))
            .thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
            () -> customUserDetailsService.loadUserByUsername("nonexistent"));
    }

    @Test
    void testLoadUserById_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        UserDetails userDetails = customUserDetailsService.loadUserById(1L);

        assertNotNull(userDetails);
        assertEquals("testuser", userDetails.getUsername());
    }

    @Test
    void testLoadUserById_NotFound() {
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
            () -> customUserDetailsService.loadUserById(999L));
    }

    @Test
    void testLoadUserByUsername_WithAdminUser() {
        User adminUser = User.builder()
            .id(2L)
            .username("admin")
            .email("admin@example.com")
            .password("encoded_password")
            .role(UserRole.ADMIN)
            .isActive(true)
            .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(adminUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("2");

        assertNotNull(userDetails);
        assertEquals("admin", userDetails.getUsername());
        assertNotNull(userDetails.getAuthorities());
        assertEquals(1, userDetails.getAuthorities().size());
    }
}
