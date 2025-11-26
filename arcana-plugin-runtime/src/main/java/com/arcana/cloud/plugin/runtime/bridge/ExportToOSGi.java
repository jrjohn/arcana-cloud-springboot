package com.arcana.cloud.plugin.runtime.bridge;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Spring bean to be exported to OSGi service registry.
 *
 * <p>Beans annotated with this annotation will be automatically registered
 * as OSGi services when the Spring-OSGi bridge is initialized.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * @Service
 * @ExportToOSGi(serviceInterface = NotificationService.class)
 * public class EmailNotificationService implements NotificationService {
 *     // ...
 * }
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExportToOSGi {

    /**
     * The service interface to register under.
     * If not specified, all implemented interfaces will be used.
     *
     * @return the service interface
     */
    Class<?>[] serviceInterface() default {};

    /**
     * Service ranking. Higher values have higher priority.
     *
     * @return the ranking
     */
    int ranking() default 0;

    /**
     * Additional service properties.
     *
     * @return properties as key=value pairs
     */
    String[] properties() default {};
}
