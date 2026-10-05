package com.manh.ecom_be.components;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.*;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitInterceptorTest {
    private RateLimitInterceptor interceptor;
    private ProxyManager<String> manager;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        manager = mock(ProxyManager.class);
        RemoteBucketBuilder<String> builder = mock(RemoteBucketBuilder.class);
        Map<String, Bucket> buckets = new HashMap<>();
        when(manager.builder()).thenReturn(builder);
        when(builder.build(anyString(), any(Supplier.class))).thenAnswer(call -> {
            String key = call.getArgument(0);
            Supplier<BucketConfiguration> config = call.getArgument(1);
            Bucket real = buckets.computeIfAbsent(key, ignored ->
                    Bucket.builder().addLimit(config.get().getBandwidths()[0]).build());
            BucketProxy proxy = mock(BucketProxy.class);
            when(proxy.tryConsumeAndReturnRemaining(anyLong())).thenAnswer(
                    consume -> real.tryConsumeAndReturnRemaining(consume.getArgument(0)));
            return proxy;
        });
        interceptor = new RateLimitInterceptor(new ObjectMapper().findAndRegisterModules(), manager);
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    private MockHttpServletResponse call(String uri, String forwarded) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr("192.0.2.5");
        request.addHeader("X-Forwarded-For", forwarded);
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, new Object());
        return response;
    }

    @Test void eleventhLoginIsRejectedEvenWhenForwardedHeaderChanges() throws Exception {
        for (int i = 0; i < 10; i++) assertThat(call("/api/v1/users/login", "10.0.0." + i).getStatus()).isEqualTo(200);
        MockHttpServletResponse response = call("/api/v1/users/login", "198.51.100.9");
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(Integer.parseInt(response.getHeader("Retry-After"))).isBetween(1, 60);
        assertThat(response.getContentAsString()).contains("\"success\":false");
        assertThat(call("/api/v1/products", "other").getStatus()).isEqualTo(200);
    }

    @Test void publicLimitIsSixty() throws Exception {
        for (int i = 0; i < 60; i++) assertThat(call("/api/v1/products", "").getStatus()).isEqualTo(200);
        assertThat(call("/api/v1/products", "").getStatus()).isEqualTo(429);
    }

    @Test void authenticatedAdminGetsOneHundredTwentyRequests() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        for (int i = 0; i < 120; i++) assertThat(call("/api/v1/products", "").getStatus()).isEqualTo(200);
        assertThat(call("/api/v1/products", "").getStatus()).isEqualTo(429);
    }

    @Test void redisFailureReturnsServiceUnavailableInsteadOfDisablingProtection() throws Exception {
        when(manager.builder()).thenThrow(new IllegalStateException("unavailable"));
        MockHttpServletResponse response = call("/api/v1/users/login", "");
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
    }
    @Test void refreshSharesTheStricterAuthenticationQuota() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("/api/v1/users/refreshToken", "").getStatus()).isEqualTo(200);
        }
        assertThat(call("/api/v1/users/refreshToken", "").getStatus()).isEqualTo(429);
        assertThat(call("/api/v1/users/login", "").getStatus()).isEqualTo(429);
    }}
