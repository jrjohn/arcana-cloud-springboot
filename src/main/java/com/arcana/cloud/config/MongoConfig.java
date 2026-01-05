package com.arcana.cloud.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB configuration.
 * Active when database.type is 'mongodb'.
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.arcana.cloud.dao.impl.mongodb")
@EnableMongoAuditing
@ConditionalOnProperty(name = "database.type", havingValue = "mongodb")
public class MongoConfig {
    // MongoDB connection is auto-configured via spring.data.mongodb.* properties
}
