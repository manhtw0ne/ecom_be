package com.manh.ecom_be.configurations;

import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot owns Flyway and the pooled DataSource. Configure migration behavior
 * using spring.flyway.*; never repair checksums automatically on application startup.
 */
@Configuration
public class FlywayConfig {
}
