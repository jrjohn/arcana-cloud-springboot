package com.arcana.cloud.config;

import org.springframework.context.annotation.Configuration;

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
    // Bean creation is handled via component scanning with @ConditionalOnProperty
}
