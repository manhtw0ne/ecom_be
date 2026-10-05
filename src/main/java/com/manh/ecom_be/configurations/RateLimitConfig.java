package com.manh.ecom_be.configurations;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitConfig {
    @Bean(destroyMethod = "shutdown")
    RedisClient rateLimitRedisClient(RedisProperties properties) {
        RedisURI.Builder uri = RedisURI.Builder.redis(properties.getHost(), properties.getPort())
                .withDatabase(properties.getDatabase()).withTimeout(Duration.ofSeconds(3));
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            uri.withPassword(properties.getPassword().toCharArray());
        }
        return RedisClient.create(uri.build());
    }

    @Bean(destroyMethod = "close")
    StatefulRedisConnection<String, byte[]> rateLimitConnection(RedisClient rateLimitRedisClient) {
        return rateLimitRedisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    @Bean
    ProxyManager<String> rateLimitProxyManager(StatefulRedisConnection<String, byte[]> rateLimitConnection) {
        return LettuceBasedProxyManager.builderFor(rateLimitConnection)
                .withExpirationStrategy(ExpirationAfterWriteStrategy
                        .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
                .build();
    }
}
