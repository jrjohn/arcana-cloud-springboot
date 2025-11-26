package com.arcana.cloud.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class GrpcConfig {

    @Value("${service.grpc.url:localhost:9090}")
    private String serviceGrpcUrl;

    @Value("${repository.grpc.url:localhost:9091}")
    private String repositoryGrpcUrl;

    private ManagedChannel serviceChannel;
    private ManagedChannel repositoryChannel;

    @Bean
    @ConditionalOnProperty(name = "deployment.layer", havingValue = "controller")
    public ManagedChannel serviceChannel() {
        this.serviceChannel = ManagedChannelBuilder.forTarget(serviceGrpcUrl)
            .usePlaintext()
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .build();
        return this.serviceChannel;
    }

    @Bean
    @ConditionalOnProperty(name = "deployment.layer", havingValue = "service")
    public ManagedChannel repositoryChannel() {
        this.repositoryChannel = ManagedChannelBuilder.forTarget(repositoryGrpcUrl)
            .usePlaintext()
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .build();
        return this.repositoryChannel;
    }

    @PreDestroy
    public void shutdown() {
        if (serviceChannel != null && !serviceChannel.isShutdown()) {
            serviceChannel.shutdown();
        }
        if (repositoryChannel != null && !repositoryChannel.isShutdown()) {
            repositoryChannel.shutdown();
        }
    }
}
