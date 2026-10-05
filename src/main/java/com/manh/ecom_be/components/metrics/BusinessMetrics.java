package com.manh.ecom_be.components.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Centralized business metrics using Micrometer.
 * All custom counters and timers for the e-commerce domain are registered here.
 *
 * Metrics exposed:
 *   - ecom.orders.created     (Counter) — number of orders successfully created
 *   - ecom.orders.cancelled   (Counter) — number of orders cancelled
 *   - ecom.payments.success   (Counter) — successful payment transactions
 *   - ecom.payments.failed    (Counter) — failed payment transactions
 *   - ecom.products.search    (Timer)   — duration of product search queries
 */
@Component
public class BusinessMetrics {

    private final Counter ordersCreatedCounter;
    private final Counter ordersCancelledCounter;
    private final Counter paymentsSuccessCounter;
    private final Counter paymentsFailedCounter;
    private final Timer productSearchTimer;

    public BusinessMetrics(MeterRegistry registry) {
        this.ordersCreatedCounter = Counter.builder("ecom.orders.created")
                .description("Total number of orders created")
                .register(registry);

        this.ordersCancelledCounter = Counter.builder("ecom.orders.cancelled")
                .description("Total number of orders cancelled")
                .register(registry);

        this.paymentsSuccessCounter = Counter.builder("ecom.payments.success")
                .description("Total successful payment transactions")
                .register(registry);

        this.paymentsFailedCounter = Counter.builder("ecom.payments.failed")
                .description("Total failed payment transactions")
                .register(registry);

        this.productSearchTimer = Timer.builder("ecom.products.search")
                .description("Product search query duration")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void incrementOrdersCreated() {
        ordersCreatedCounter.increment();
    }

    public void incrementOrdersCancelled() {
        ordersCancelledCounter.increment();
    }

    public void incrementPaymentsSuccess() {
        paymentsSuccessCounter.increment();
    }

    public void incrementPaymentsFailed() {
        paymentsFailedCounter.increment();
    }

    public Timer getProductSearchTimer() {
        return productSearchTimer;
    }
}
