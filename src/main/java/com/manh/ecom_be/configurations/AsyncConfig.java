package com.manh.ecom_be.configurations;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring's asynchronous method execution capability.
 *
 * With @EnableAsync, any bean method annotated with @Async will be executed
 * in a background thread using the executor named in the annotation value
 * (e.g., @Async("emailExecutor")).
 *
 * The actual thread pool for email is defined in WebMvcConfig.emailExecutor().
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
