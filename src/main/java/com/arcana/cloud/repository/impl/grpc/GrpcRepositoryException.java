package com.arcana.cloud.repository.impl.grpc;

/**
 * Thrown when a gRPC repository operation fails with an unexpected status.
 */
public class GrpcRepositoryException extends RuntimeException {

    public GrpcRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
