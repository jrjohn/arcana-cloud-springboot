package com.arcana.cloud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

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
     * Using SimpleClientHttpRequestFactory with explicit timeouts (10s connect, 30s read).
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }
}
