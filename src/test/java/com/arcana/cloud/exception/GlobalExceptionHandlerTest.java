package com.arcana.cloud.exception;

import com.arcana.cloud.dto.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        webRequest = mock(WebRequest.class);
    }

    @Test
    void testHandleResourceNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User", "id", 1L);
        ResponseEntity<ApiResponse<Void>> response = handler.handleResourceNotFound(ex, webRequest);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertNotNull(response.getBody().getMessage());
    }

    @Test
    void testHandleUnauthorized() {
        UnauthorizedException ex = new UnauthorizedException("Access denied");
        ResponseEntity<ApiResponse<Void>> response = handler.handleUnauthorized(ex, webRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Access denied", response.getBody().getMessage());
    }

    @Test
    void testHandleValidation() {
        ValidationException ex = new ValidationException("Invalid input");
        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex, webRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Invalid input", response.getBody().getMessage());
    }

    @Test
    void testHandleMethodArgumentNotValid() throws Exception {
        BeanPropertyBindingResult bindingResult =
            new BeanPropertyBindingResult(new Object(), "testObject");
        bindingResult.addError(new FieldError("testObject", "username", "Username is required"));
        bindingResult.addError(new FieldError("testObject", "email", "Email is invalid"));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiResponse<Map<String, String>>> response =
            handler.handleMethodArgumentNotValid(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Validation failed", response.getBody().getMessage());
        assertNotNull(response.getBody().getErrors());
    }

    @Test
    void testHandleBadCredentials() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");
        ResponseEntity<ApiResponse<Void>> response = handler.handleBadCredentials(ex, webRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Invalid username or password", response.getBody().getMessage());
    }

    @Test
    void testHandleAuthentication() {
        InsufficientAuthenticationException ex =
            new InsufficientAuthenticationException("Authentication required");
        ResponseEntity<ApiResponse<Void>> response = handler.handleAuthentication(ex, webRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Authentication failed", response.getBody().getMessage());
    }

    @Test
    void testHandleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");
        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDenied(ex, webRequest);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Access denied", response.getBody().getMessage());
    }

    @Test
    void testHandleGenericException() {
        Exception ex = new RuntimeException("Unexpected error");
        ResponseEntity<ApiResponse<Void>> response =
            handler.handleGenericException(ex, webRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Internal server error", response.getBody().getMessage());
    }

    @Test
    void testHandleMethodArgumentNotValid_NullMessage() throws Exception {
        BeanPropertyBindingResult bindingResult =
            new BeanPropertyBindingResult(new Object(), "testObject");
        bindingResult.addError(new FieldError("testObject", "field", null, false,
            null, null, null));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiResponse<Map<String, String>>> response =
            handler.handleMethodArgumentNotValid(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) response.getBody().getErrors();
        assertNotNull(errors);
        assertEquals("Validation error", errors.get("field"));
    }
}
