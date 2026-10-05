package com.manh.ecom_be.configurations;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration.
 *
 * Access Swagger UI at: http://localhost:8080/swagger-ui.html
 * Raw OpenAPI spec:     http://localhost:8080/api-docs
 *
 * JWT usage in UI:
 *   1. Call POST /api/v1/users/login to get a token.
 *   2. Click the Authorize button and enter: Bearer <token>
 *   3. All subsequent requests will include the Authorization header.
 *
 * Swagger UI is disabled in the production profile (application-prod.yml).
 */
@OpenAPIDefinition(
        info = @Info(
                title = "Ecom Backend API",
                version = "1.0.0",
                description = """
                        RESTful API for an e-commerce platform built with Spring Boot 3, Java 21.

                        **Key features:**
                        - JWT authentication + Google/Facebook OAuth2
                        - Product catalog with category, image management
                        - COD order lifecycle; experimental VNPay endpoints disabled by default
                        - Redis caching & distributed lock (anti-overselling)
                        - Rate limiting: 10 req/min (login), 60 req/min (public API)
                        - Real-time metrics via Prometheus + Grafana

                        **Quick start:** Register → Login (get JWT) → Authorize above → Explore!
                        """,
                contact = @Contact(
                        name = "Manh Nguyen",
                        email = "contact@ecom-be.dev"
                ),
                license = @License(
                        name = "MIT License",
                        url = "https://opensource.org/licenses/MIT"
                )
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local Development"),
                @Server(url = "https://ecom-be.onrender.com", description = "Production (Render)")
        }
)
@SecurityScheme(
        name = "bearer-key",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER,
        description = "Enter your JWT token. Obtain it from POST /api/v1/users/login"
)
@Configuration
public class OpenApiConfig {
}
