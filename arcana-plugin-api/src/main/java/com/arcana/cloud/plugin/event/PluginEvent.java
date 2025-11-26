package com.arcana.cloud.plugin.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all plugin events.
 *
 * <p>Events are used to communicate between plugins and the platform.
 * Plugins can publish events and subscribe to events from other plugins
 * or the platform itself.</p>
 *
 * <p>Example custom event:</p>
 * <pre>{@code
 * public class AuditLogCreatedEvent extends PluginEvent {
 *     private final AuditEntry entry;
 *
 *     public AuditLogCreatedEvent(AuditEntry entry) {
 *         super();
 *         this.entry = entry;
 *     }
 *
 *     public AuditEntry getEntry() {
 *         return entry;
 *     }
 * }
 * }</pre>
 */
public abstract class PluginEvent {

    private final String eventId;
    private final Instant timestamp;
    private final String sourcePluginKey;

    /**
     * Creates a new plugin event.
     */
    protected PluginEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.sourcePluginKey = null; // Set by framework
    }

    /**
     * Creates a new plugin event with a source plugin.
     *
     * @param sourcePluginKey the key of the plugin that created this event
     */
    protected PluginEvent(String sourcePluginKey) {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.sourcePluginKey = sourcePluginKey;
    }

    /**
     * Returns the unique event ID.
     *
     * @return the event ID
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Returns the event timestamp.
     *
     * @return the timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the source plugin key, if known.
     *
     * @return the source plugin key or null
     */
    public String getSourcePluginKey() {
        return sourcePluginKey;
    }

    /**
     * Returns the event type name.
     *
     * @return the event type
     */
    public String getEventType() {
        return getClass().getSimpleName();
    }

    @Override
    public String toString() {
        return String.format("%s[id=%s, timestamp=%s, source=%s]",
            getEventType(), eventId, timestamp, sourcePluginKey);
    }
}
