package com.arcana.cloud.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExceptionClassesTest {

    @Test
    void testResourceNotFoundException_WithAllArgs() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User", "id", 1L);
        assertNotNull(ex.getMessage());
        assertEquals("User", ex.getResourceName());
        assertEquals("id", ex.getFieldName());
        assertEquals(1L, ex.getFieldValue());
        assertEquals("User not found with id: '1'", ex.getMessage());
    }

    @Test
    void testResourceNotFoundException_WithStringFieldValue() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User", "username", "testuser");
        assertEquals("User not found with username: 'testuser'", ex.getMessage());
        assertEquals("testuser", ex.getFieldValue());
    }

    @Test
    void testUnauthorizedException_WithMessage() {
        UnauthorizedException ex = new UnauthorizedException("Unauthorized access");
        assertEquals("Unauthorized access", ex.getMessage());
        assertNotNull(ex);
    }

    @Test
    void testUnauthorizedException_WithMessageAndCause() {
        Throwable cause = new RuntimeException("original cause");
        UnauthorizedException ex = new UnauthorizedException("Unauthorized", cause);
        assertEquals("Unauthorized", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void testValidationException_WithMessage() {
        ValidationException ex = new ValidationException("Validation failed");
        assertEquals("Validation failed", ex.getMessage());
        assertNotNull(ex);
    }

    @Test
    void testValidationException_WithMessageAndCause() {
        Throwable cause = new IllegalArgumentException("bad arg");
        ValidationException ex = new ValidationException("Validation error", cause);
        assertEquals("Validation error", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void testServiceUnavailableException_WithMessage() {
        ServiceUnavailableException ex = new ServiceUnavailableException("Service unavailable");
        assertEquals("Service unavailable", ex.getMessage());
        assertNotNull(ex);
    }

    @Test
    void testServiceUnavailableException_WithMessageAndCause() {
        Throwable cause = new RuntimeException("connection refused");
        ServiceUnavailableException ex = new ServiceUnavailableException("Unavailable", cause);
        assertEquals("Unavailable", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void testExceptions_AreRuntimeExceptions() {
        assertIsRuntimeException(new ResourceNotFoundException("R", "f", "v"));
        assertIsRuntimeException(new UnauthorizedException("msg"));
        assertIsRuntimeException(new ValidationException("msg"));
        assertIsRuntimeException(new ServiceUnavailableException("msg"));
    }

    private void assertIsRuntimeException(Exception ex) {
        assertNotNull(ex);
        // All custom exceptions should extend RuntimeException
        assertEquals(RuntimeException.class, ex.getClass().getSuperclass());
    }
}
