package com.manh.ecom_be.components;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manh.ecom_be.exceptions.ErrorCode;
import com.manh.ecom_be.responses.ApiResponse;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitInterceptor implements HandlerInterceptor {
    private final ObjectMapper objectMapper;
    private final ProxyManager<String> rateLimitProxyManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equals(request.getMethod())) return true;
        String uri = request.getRequestURI();
        boolean login = uri.endsWith("/users/login") || uri.endsWith("/users/register") || uri.endsWith("/users/refreshToken");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        int capacity = login ? 10 : admin ? 120 : 60;
        String policy = login ? "login" : admin ? "admin" : "public";
        // Do not trust a client-supplied X-Forwarded-For header.
        String key = "rate-limit:" + policy + ":" + request.getRemoteAddr();
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(b -> b.capacity(capacity).refillIntervally(capacity, Duration.ofMinutes(1)))
                .build();
        ConsumptionProbe probe;
        try {
            probe = rateLimitProxyManager.builder().build(key, () -> configuration)
                    .tryConsumeAndReturnRemaining(1);
        } catch (RuntimeException ex) {
            response.setStatus(503);
            response.setHeader("Retry-After", "5");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiResponse.error(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Rate limiter temporarily unavailable"));
            return false;
        }
        response.setHeader("X-RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
        if (probe.isConsumed()) return true;
        long retry = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
        response.setHeader("Retry-After", Long.toString(retry));
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(ErrorCode.TOO_MANY_REQUESTS));
        return false;
    }
}
