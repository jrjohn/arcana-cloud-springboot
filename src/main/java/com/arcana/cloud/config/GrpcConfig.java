package com.arcana.cloud.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLException;
import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * gRPC Configuration with TLS/mTLS support and resilience patterns.
 *
 * <p>Supports three modes:</p>
 * <ul>
 *   <li>Plaintext (development/testing)</li>
 *   <li>TLS (server authentication only)</li>
 *   <li>mTLS (mutual authentication)</li>
 * </ul>
 */
@Configuration
public class GrpcConfig {

    private static final Logger log = LoggerFactory.getLogger(GrpcConfig.class);

    @Value("${service.grpc.url:localhost:9090}")
    private String serviceGrpcUrl;

    @Value("${repository.grpc.url:localhost:9091}")
    private String repositoryGrpcUrl;

    // TLS Configuration
    @Value("${grpc.client.tls.enabled:false}")
    private boolean tlsEnabled;

    @Value("${grpc.client.tls.trust-cert-path:}")
    private String trustCertPath;

    @Value("${grpc.client.tls.client-cert-path:}")
    private String clientCertPath;

    @Value("${grpc.client.tls.client-key-path:}")
    private String clientKeyPath;

    // Resilience Configuration
    @Value("${grpc.client.keepalive.time:30}")
    private long keepAliveTimeSeconds;

    @Value("${grpc.client.keepalive.timeout:10}")
    private long keepAliveTimeoutSeconds;

    @Value("${grpc.client.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${grpc.client.retry.initial-backoff-ms:100}")
    private long initialBackoffMs;

    @Value("${grpc.client.retry.max-backoff-ms:2000}")
    private long maxBackoffMs;

    @Value("${grpc.client.deadline-ms:30000}")
    private long deadlineMs;

    @Value("${grpc.client.max-inbound-message-size:16777216}")
    private int maxInboundMessageSize;

    private ManagedChannel serviceChannel;
    private ManagedChannel repositoryChannel;

    @Bean
    @ConditionalOnProperty(name = "deployment.layer", havingValue = "controller")
    public ManagedChannel serviceChannel() {
        log.info("Creating gRPC service channel to {} (TLS: {})", serviceGrpcUrl, tlsEnabled);
        this.serviceChannel = createChannel(serviceGrpcUrl);
        return this.serviceChannel;
    }

    @Bean
    @ConditionalOnProperty(name = "deployment.layer", havingValue = "service")
    public ManagedChannel repositoryChannel() {
        log.info("Creating gRPC repository channel to {} (TLS: {})", repositoryGrpcUrl, tlsEnabled);
        this.repositoryChannel = createChannel(repositoryGrpcUrl);
        return this.repositoryChannel;
    }

    /**
     * Creates a managed gRPC channel with configured resilience and security settings.
     */
    private ManagedChannel createChannel(String target) {
        if (tlsEnabled) {
            return createSecureChannel(target);
        } else {
            return createPlaintextChannel(target);
        }
    }

    /**
     * Creates a plaintext channel (for development/testing).
     */
    private ManagedChannel createPlaintextChannel(String target) {
        return ManagedChannelBuilder.forTarget(target)
            .usePlaintext()
            .keepAliveTime(keepAliveTimeSeconds, TimeUnit.SECONDS)
            .keepAliveTimeout(keepAliveTimeoutSeconds, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .maxInboundMessageSize(maxInboundMessageSize)
            // Enable retry
            .enableRetry()
            .maxRetryAttempts(maxRetryAttempts)
            // Default service config with retry policy
            .defaultServiceConfig(getDefaultServiceConfig())
            .build();
    }

    /**
     * Creates a TLS/mTLS secured channel.
     */
    private ManagedChannel createSecureChannel(String target) {
        try {
            SslContext sslContext = buildSslContext();

            return NettyChannelBuilder.forTarget(target)
                .sslContext(sslContext)
                .keepAliveTime(keepAliveTimeSeconds, TimeUnit.SECONDS)
                .keepAliveTimeout(keepAliveTimeoutSeconds, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .maxInboundMessageSize(maxInboundMessageSize)
                .enableRetry()
                .maxRetryAttempts(maxRetryAttempts)
                .defaultServiceConfig(getDefaultServiceConfig())
                .build();

        } catch (SSLException e) {
            log.error("Failed to create secure gRPC channel, falling back to plaintext", e);
            return createPlaintextChannel(target);
        }
    }

    /**
     * Builds SSL context for TLS/mTLS.
     */
    private SslContext buildSslContext() throws SSLException {
        SslContextBuilder builder = GrpcSslContexts.forClient();

        // Trust certificate (CA certificate for server verification)
        if (trustCertPath != null && !trustCertPath.isEmpty()) {
            File trustCert = new File(trustCertPath);
            if (trustCert.exists()) {
                builder.trustManager(trustCert);
                log.info("Loaded trust certificate from: {}", trustCertPath);
            }
        }

        // Client certificate and key (for mTLS)
        if (clientCertPath != null && !clientCertPath.isEmpty()
            && clientKeyPath != null && !clientKeyPath.isEmpty()) {
            File clientCert = new File(clientCertPath);
            File clientKey = new File(clientKeyPath);
            if (clientCert.exists() && clientKey.exists()) {
                builder.keyManager(clientCert, clientKey);
                log.info("Loaded client certificate for mTLS from: {}", clientCertPath);
            }
        }

        return builder.build();
    }

    /**
     * Returns default service config with retry policy.
     */
    private java.util.Map<String, Object> getDefaultServiceConfig() {
        // Retry policy for idempotent methods
        java.util.Map<String, Object> retryPolicy = new java.util.HashMap<>();
        retryPolicy.put("maxAttempts", (double) maxRetryAttempts);
        retryPolicy.put("initialBackoff", initialBackoffMs + "ms");
        retryPolicy.put("maxBackoff", maxBackoffMs + "ms");
        retryPolicy.put("backoffMultiplier", 2.0);
        retryPolicy.put("retryableStatusCodes", java.util.List.of("UNAVAILABLE", "DEADLINE_EXCEEDED"));

        java.util.Map<String, Object> methodConfig = new java.util.HashMap<>();
        methodConfig.put("name", java.util.List.of(java.util.Map.of()));
        methodConfig.put("retryPolicy", retryPolicy);
        methodConfig.put("timeout", deadlineMs + "ms");

        java.util.Map<String, Object> serviceConfig = new java.util.HashMap<>();
        serviceConfig.put("methodConfig", java.util.List.of(methodConfig));

        // Load balancing policy for Kubernetes DNS
        serviceConfig.put("loadBalancingPolicy", "round_robin");

        return serviceConfig;
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down gRPC channels");
        shutdownChannel(serviceChannel, "service");
        shutdownChannel(repositoryChannel, "repository");
    }

    private void shutdownChannel(ManagedChannel channel, String name) {
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdown();
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("gRPC {} channel did not terminate gracefully, forcing shutdown", name);
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted while shutting down gRPC {} channel", name);
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
