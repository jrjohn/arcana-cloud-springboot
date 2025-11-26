package com.arcana.cloud.plugin.event;

/**
 * Base class for user-related events.
 */
public abstract class UserEvent extends PluginEvent {

    private final Long userId;
    private final String username;
    private final String email;

    /**
     * Creates a new user event.
     *
     * @param userId the user ID
     * @param username the username
     * @param email the email
     */
    protected UserEvent(Long userId, String username, String email) {
        super();
        this.userId = userId;
        this.username = username;
        this.email = email;
    }

    /**
     * Returns the user ID.
     *
     * @return the user ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Returns the username.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the email.
     *
     * @return the email
     */
    public String getEmail() {
        return email;
    }
}

/**
 * Event fired when a user is created.
 */
class UserCreatedEvent extends UserEvent {

    public UserCreatedEvent(Long userId, String username, String email) {
        super(userId, username, email);
    }
}

/**
 * Event fired when a user is updated.
 */
class UserUpdatedEvent extends UserEvent {

    private final String[] changedFields;

    public UserUpdatedEvent(Long userId, String username, String email, String... changedFields) {
        super(userId, username, email);
        this.changedFields = changedFields;
    }

    /**
     * Returns the fields that were changed.
     *
     * @return array of changed field names
     */
    public String[] getChangedFields() {
        return changedFields;
    }
}

/**
 * Event fired when a user is deleted.
 */
class UserDeletedEvent extends UserEvent {

    public UserDeletedEvent(Long userId, String username, String email) {
        super(userId, username, email);
    }
}

/**
 * Event fired when a user logs in.
 */
class UserLoginEvent extends UserEvent {

    private final String ipAddress;
    private final String userAgent;

    public UserLoginEvent(Long userId, String username, String email, String ipAddress, String userAgent) {
        super(userId, username, email);
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }
}

/**
 * Event fired when a user logs out.
 */
class UserLogoutEvent extends UserEvent {

    public UserLogoutEvent(Long userId, String username, String email) {
        super(userId, username, email);
    }
}
