package com.arcana.cloud.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA configuration.
 * Active when database.orm is 'jpa'.
 * Works with MySQL and PostgreSQL databases.
 */
@Configuration
@EnableJpaRepositories(basePackages = {
    "com.arcana.cloud.dao.impl.jpa.repository"
})
@EnableTransactionManagement
@ConditionalOnProperty(name = "database.orm", havingValue = "jpa")
public class JpaConfig {
    // JPA auto-configuration handles most setup via application properties
    // Repository scanning is enabled via @EnableJpaRepositories annotation
}
