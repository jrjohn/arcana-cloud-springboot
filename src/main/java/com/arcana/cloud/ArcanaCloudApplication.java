package com.arcana.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableCaching
@EnableJpaAuditing
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class ArcanaCloudApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArcanaCloudApplication.class, args);
    }
}
