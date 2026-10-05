package com.manh.ecom_be.services.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manh.ecom_be.components.TransactionCallbacks;
import com.manh.ecom_be.responses.product.ProductResponse;
import com.manh.ecom_be.responses.product.ProductListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductRedisService implements InterfaceProductRedisService {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.data.redis.use-redis-cache:true}")
    private boolean useRedisCache;

    private String getKeyFrom(String keyword, Long categoryId, PageRequest pageRequest) {
        String search = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (keyword == null ? "" : keyword).getBytes(StandardCharsets.UTF_8));
        return "products:page-v3:page:" + pageRequest.getPageNumber() + ":limit:" + pageRequest.getPageSize()
                + ":keyword:" + search + ":categoryId:" + categoryId + ":sort:" + pageRequest.getSort();
    }

    @Override
    public ProductListResponse getAllProducts(String keyword, Long categoryId, PageRequest pageRequest)
            throws JsonProcessingException {
        if (!useRedisCache) return null;
        try {
            String json = redisTemplate.opsForValue().get(getKeyFrom(keyword, categoryId, pageRequest));
            if (json == null) return null;
            var cached = objectMapper.readValue(json, ProductListResponse.class);
            return cached == null || cached.getProducts() == null || cached.getTotalPages() < 0 ? null : cached;
        } catch (org.springframework.dao.DataAccessException | JsonProcessingException ex) {
            log.warn("Product cache read failed; querying database", ex);
            return null;
        }
    }

    @Override
    public void clear() {
        if (!useRedisCache) return;
        TransactionCallbacks.afterCommit(() -> {
            try (var cursor = redisTemplate.scan(ScanOptions.scanOptions().match("products:*").count(100).build())) {
                while (cursor.hasNext()) redisTemplate.delete(cursor.next());
            } catch (org.springframework.dao.DataAccessException ex) {
                log.warn("Product cache invalidation failed; entries expire within five minutes", ex);
            }
        });
    }

    @Override
    public void saveAllProducts(ProductListResponse products, String keyword, Long categoryId,
                                PageRequest pageRequest) throws JsonProcessingException {
        if (!useRedisCache) return;
        try {
            redisTemplate.opsForValue().set(getKeyFrom(keyword, categoryId, pageRequest),
                    objectMapper.writeValueAsString(products), Duration.ofMinutes(5));
        } catch (org.springframework.dao.DataAccessException ex) {
            log.warn("Product cache write failed", ex);
        }
    }
}
