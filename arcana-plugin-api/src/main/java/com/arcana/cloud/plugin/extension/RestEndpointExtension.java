package com.arcana.cloud.plugin.extension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Extension point for adding REST API endpoints.
 *
 * <p>Classes annotated with @RestEndpointExtension are registered as REST controllers
 * and their endpoints are made available under the specified path prefix.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @RestEndpointExtension(key = "my-api", path = "/api/v1/plugins/myapi")
 * public class MyApiController {
 *
 *     @GetMapping("/hello")
 *     public ResponseEntity<String> hello() {
 *         return ResponseEntity.ok("Hello from plugin!");
 *     }
 *
 *     @PostMapping("/process")
 *     public ResponseEntity<Result> process(@RequestBody Request request) {
 *         // Process request
 *         return ResponseEntity.ok(result);
 *     }
 * }
 * }</pre>
 *
 * <p>In arcana-plugin.xml:</p>
 * <pre>{@code
 * <rest-extension key="my-api"
 *                 path="/api/v1/plugins/myapi"
 *                 class="com.example.MyApiController">
 *     <description>My API endpoints</description>
 *     <requires-permission>USER</requires-permission>
 * </rest-extension>
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtensionPoint(type = "rest-extension", description = "REST API endpoint extension")
public @interface RestEndpointExtension {

    /**
     * Unique key for this extension within the plugin.
     *
     * @return the extension key
     */
    String key();

    /**
     * The base path for all endpoints in this controller.
     * Should start with /api/v1/plugins/
     *
     * @return the base path
     */
    String path();

    /**
     * Description of this REST extension.
     *
     * @return the description
     */
    String description() default "";

    /**
     * Required permission to access these endpoints.
     * Empty means public access.
     *
     * @return the required permission
     */
    String requiresPermission() default "";

    /**
     * Whether to include this in OpenAPI documentation.
     *
     * @return true if included in docs
     */
    boolean documented() default true;
}
