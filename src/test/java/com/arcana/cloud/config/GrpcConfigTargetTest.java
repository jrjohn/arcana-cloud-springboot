package com.arcana.cloud.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcConfigTargetTest {

    @Test
    void schemeLessHostPortGetsExplicitDnsScheme() {
        assertThat(GrpcConfig.normalizeTarget("service:9090")).isEqualTo("dns:///service:9090");
        assertThat(GrpcConfig.normalizeTarget("arcana-service-grpc:9090"))
            .isEqualTo("dns:///arcana-service-grpc:9090");
        assertThat(GrpcConfig.normalizeTarget("localhost:9091")).isEqualTo("dns:///localhost:9091");
        assertThat(GrpcConfig.normalizeTarget("10.0.0.7:9090")).isEqualTo("dns:///10.0.0.7:9090");
        assertThat(GrpcConfig.normalizeTarget("[::1]:9090")).isEqualTo("dns:///[::1]:9090");
        assertThat(GrpcConfig.normalizeTarget("user-service.arcana-cloud.svc.cluster.local:9090"))
            .isEqualTo("dns:///user-service.arcana-cloud.svc.cluster.local:9090");
    }

    @Test
    void surroundingWhitespaceIsTrimmed() {
        assertThat(GrpcConfig.normalizeTarget("  service:9090 ")).isEqualTo("dns:///service:9090");
    }

    @Test
    void targetsWithSchemeAreKeptAsIs() {
        assertThat(GrpcConfig.normalizeTarget("dns:///arcana-service-grpc:9090"))
            .isEqualTo("dns:///arcana-service-grpc:9090");
        assertThat(GrpcConfig.normalizeTarget("dns://8.8.8.8/service:9090"))
            .isEqualTo("dns://8.8.8.8/service:9090");
        assertThat(GrpcConfig.normalizeTarget("static://host:1")).isEqualTo("static://host:1");
        assertThat(GrpcConfig.normalizeTarget("unix:///tmp/grpc.sock")).isEqualTo("unix:///tmp/grpc.sock");
        assertThat(GrpcConfig.normalizeTarget("unix:relative.sock")).isEqualTo("unix:relative.sock");
    }

    @Test
    void nullAndBlankArePassedThrough() {
        assertThat(GrpcConfig.normalizeTarget(null)).isNull();
        assertThat(GrpcConfig.normalizeTarget("   ")).isEmpty();
    }
}
