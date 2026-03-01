package com.arcana.cloud.exception;

import com.arcana.cloud.dto.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Additional branch coverage for GlobalExceptionHandler.
 * Covers: duplicate-field merge, ServiceUnavailableException, other AuthenticationException subtypes.
 */
@DisplayName("GlobalExceptionHandler - Additional Coverage")
class GlobalExceptionHandlerAdditionalTest {

    private GlobalExceptionHandler handler;
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        webRequest = mock(WebRequest.class);
    }

    // ─── handleMethodArgumentNotValid ────────────────────────────────────────

    @Test
    @DisplayName("handleMethodArgumentNotValid: duplicate field name → merge keeps first error")
    void handleMethodArgumentNotValid_duplicateField_keepsFirst() {
        BeanPropertyBindingResult bindingResult =
            new BeanPropertyBindingResult(new Object(), "target");
        // Two errors for the SAME field "email"
        bindingResult.addError(new FieldError("target", "email", "Email is required"));
        bindingResult.addError(new FieldError("target", "email", "Email format is invalid"));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiResponse<Map<String, String>>> response =
            handler.handleMethodArgumentNotValid(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) response.getBody().getErrors();
        assertNotNull(errors);
        // Merge function keeps the first entry
        assertEquals("Email is required", errors.get("email"));
        assertEquals(1, errors.size());
    }

    @Test
    @DisplayName("handleMethodArgumentNotValid: single field with null message → 'Validation error'")
    void handleMethodArgumentNotValid_nullMessage_usesDefault() {
        BeanPropertyBindingResult bindingResult =
            new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(
            new FieldError("target", "username", null, false, null, null, null));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiResponse<Map<String, String>>> response =
            handler.handleMethodArgumentNotValid(ex);

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) response.getBody().getErrors();
        assertEquals("Validation error", errors.get("username"));
    }

    @Test
    @DisplayName("handleMethodArgumentNotValid: multiple distinct fields → all present in map")
    void handleMethodArgumentNotValid_multipleFields() {
        BeanPropertyBindingResult bindingResult =
            new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "username", "Required"));
        bindingResult.addError(new FieldError("target", "email", "Invalid email"));
        bindingResult.addError(new FieldError("target", "password", "Too short"));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiResponse<Map<String, String>>> response =
            handler.handleMethodArgumentNotValid(ex);

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) response.getBody().getErrors();
        assertEquals(3, errors.size());
        assertEquals("Required", errors.get("username"));
        assertEquals("Invalid email", errors.get("email"));
        assertEquals("Too short", errors.get("password"));
    }

    @Test
    @DisplayName("handleMethodArgumentNotValid: empty binding result → empty errors map")
    void handleMethodArgumentNotValid_noErrors_emptyMap() {
        BeanPropertyBindingResult bindingResult =
            new BeanPropertyBindingResult(new Object(), "target");
        // No errors added

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiResponse<Map<String, String>>> response =
            handler.handleMethodArgumentNotValid(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) response.getBody().getErrors();
        assertNotNull(errors);
        assertEquals(0, errors.size());
    }

    // ─── handleGenericException ───────────────────────────────────────────────

    @Test
    @DisplayName("handleGenericException: ServiceUnavailableException → 500 internal server error")
    void handleGenericException_serviceUnavailable() {
        ServiceUnavailableException ex = new ServiceUnavailableException("Service down");
        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(ex, webRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Internal server error", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleGenericException: NullPointerException → 500 internal server error")
    void handleGenericException_nullPointer() {
        NullPointerException ex = new NullPointerException("null ref");
        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(ex, webRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    @DisplayName("handleGenericException: IllegalStateException → 500")
    void handleGenericException_illegalState() {
        IllegalStateException ex = new IllegalStateException("bad state");
        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(ex, webRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    // ─── handleAuthentication ─────────────────────────────────────────────────

    @Test
    @DisplayName("handleAuthentication: DisabledException → 401 authentication failed")
    void handleAuthentication_disabledException() {
        DisabledException ex = new DisabledException("User disabled");
        ResponseEntity<ApiResponse<Void>> response = handler.handleAuthentication(ex, webRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Authentication failed", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleAuthentication: LockedException → 401 authentication failed")
    void handleAuthentication_lockedException() {
        LockedException ex = new LockedException("Account locked");
        ResponseEntity<ApiResponse<Void>> response = handler.handleAuthentication(ex, webRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Authentication failed", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleAuthentication: InsufficientAuthenticationException → 401")
    void handleAuthentication_insufficientAuth() {
        InsufficientAuthenticationException ex =
            new InsufficientAuthenticationException("Need auth");
        ResponseEntity<ApiResponse<Void>> response = handler.handleAuthentication(ex, webRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // ─── handleBadCredentials ────────────────────────────────────────────────

    @Test
    @DisplayName("handleBadCredentials: any message → always returns 'Invalid username or password'")
    void handleBadCredentials_messageIsFixed() {
        BadCredentialsException ex = new BadCredentialsException("custom message");
        ResponseEntity<ApiResponse<Void>> response =
            handler.handleBadCredentials(ex, webRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Invalid username or password", response.getBody().getMessage());
    }

    // ─── handleResourceNotFound ───────────────────────────────────────────────

    @Test
    @DisplayName("handleResourceNotFound: string field identifier")
    void handleResourceNotFound_stringFieldId() {
        ResourceNotFoundException ex =
            new ResourceNotFoundException("User", "username", "alice");
        ResponseEntity<ApiResponse<Void>> response =
            handler.handleResourceNotFound(ex, webRequest);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertNotNull(response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleResourceNotFound: long field identifier")
    void handleResourceNotFound_longFieldId() {
        ResourceNotFoundException ex =
            new ResourceNotFoundException("Order", "id", 9999L);
        ResponseEntity<ApiResponse<Void>> response =
            handler.handleResourceNotFound(ex, webRequest);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ─── handleUnauthorized ───────────────────────────────────────────────────

    @Test
    @DisplayName("handleUnauthorized: message is propagated")
    void handleUnauthorized_messageIsPropagated() {
        UnauthorizedException ex = new UnauthorizedException("Token expired");
        ResponseEntity<ApiResponse<Void>> response =
            handler.handleUnauthorized(ex, webRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Token expired", response.getBody().getMessage());
    }

    // ─── handleValidation ────────────────────────────────────────────────────

    @Test
    @DisplayName("handleValidation: message is propagated")
    void handleValidation_messageIsPropagated() {
        ValidationException ex = new ValidationException("Field X is required");
        ResponseEntity<ApiResponse<Void>> response =
            handler.handleValidation(ex, webRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Field X is required", response.getBody().getMessage());
    }

    // ─── handleAccessDenied ──────────────────────────────────────────────────

    @Test
    @DisplayName("handleAccessDenied: always returns 'Access denied' with 403")
    void handleAccessDenied_403() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden action");
        ResponseEntity<ApiResponse<Void>> response =
            handler.handleAccessDenied(ex, webRequest);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Access denied", response.getBody().getMessage());
    }
}
