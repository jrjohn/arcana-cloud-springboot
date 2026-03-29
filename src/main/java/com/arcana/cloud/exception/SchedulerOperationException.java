package com.arcana.cloud.exception;

/**
 * Exception thrown when a Quartz scheduler operation fails.
 */
public class SchedulerOperationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SchedulerOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
