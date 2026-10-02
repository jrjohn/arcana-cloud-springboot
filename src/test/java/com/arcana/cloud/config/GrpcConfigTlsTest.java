package com.arcana.cloud.config;

import io.grpc.ManagedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TLS misconfiguration must fail loudly. Before 2026-10-02 GrpcConfig caught SSLException and
 * returned a PLAINTEXT channel, skipped a configured-but-missing trust certificate (falling back
 * to the JVM trust store), and skipped a missing client cert/key (silently dropping mTLS).
 */
@DisplayName("GrpcConfig — TLS fails fast, never downgrades")
class GrpcConfigTlsTest {

    private ManagedChannel channel;

    @AfterEach
    void close() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static String resource(String name) {
        try {
            return new File(Objects.requireNonNull(
                GrpcConfigTlsTest.class.getResource("/tls/" + name)).toURI()).getAbsolutePath();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static GrpcConfig config(boolean tls, String trust, String clientCert, String clientKey) {
        GrpcConfig config = new GrpcConfig();
        ReflectionTestUtils.setField(config, "tlsEnabled", tls);
        ReflectionTestUtils.setField(config, "trustCertPath", trust);
        ReflectionTestUtils.setField(config, "clientCertPath", clientCert);
        ReflectionTestUtils.setField(config, "clientKeyPath", clientKey);
        ReflectionTestUtils.setField(config, "keepAliveTimeSeconds", 30L);
        ReflectionTestUtils.setField(config, "keepAliveTimeoutSeconds", 10L);
        ReflectionTestUtils.setField(config, "maxRetryAttempts", 3);
        ReflectionTestUtils.setField(config, "initialBackoffMs", 100L);
        ReflectionTestUtils.setField(config, "maxBackoffMs", 2000L);
        ReflectionTestUtils.setField(config, "deadlineMs", 30000L);
        ReflectionTestUtils.setField(config, "maxInboundMessageSize", 16777216);
        return config;
    }

    @Test
    @DisplayName("valid CA certificate → TLS channel")
    void validTrustCertificateBuildsChannel() {
        channel = config(true, resource("test-ca.pem"), "", "").createChannel("localhost:9090");
        assertNotNull(channel);
    }

    @Test
    @DisplayName("valid client cert + key → mTLS channel")
    void validClientCertificateBuildsMutualTlsChannel() {
        channel = config(true, resource("test-ca.pem"), resource("test-client.pem"),
            resource("test-client.key")).createChannel("localhost:9090");
        assertNotNull(channel);
    }

    @Test
    @DisplayName("missing trust certificate file → refuses (no silent JVM trust store)")
    void missingTrustCertificateFails() {
        GrpcConfig cfg = config(true, "/nonexistent/ca.pem", "", "");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> cfg.createChannel("localhost:9090"));
        assertTrue(ex.getMessage().contains("/nonexistent/ca.pem"), ex.getMessage());
    }

    @Test
    @DisplayName("client cert without key → refuses (no silent loss of mTLS)")
    void clientCertificateWithoutKeyFails() {
        GrpcConfig cfg = config(true, resource("test-ca.pem"), resource("test-client.pem"), "");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> cfg.createChannel("localhost:9090"));
        assertTrue(ex.getMessage().contains("client-key-path"), ex.getMessage());
    }

    @Test
    @DisplayName("missing client key file → refuses")
    void missingClientKeyFileFails() {
        GrpcConfig cfg = config(true, resource("test-ca.pem"), resource("test-client.pem"),
            "/nonexistent/client.key");
        assertThrows(IllegalStateException.class, () -> cfg.createChannel("localhost:9090"));
    }

    @Test
    @DisplayName("corrupt certificate → refuses, never a plaintext fallback")
    void corruptCertificateFailsInsteadOfPlaintext() {
        GrpcConfig cfg = config(true, resource("garbage.pem"), "", "");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> cfg.createChannel("localhost:9090"));
        assertTrue(ex.getMessage().contains("TLS"), ex.getMessage());
    }

    @Test
    @DisplayName("TLS disabled → plaintext channel as before")
    void tlsDisabledStillBuildsPlaintextChannel() {
        channel = config(false, "/nonexistent/ignored.pem", "", "").createChannel("localhost:9090");
        assertNotNull(channel);
    }
}
