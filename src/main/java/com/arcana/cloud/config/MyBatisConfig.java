package com.arcana.cloud.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * MyBatis configuration.
 * Active when database.orm is 'mybatis' or not specified (default).
 * Works with MySQL and PostgreSQL databases.
 */
@Configuration
@MapperScan("com.arcana.cloud.dao.impl.mybatis.mapper")
@EnableTransactionManagement
@ConditionalOnProperty(name = "database.orm", havingValue = "mybatis", matchIfMissing = true)
public class MyBatisConfig {
    // MyBatis auto-configuration handles most setup via application properties
    // Mapper scanning is enabled via @MapperScan annotation
}
