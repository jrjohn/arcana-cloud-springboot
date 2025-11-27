package com.arcana.cloud.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * Test security configuration that provides mock services for testing.
 * Used in integration tests to allow unauthenticated access.
 *
 * Note: Tests should also use @MockBean for JwtAuthenticationFilter
 * to bypass the JWT validation in the security filter chain.
 */
@TestConfiguration
public class TestSecurityConfig {

    /**
     * Provide a test UserDetailsService.
     */
    @Bean
    @Primary
    public UserDetailsService testUserDetailsService() {
        return username -> User.builder()
            .username(username)
            .password("password")
            .authorities("ROLE_ADMIN", "ROLE_USER")
            .build();
    }
}
