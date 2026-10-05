package com.manh.ecom_be.configurations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manh.ecom_be.models.Category;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class RedisConfig {
    @Bean
    CacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper,
                              @Value("${spring.data.redis.use-redis-cache:true}") boolean enabled) {
        if (!enabled) return new NoOpCacheManager();
        Jackson2JsonRedisSerializer<List<Category>> serializer = new Jackson2JsonRedisSerializer<>(
                objectMapper, objectMapper.getTypeFactory().constructCollectionType(List.class, Category.class));
        RedisCacheConfiguration categories = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)).disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration("categories", categories).transactionAware().build();
    }
}
