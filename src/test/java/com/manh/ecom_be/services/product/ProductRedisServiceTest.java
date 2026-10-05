package com.manh.ecom_be.services.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.*;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductRedisServiceTest {
    private StringRedisTemplate redis;
    private ProductRedisService service;

    @BeforeEach void setup() {
        redis = mock(StringRedisTemplate.class);
        service = new ProductRedisService(redis, new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(service, "useRedisCache", true);
    }
    @AfterEach void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test void disabledCacheDoesNotConnectForReadsWritesOrInvalidation() throws Exception {
        ReflectionTestUtils.setField(service, "useRedisCache", false);
        assertThat(service.getAllProducts("", 0L, PageRequest.of(0, 10))).isNull();
        service.saveAllProducts(new com.manh.ecom_be.responses.product.ProductListResponse(List.of(), 7), "", 0L, PageRequest.of(0, 10));
        service.clear();
        verifyNoInteractions(redis);
    }

    @Test @SuppressWarnings("unchecked")
    void valuesHaveFiniteTtlAndUnsortedPageIsSupported() throws Exception {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        service.saveAllProducts(new com.manh.ecom_be.responses.product.ProductListResponse(List.of(), 7), "", 0L, PageRequest.of(0, 10));
        verify(values).set(startsWith("products:page-v3:page:0:limit:10:"), contains("\"totalPages\":7"), eq(Duration.ofMinutes(5)));
    }

    @Test @SuppressWarnings("unchecked")
    void onlyProductKeysAreInvalidatedAfterCommitAndCursorCloses() {
        org.springframework.data.redis.core.Cursor<String> cursor = mock(org.springframework.data.redis.core.Cursor.class);
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn("products:page-v3:page:0");
        TransactionSynchronizationManager.initSynchronization();
        service.clear();
        verifyNoInteractions(redis);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(redis).scan(argThat(options -> "products:*".equals(options.getPattern())));
        verify(redis).delete("products:page-v3:page:0");
        verify(cursor).close();
        verifyNoMoreInteractions(redis);
    }
    @Test @SuppressWarnings("unchecked")
    void cachedJsonPreservesDecimalPriceWithoutFloatConversion() throws Exception {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("{\"products\":[{\"id\":1,\"price\":9999999.99}],\"totalPages\":1}");
        var result = service.getAllProducts("", 0L, PageRequest.of(0, 10));
        assertThat(result.getProducts().getFirst().getPrice()).isEqualByComparingTo("9999999.99");
        service.saveAllProducts(result, "", 0L, PageRequest.of(0, 10));
        verify(values).set(anyString(), contains("9999999.99"), eq(Duration.ofMinutes(5)));
    }
    @Test @SuppressWarnings("unchecked")
    void emptyPageRoundTripPreservesTotalPages() throws Exception {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var capture = org.mockito.ArgumentCaptor.forClass(String.class);
        service.saveAllProducts(new com.manh.ecom_be.responses.product.ProductListResponse(List.of(), 4), "", 0L, PageRequest.of(9, 10));
        verify(values).set(anyString(), capture.capture(), eq(Duration.ofMinutes(5)));
        when(values.get(anyString())).thenReturn(capture.getValue());
        var page = service.getAllProducts("", 0L, PageRequest.of(9, 10));
        assertThat(page.getProducts()).isEmpty();
        assertThat(page.getTotalPages()).isEqualTo(4);
    }

    @Test @SuppressWarnings("unchecked")
    void corruptCacheIsTreatedAsMiss() throws Exception {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        for (String invalid : List.of("not-json", "[]", "{}", "null")) {
            when(values.get(anyString())).thenReturn(invalid);
            assertThat(service.getAllProducts("", 0L, PageRequest.of(0, 10))).isNull();
        }
    }

    @Test void redisOutageDoesNotBreakCatalogCacheAccess() throws Exception {
        when(redis.opsForValue()).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("offline"));
        assertThat(service.getAllProducts("", 0L, PageRequest.of(0, 10))).isNull();
        service.saveAllProducts(new com.manh.ecom_be.responses.product.ProductListResponse(List.of(), 0), "", 0L, PageRequest.of(0, 10));
    }
}