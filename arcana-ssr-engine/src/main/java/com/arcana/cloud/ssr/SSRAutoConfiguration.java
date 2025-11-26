package com.arcana.cloud.ssr;

import com.arcana.cloud.ssr.cache.InMemorySSRCache;
import com.arcana.cloud.ssr.cache.SSRCache;
import com.arcana.cloud.ssr.controller.SSRController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration for SSR Engine.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "arcana.ssr.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SSRConfiguration.class)
@ComponentScan(basePackages = "com.arcana.cloud.ssr")
public class SSRAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SSRConfiguration ssrConfiguration() {
        return new SSRConfiguration();
    }

    @Bean
    @ConditionalOnMissingBean
    public SSRCache ssrCache() {
        return new InMemorySSRCache();
    }

    @Bean
    @ConditionalOnMissingBean
    public SSREngine ssrEngine(SSRConfiguration config, SSRCache cache) {
        return new SSREngine(config, cache);
    }

    @Bean
    @ConditionalOnMissingBean
    public SSRController ssrController(SSREngine engine, SSRCache cache) {
        return new SSRController(engine, cache);
    }
}
