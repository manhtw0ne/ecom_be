package com.manh.ecom_be.configurations;

import com.manh.ecom_be.components.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;

/**
 * MVC configuration: CORS, interceptors, and async executor setup.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final org.springframework.beans.factory.ObjectProvider<RateLimitInterceptor> rateLimitInterceptor;

    @org.springframework.beans.factory.annotation.Value("${api.prefix}")
    private String apiPrefix;

    // ── Interceptors ─────────────────────────────────────────────────────────

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (rateLimitInterceptor.getIfAvailable() == null) return;
        registry.addInterceptor(rateLimitInterceptor.getObject())
                // Apply to all API endpoints
                .addPathPatterns(apiPrefix + "/**")
                // Exclude actuator and docs endpoints from rate limiting
                .excludePathPatterns(
                        apiPrefix + "/actuator/**",
                        "/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                );
    }

    // ── CORS ─────────────────────────────────────────────────────────────────



    // ── Async Executors ───────────────────────────────────────────────────────

    /**
     * Dedicated thread pool for outgoing email delivery.
     *
     * Using a separate executor instead of the default task executor ensures
     * email sending does not starve the main request-handling thread pool.
     * Sizing: small pool (2-4 threads) is sufficient for transactional emails.
     */
    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
