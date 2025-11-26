package com.arcana.cloud.plugin.extension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Extension point for adding custom services.
 *
 * <p>Services are registered as OSGi services and can be injected into
 * other components. They provide business logic that can be used by
 * other plugins or the platform itself.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Service interface
 * public interface NotificationService {
 *     void sendNotification(User user, String message);
 * }
 *
 * // Service implementation
 * @ServiceExtension(
 *     key = "email-notification",
 *     serviceInterface = NotificationService.class
 * )
 * public class EmailNotificationService implements NotificationService {
 *
 *     @Override
 *     public void sendNotification(User user, String message) {
 *         // Send email notification
 *     }
 * }
 * }</pre>
 *
 * <p>In arcana-plugin.xml:</p>
 * <pre>{@code
 * <service-extension key="email-notification"
 *                    interface="com.example.NotificationService"
 *                    class="com.example.EmailNotificationService">
 *     <description>Email notification service</description>
 *     <property name="priority" value="100"/>
 * </service-extension>
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtensionPoint(type = "service-extension", description = "Service layer extension")
public @interface ServiceExtension {

    /**
     * Unique key for this extension within the plugin.
     *
     * @return the extension key
     */
    String key();

    /**
     * The service interface this extension implements.
     *
     * @return the service interface class
     */
    Class<?> serviceInterface();

    /**
     * Description of this service.
     *
     * @return the description
     */
    String description() default "";

    /**
     * Priority for service ranking when multiple implementations exist.
     * Higher values have higher priority.
     *
     * @return the priority
     */
    int priority() default 0;

    /**
     * Whether this service should be eager-loaded on plugin start.
     *
     * @return true if eager-loaded
     */
    boolean eager() default false;
}
