package com.manh.ecom_be.configurations;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson configuration for distributed locking.
 *
 * Redisson provides distributed locks backed by Redis, ensuring that critical
 * sections (e.g., stock deduction) are safe even when multiple app instances
 * are running in parallel — something Java's synchronized cannot guarantee.
 *
 * Disabled automatically when spring.data.redis.use-redisson=false (e.g., in test profile).
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.use-redisson", havingValue = "true", matchIfMissing = true)
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setPassword(password.isBlank() ? null : password)
                .setDatabase(database)
                // Connection pool settings to avoid resource leaks
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(10)
                // Timeout settings
                .setConnectTimeout(3000)
                .setTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(500);
        return Redisson.create(config);
    }
}
