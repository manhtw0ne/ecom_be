package com.manh.ecom_be.components;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

/** Central CORS policy consumed by Spring Security before authentication. */
@Configuration("applicationCorsPolicy")
public class CorsFilter {
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:4200,http://localhost:4300,http://localhost:3000}") String origins) {
        var allowed = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (allowed.stream().anyMatch(s -> s.contains("*"))) {
            throw new IllegalArgumentException("CORS origins must be explicit origins, not wildcards");
        }
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowed);
        cors.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN", "X-Request-Id"));
        cors.setExposedHeaders(List.of("X-Request-Id", "X-Total-Count", "X-Total-Pages", "X-RateLimit-Remaining", "Retry-After"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
