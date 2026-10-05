package com.manh.ecom_be.configurations;


import com.manh.ecom_be.filters.JwtTokenFilter;
import com.manh.ecom_be.filters.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import static org.springframework.http.HttpMethod.*;



@Configuration
@EnableMethodSecurity
@EnableWebSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {
    private final JwtTokenFilter jwtTokenFilter;
    private final RequestIdFilter requestIdFilter;

    @Value("${api.prefix}")
    private String apiPrefix;

    @Value("${management.endpoints.web.base-path:/actuator}")
    private String managementBasePath;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtTokenFilter, RequestIdFilter.class)
                .exceptionHandling(customizer -> customizer
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(GET, managementBasePath + "/health",
                                managementBasePath + "/health/liveness",
                                managementBasePath + "/health/readiness").permitAll()
                        .requestMatchers(org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest.toAnyEndpoint())
                                .hasRole("ADMIN")
                        .requestMatchers(managementBasePath, managementBasePath + "/**").hasRole("ADMIN")
                        .requestMatchers(apiPrefix + "/healthcheck/**").hasRole("ADMIN")
                        .requestMatchers(POST, apiPrefix + "/users/refreshToken").permitAll()
                        .requestMatchers(
                                String.format("%s/users/register", apiPrefix),
                                String.format("%s/users/login", apiPrefix),
                                // Swagger
                                "/api-docs",
                                "/api-docs/**",
                                "/swagger-resources",
                                "/swagger-resources/**",
                                "/configuration/ui",
                                "/configuration/security",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/webjars/swagger-ui/**",
                                "/swagger-ui/index.html",
                                // Social login
                                String.format("%s/users/auth/social-login", apiPrefix),
                                String.format("%s/users/auth/social/callback", apiPrefix)
                        ).permitAll()
                        .requestMatchers(
                                GET,
                                String.format("%s/roles**", apiPrefix)).permitAll()
                                .requestMatchers(GET,
                                        String.format("%s/policies/**", apiPrefix)).permitAll()
                                .requestMatchers(GET,
                                        String.format("%s/categories/**", apiPrefix)).permitAll()
                                .requestMatchers(GET,
                                        String.format("%s/products/**", apiPrefix)).permitAll()
                                .requestMatchers(GET,
                                        String.format("%s/products/images/*", apiPrefix)).permitAll()
                                .requestMatchers(GET,
                                        String.format("%s/users/profile-images/**", apiPrefix)).permitAll()
                                .anyRequest().authenticated()
                        )
                .oauth2Login(Customizer.withDefaults())
                .oauth2ResourceServer(c -> c.bearerTokenResolver(request ->
                        org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null
                                ? null : new org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver().resolve(request))
                        .opaqueToken(Customizer.withDefaults()));
        return http.build();
    }
}
