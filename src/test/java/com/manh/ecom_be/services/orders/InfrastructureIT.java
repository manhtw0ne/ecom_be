package com.manh.ecom_be.services.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manh.ecom_be.components.RateLimitInterceptor;
import com.manh.ecom_be.dtos.CategoryDTO;
import com.manh.ecom_be.models.Category;
import com.manh.ecom_be.services.category.CategoryService;
import com.manh.ecom_be.services.product.ProductRedisService;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.codec.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.*;
import org.springframework.test.context.TestPropertySource;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:13306/ecom_test?useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=ecom", "spring.datasource.password=test-password",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=true",
        "spring.data.redis.host=localhost", "spring.data.redis.port=16379",
        "spring.data.redis.use-redis-cache=true", "spring.data.redis.use-redisson=true",
        "app.rate-limit.enabled=true"
})
@Import(OrderConcurrencyTest.EventConfig.class)
class InfrastructureIT extends OrderConcurrencyTest {
    @Autowired RateLimitInterceptor limiter;
    @Autowired RedisClient rateLimitRedisClient;
    @Autowired ObjectMapper mapper;
    @Autowired StringRedisTemplate redis;
    @Autowired ProductRedisService productCache;
    @Autowired CategoryService categoryService;

    @Test void redisQuotaIsSharedWithNewApplicationInstance() throws Exception {
        String ip = "198.51.100." + System.nanoTime();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users/login");
        request.setRemoteAddr(ip);
        for (int i = 0; i < 10; i++) assertThat(limiter.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        try (var connection = rateLimitRedisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE))) {
            var manager = LettuceBasedProxyManager.builderFor(connection)
                    .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
                    .build();
            RateLimitInterceptor secondInstance = new RateLimitInterceptor(mapper, manager);
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThat(secondInstance.preHandle(request, response, new Object())).isFalse();
            assertThat(response.getStatus()).isEqualTo(429);
            assertThat(redis.getExpire("rate-limit:login:" + ip)).isPositive();
        }
    }

    @Test void invalidationPreservesNonProductKeysAndCategoryCacheRoundTrips() throws Exception {
        String unrelated = "verification:keep:" + System.nanoTime();
        redis.opsForValue().set(unrelated, "keep", Duration.ofMinutes(1));
        productCache.saveAllProducts(new com.manh.ecom_be.responses.product.ProductListResponse(List.of(), 7), "verification", 0L, PageRequest.of(0, 10));
        assertThat(productCache.getAllProducts("verification", 0L, PageRequest.of(0, 10)).getProducts()).isEmpty();
        productCache.clear();
        assertThat(productCache.getAllProducts("verification", 0L, PageRequest.of(0, 10))).isNull();
        assertThat(redis.opsForValue().get(unrelated)).isEqualTo("keep");

        categoryService.getAllCategories();
        CategoryDTO dto = new CategoryDTO();
        dto.setName("Cached " + System.nanoTime());
        var admin = com.manh.ecom_be.models.User.builder().id(999999L).active(true)
                .role(com.manh.ecom_be.models.Role.builder().name("ADMIN").build()).build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
        Category created;
        try { created = categoryService.createCategory(dto); }
        finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
        assertThat(categoryService.getAllCategories()).extracting(Category::getId).contains(created.getId());
        assertThat(categoryService.getAllCategories()).extracting(Category::getId).contains(created.getId());
        assertThat(redis.getExpire("categories::all")).isBetween(1L, 600L);
    }
}
