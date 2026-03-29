package com.arcana.cloud.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ServiceUnavailableExceptionTest {

    @Test
    void testMessageConstructor() {
        ServiceUnavailableException ex = new ServiceUnavailableException("Service is down");
        assertEquals("Service is down", ex.getMessage());
    }

    @Test
    void testMessageAndCauseConstructor() {
        Throwable cause = new RuntimeException("connection refused");
        ServiceUnavailableException ex =
            new ServiceUnavailableException("Upstream unavailable", cause);

        assertEquals("Upstream unavailable", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertNotNull(ex.getCause());
    }

    @Test
    void testIsRuntimeException() {
        ServiceUnavailableException ex = new ServiceUnavailableException("test");
        assertNotNull(ex);
        // Verify it's a RuntimeException (no checked exception propagation needed)
        assertEquals(RuntimeException.class, ex.getClass().getSuperclass());
    }
}
