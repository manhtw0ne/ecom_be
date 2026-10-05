package com.manh.ecom_be.components.metrics;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class BusinessMetricsTest {
    @Test
    void exportsCounterAndHistogramNamesUsedByGrafana() {
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        try {
            var metrics = new BusinessMetrics(registry);
            metrics.incrementOrdersCreated();
            metrics.getProductSearchTimer().record(Duration.ofMillis(25));
            assertThat(registry.scrape()).contains("ecom_orders_total 1.0")
                    .contains("ecom_products_search_seconds_bucket{")
                    .contains("ecom_products_search_seconds_count 1");
        } finally {
            registry.close();
        }
    }
}