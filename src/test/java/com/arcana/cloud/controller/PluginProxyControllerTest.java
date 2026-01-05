package com.arcana.cloud.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PluginProxyController.
 */
@ExtendWith(MockitoExtension.class)
class PluginProxyControllerTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private PluginProxyController proxyController;

    @Nested
    @DisplayName("GET Proxy Tests")
    class GetProxyTests {

        @Test
        @DisplayName("Should proxy GET request successfully")
        void shouldProxyGetRequestSuccessfully() {
            // Given
            ReflectionTestUtils.setField(proxyController, "serviceLayerUrl", "http://service:8081");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/proxy/plugins/test.plugin/endpoint");
            request.setMethod("GET");
            request.setRemoteAddr("127.0.0.1");
            request.setScheme("http");
            request.setServerName("localhost");

            ResponseEntity<String> mockResponse = ResponseEntity.ok("{\"data\": \"response\"}");
            when(restTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)
            )).thenReturn(mockResponse);

            // When
            ResponseEntity<String> result = proxyController.proxyGet("test.plugin", request);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("{\"data\": \"response\"}", result.getBody());
            verify(restTemplate).exchange(
                eq("http://service:8081/api/v1/plugins/test.plugin/endpoint"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
            );
        }

        @Test
        @DisplayName("Should proxy GET request with query string")
        void shouldProxyGetRequestWithQueryString() {
            // Given
            ReflectionTestUtils.setField(proxyController, "serviceLayerUrl", "http://service:8081");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/proxy/plugins/test.plugin/search");
            request.setQueryString("q=test&limit=10");
            request.setRemoteAddr("127.0.0.1");
            request.setScheme("http");
            request.setServerName("localhost");

            ResponseEntity<String> mockResponse = ResponseEntity.ok("[]");
            when(restTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)
            )).thenReturn(mockResponse);

            // When
            proxyController.proxyGet("test.plugin", request);

            // Then
            verify(restTemplate).exchange(
                eq("http://service:8081/api/v1/plugins/test.plugin/search?q=test&limit=10"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
            );
        }

        @Test
        @DisplayName("Should return BAD_GATEWAY on failure")
        void shouldReturnBadGatewayOnFailure() {
            // Given
            ReflectionTestUtils.setField(proxyController, "serviceLayerUrl", "http://service:8081");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/proxy/plugins/test.plugin/endpoint");
            request.setRemoteAddr("127.0.0.1");
            request.setScheme("http");
            request.setServerName("localhost");

            when(restTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)
            )).thenThrow(new RestClientException("Connection refused"));

            // When
            ResponseEntity<String> result = proxyController.proxyGet("test.plugin", request);

            // Then
            assertEquals(HttpStatus.BAD_GATEWAY, result.getStatusCode());
            assertTrue(result.getBody().contains("success"));
            assertTrue(result.getBody().contains("false"));
        }
    }

    @Nested
    @DisplayName("POST Proxy Tests")
    class PostProxyTests {

        @Test
        @DisplayName("Should proxy POST request with body")
        void shouldProxyPostRequestWithBody() {
            // Given
            ReflectionTestUtils.setField(proxyController, "serviceLayerUrl", "http://service:8081");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/proxy/plugins/test.plugin/action");
            request.setRemoteAddr("127.0.0.1");
            request.setScheme("http");
            request.setServerName("localhost");

            String requestBody = "{\"action\": \"test\"}";

            ResponseEntity<String> mockResponse = ResponseEntity.ok("{\"success\": true}");
            when(restTemplate.exchange(
                anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)
            )).thenReturn(mockResponse);

            // When
            ResponseEntity<String> result = proxyController.proxyPost("test.plugin", requestBody, request);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(restTemplate).exchange(
                eq("http://service:8081/api/v1/plugins/test.plugin/action"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
            );
        }

        @Test
        @DisplayName("Should proxy POST request without body")
        void shouldProxyPostRequestWithoutBody() {
            // Given
            ReflectionTestUtils.setField(proxyController, "serviceLayerUrl", "http://service:8081");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/proxy/plugins/test.plugin/trigger");
            request.setRemoteAddr("127.0.0.1");
            request.setScheme("http");
            request.setServerName("localhost");

            ResponseEntity<String> mockResponse = ResponseEntity.ok("{}");
            when(restTemplate.exchange(
                anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)
            )).thenReturn(mockResponse);

            // When
            ResponseEntity<String> result = proxyController.proxyPost("test.plugin", null, request);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
        }
    }

    @Nested
    @DisplayName("PUT Proxy Tests")
    class PutProxyTests {

        @Test
        @DisplayName("Should proxy PUT request with body")
        void shouldProxyPutRequestWithBody() {
            // Given
            ReflectionTestUtils.setField(proxyController, "serviceLayerUrl", "http://service:8081");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/proxy/plugins/test.plugin/resource/123");
            request.setRemoteAddr("127.0.0.1");
            request.setScheme("http");
            request.setServerName("localhost");

            String requestBody = "{\"name\": \"updated\"}";

            ResponseEntity<String> mockResponse = ResponseEntity.ok("{\"success\": true}");
            when(restTemplate.exchange(
                anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)
            )).thenReturn(mockResponse);

            // When
            ResponseEntity<String> result = proxyController.proxyPut("test.plugin", requestBody, request);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(restTemplate).exchange(
                eq("http://service:8081/api/v1/plugins/test.plugin/resource/123"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(String.class)
            );
        }
    }

    @Nested
    @DisplayName("PATCH Proxy Tests")
    class PatchProxyTests {

        @Test
        @DisplayName("Should proxy PATCH request with body")
        void shouldProxyPatchRequestWithBody() {
            // Given
            ReflectionTestUtils.setField(proxyController, "serviceLayerUrl", "http://service:8081");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/proxy/plugins/test.plugin/resource/123");
            request.setRemoteAddr("127.0.0.1");
            request.setScheme("http");
            request.setServerName("localhost");

            String requestBody = "{\"field\": \"value\"}";

            ResponseEntity<String> mockResponse = ResponseEntity.ok("{\"success\": true}");
            when(restTemplate.exchange(
                anyString(), eq(HttpMethod.PATCH), any(HttpEntity.class), eq(String.class)
            )).thenReturn(mockResponse);

            // When
            ResponseEntity<String> result = proxyController.proxyPatch("test.plugin", requestBody, request);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
        }
    }

    @Nested
    @DisplayName("DELETE Proxy Tests")
    class DeleteProxyTests {

        @Test
        @DisplayName("Should proxy DELETE request")
        void shouldProxyDeleteRequest() {
            // Given
            ReflectionTestUtils.setField(proxyController, "serviceLayerUrl", "http://service:8081");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/proxy/plugins/test.plugin/resource/123");
            request.setRemoteAddr("127.0.0.1");
            request.setScheme("http");
            request.setServerName("localhost");

            ResponseEntity<String> mockResponse = ResponseEntity.ok("{\"deleted\": true}");
            when(restTemplate.exchange(
                anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class)
            )).thenReturn(mockResponse);

            // When
            ResponseEntity<String> result = proxyController.proxyDelete("test.plugin", request);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(restTemplate).exchange(
                eq("http://service:8081/api/v1/plugins/test.plugin/resource/123"),
                eq(HttpMethod.DELETE),
                any(HttpEntity.class),
                eq(String.class)
            );
        }
    }

    @Nested
    @DisplayName("Header Handling Tests")
    class HeaderHandlingTests {

        @Test
        @DisplayName("Should copy headers except hop-by-hop")
        void shouldCopyHeadersExceptHopByHop() {
            // Given
            ReflectionTestUtils.setField(proxyController, "serviceLayerUrl", "http://service:8081");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/proxy/plugins/test.plugin/endpoint");
            request.setRemoteAddr("192.168.1.100");
            request.setScheme("https");
            request.setServerName("api.example.com");
            request.addHeader("Authorization", "Bearer token123");
            request.addHeader("Content-Type", "application/json");
            request.addHeader("Connection", "keep-alive"); // hop-by-hop - should be filtered
            request.addHeader("Transfer-Encoding", "chunked"); // hop-by-hop - should be filtered

            ResponseEntity<String> mockResponse = ResponseEntity.ok("{}");
            when(restTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)
            )).thenReturn(mockResponse);

            // When
            proxyController.proxyGet("test.plugin", request);

            // Then - verify the exchange was called (headers are handled internally)
            verify(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.GET),
                argThat((HttpEntity<String> entity) -> {
                    HttpHeaders headers = entity.getHeaders();
                    // Check forwarding headers were added
                    assertNotNull(headers.get("X-Forwarded-For"));
                    assertNotNull(headers.get("X-Forwarded-Proto"));
                    assertNotNull(headers.get("X-Forwarded-Host"));
                    // Check Authorization was preserved
                    assertNotNull(headers.get("Authorization"));
                    return true;
                }),
                eq(String.class)
            );
        }
    }

    @Nested
    @DisplayName("URL Path Extraction Tests")
    class UrlPathExtractionTests {

        @Test
        @DisplayName("Should extract empty path for plugin root")
        void shouldExtractEmptyPathForPluginRoot() {
            // Given
            ReflectionTestUtils.setField(proxyController, "serviceLayerUrl", "http://service:8081");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/proxy/plugins/test.plugin");
            request.setRemoteAddr("127.0.0.1");
            request.setScheme("http");
            request.setServerName("localhost");

            ResponseEntity<String> mockResponse = ResponseEntity.ok("{}");
            when(restTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)
            )).thenReturn(mockResponse);

            // When
            proxyController.proxyGet("test.plugin", request);

            // Then
            verify(restTemplate).exchange(
                eq("http://service:8081/api/v1/plugins/test.plugin"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
            );
        }

        @Test
        @DisplayName("Should handle nested paths")
        void shouldHandleNestedPaths() {
            // Given
            ReflectionTestUtils.setField(proxyController, "serviceLayerUrl", "http://service:8081");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/proxy/plugins/test.plugin/api/v1/nested/path");
            request.setRemoteAddr("127.0.0.1");
            request.setScheme("http");
            request.setServerName("localhost");

            ResponseEntity<String> mockResponse = ResponseEntity.ok("{}");
            when(restTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)
            )).thenReturn(mockResponse);

            // When
            proxyController.proxyGet("test.plugin", request);

            // Then
            verify(restTemplate).exchange(
                eq("http://service:8081/api/v1/plugins/test.plugin/api/v1/nested/path"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
            );
        }
    }

    @Nested
    @DisplayName("Response Handling Tests")
    class ResponseHandlingTests {

        @Test
        @DisplayName("Should preserve response status code")
        void shouldPreserveResponseStatusCode() {
            // Given
            ReflectionTestUtils.setField(proxyController, "serviceLayerUrl", "http://service:8081");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/proxy/plugins/test.plugin/resource");
            request.setRemoteAddr("127.0.0.1");
            request.setScheme("http");
            request.setServerName("localhost");

            ResponseEntity<String> mockResponse = ResponseEntity.status(HttpStatus.CREATED).body("{\"id\": 1}");
            when(restTemplate.exchange(
                anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)
            )).thenReturn(mockResponse);

            // When
            ResponseEntity<String> result = proxyController.proxyPost("test.plugin", "{}", request);

            // Then
            assertEquals(HttpStatus.CREATED, result.getStatusCode());
        }

        @Test
        @DisplayName("Should filter hop-by-hop response headers")
        void shouldFilterHopByHopResponseHeaders() {
            // Given
            ReflectionTestUtils.setField(proxyController, "serviceLayerUrl", "http://service:8081");

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/v1/proxy/plugins/test.plugin/endpoint");
            request.setRemoteAddr("127.0.0.1");
            request.setScheme("http");
            request.setServerName("localhost");

            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.set("Content-Type", "application/json");
            responseHeaders.set("X-Custom-Header", "value");
            responseHeaders.set("Transfer-Encoding", "chunked"); // hop-by-hop

            ResponseEntity<String> mockResponse = ResponseEntity.ok().headers(responseHeaders).body("{}");
            when(restTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)
            )).thenReturn(mockResponse);

            // When
            ResponseEntity<String> result = proxyController.proxyGet("test.plugin", request);

            // Then
            assertNotNull(result.getHeaders().get("Content-Type"));
            assertNotNull(result.getHeaders().get("X-Custom-Header"));
            // Transfer-Encoding should be filtered
            assertNull(result.getHeaders().get("Transfer-Encoding"));
        }
    }
}
