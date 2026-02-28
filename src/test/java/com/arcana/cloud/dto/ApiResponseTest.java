package com.arcana.cloud.dto;

import com.arcana.cloud.dto.response.ApiResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseTest {

    @Test
    void testSuccess_WithData() {
        ApiResponse<String> response = ApiResponse.success("test data");

        assertTrue(response.isSuccess());
        assertEquals("test data", response.getData());
        assertNull(response.getMessage());
        assertNull(response.getErrors());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void testSuccess_WithDataAndMessage() {
        ApiResponse<String> response = ApiResponse.success("data", "Operation successful");

        assertTrue(response.isSuccess());
        assertEquals("data", response.getData());
        assertEquals("Operation successful", response.getMessage());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void testSuccess_WithNullData() {
        ApiResponse<Void> response = ApiResponse.success(null);

        assertTrue(response.isSuccess());
        assertNull(response.getData());
    }

    @Test
    void testError_WithMessage() {
        ApiResponse<Void> response = ApiResponse.error("Something went wrong");

        assertFalse(response.isSuccess());
        assertEquals("Something went wrong", response.getMessage());
        assertNull(response.getData());
        assertNull(response.getErrors());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void testError_WithMessageAndErrors() {
        Map<String, String> errors = Map.of("field1", "error1", "field2", "error2");
        ApiResponse<Map<String, String>> response = ApiResponse.error("Validation failed", errors);

        assertFalse(response.isSuccess());
        assertEquals("Validation failed", response.getMessage());
        assertNotNull(response.getErrors());
    }

    @Test
    void testBuilder_DefaultTimestamp() {
        ApiResponse<String> response1 = ApiResponse.success("data");
        LocalDateTime before = LocalDateTime.now();

        assertNotNull(response1.getTimestamp());
        assertTrue(response1.getTimestamp().isBefore(before) || response1.getTimestamp().equals(before));
    }

    @Test
    void testBuilder_ManualTimestamp() {
        LocalDateTime customTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        ApiResponse<String> response = ApiResponse.<String>builder()
            .success(true)
            .data("data")
            .timestamp(customTime)
            .build();

        assertEquals(customTime, response.getTimestamp());
    }

    @Test
    void testBuilderPattern_AllFields() {
        ApiResponse<String> response = ApiResponse.<String>builder()
            .success(false)
            .message("Error")
            .data("data")
            .errors(Map.of("field", "error"))
            .build();

        assertFalse(response.isSuccess());
        assertEquals("Error", response.getMessage());
        assertEquals("data", response.getData());
        assertNotNull(response.getErrors());
    }
}
