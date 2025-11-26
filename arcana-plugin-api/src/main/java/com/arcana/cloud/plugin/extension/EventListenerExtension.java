package com.arcana.cloud.plugin.extension;

import com.arcana.cloud.plugin.event.PluginEvent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Extension point for listening to platform events.
 *
 * <p>Event listeners receive notifications about platform activities such as
 * user actions, system events, and plugin lifecycle changes. This is useful
 * for audit logging, notifications, and reactive workflows.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @EventListenerExtension(
 *     key = "user-event-listener",
 *     events = {UserCreatedEvent.class, UserUpdatedEvent.class}
 * )
 * public class UserEventListener implements EventHandler {
 *
 *     @Override
 *     public void handle(PluginEvent event) {
 *         if (event instanceof UserCreatedEvent userEvent) {
 *             // Handle user creation
 *             auditService.log("User created: " + userEvent.getUser().getUsername());
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>In arcana-plugin.xml:</p>
 * <pre>{@code
 * <event-listener key="user-event-listener"
 *                 class="com.example.UserEventListener">
 *     <event>com.arcana.cloud.event.UserCreatedEvent</event>
 *     <event>com.arcana.cloud.event.UserUpdatedEvent</event>
 * </event-listener>
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtensionPoint(type = "event-listener", description = "Platform event listener")
public @interface EventListenerExtension {

    /**
     * Unique key for this extension within the plugin.
     *
     * @return the extension key
     */
    String key();

    /**
     * The event types this listener handles.
     *
     * @return array of event classes
     */
    Class<? extends PluginEvent>[] events() default {};

    /**
     * Description of this event listener.
     *
     * @return the description
     */
    String description() default "";

    /**
     * Order of execution when multiple listeners handle the same event.
     * Lower values execute first.
     *
     * @return the order
     */
    int order() default 0;

    /**
     * Whether to execute this listener asynchronously.
     *
     * @return true if async execution
     */
    boolean async() default false;
}

/**
 * Interface for event handlers.
 */
interface EventHandler {

    /**
     * Handles a plugin event.
     *
     * @param event the event to handle
     */
    void handle(PluginEvent event);
}
