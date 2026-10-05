package com.manh.ecom_be.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@ActiveProfiles("test")
class ActuatorAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockBean(name = "redisCustom") HealthIndicator redis;
    @MockBean(name = "kafkaCustom") HealthIndicator kafka;
    @MockBean(name = "customHealthCheck") HealthIndicator custom;

    @BeforeEach void healthyInfrastructure() {
        var health = Health.up().withDetail("internalHost", "private-host").build();
        when(redis.health()).thenReturn(health);
        when(kafka.health()).thenReturn(health);
        when(custom.health()).thenReturn(health);
        // Actuator invokes the interface default method, not health() directly.
        when(redis.getHealth(org.mockito.ArgumentMatchers.anyBoolean())).thenCallRealMethod();
        when(kafka.getHealth(org.mockito.ArgumentMatchers.anyBoolean())).thenCallRealMethod();
        when(custom.getHealth(org.mockito.ArgumentMatchers.anyBoolean())).thenCallRealMethod();
    }

    @ParameterizedTest @ValueSource(strings = {"", "/liveness", "/readiness"})
    void publicHealthDoesNotExposeComponentsOrDetails(String suffix) throws Exception {
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                get("/api/v1/actuator/health" + suffix),
                get("/api/v1/actuator/health" + suffix).with(user("buyer").roles("USER"))}) {
            mvc.perform(request).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(jsonPath("$.details").doesNotExist())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-host"))));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/actuator", "/api/v1/actuator/info",
            "/api/v1/actuator/metrics", "/api/v1/actuator/metrics/jvm.memory.used",
            "/api/v1/actuator/prometheus", "/api/v1/actuator/health/db", "/api/v1/healthcheck/health"})
    void operationalEndpointsRequireAdmin(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(user("buyer").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(user("operator").roles("ADMIN"))).andExpect(status().isOk());
    }

    @Test void onlyAdminCanSeeHealthDetails() throws Exception {
        mvc.perform(get("/api/v1/actuator/health").with(user("operator").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.redisCustom.details.internalHost").value("private-host"));
    }

    @Test void unhealthyReadinessReturns503WithoutLeakingCause() throws Exception {
        when(redis.health()).thenReturn(Health.down().withDetail("error", "private-host unavailable").build());
        mvc.perform(get("/api/v1/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-host"))));
    }
}
