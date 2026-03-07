package com.arcana.cloud.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Service configuration for layered deployment.
 *
 * Bean creation is now handled via @ConditionalOnProperty annotations
 * directly on the service classes:
 * - UserServiceImpl: created when deployment.layer=repository (or not set)
 * - GrpcUserServiceClient: created when deployment.layer=controller
 * - HttpUserServiceClient: created when deployment.layer=controller and communication.protocol=http
 */
@Configuration
public class ServiceConfiguration {

    /**
     * RestTemplate used by HTTP-mode service clients and PluginProxyController.
     * Spring Boot 3.x no longer auto-configures RestTemplate; define it explicitly.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(30))
            .build();
    }
}
