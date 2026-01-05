package com.arcana.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.grpc.server.autoconfigure.health.GrpcServerHealthAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(exclude = GrpcServerHealthAutoConfiguration.class)
@EnableCaching
@EnableJpaAuditing
public class ArcanaCloudApplication {

    /**
     * Main entry point for the Arcana Cloud application.
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ArcanaCloudApplication.class, args);
    }

    // Private constructor to satisfy checkstyle
    private ArcanaCloudApplication() {
    }
}
