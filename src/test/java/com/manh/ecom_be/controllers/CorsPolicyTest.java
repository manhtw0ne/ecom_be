package com.manh.ecom_be.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class CorsPolicyTest {
    @Autowired MockMvc mvc;
    @Test void allowedOriginCanPreflightProtectedApiWithoutToken() throws Exception {
        mvc.perform(options("/api/v1/orders").header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
    @Test void foreignOriginIsRejectedForPreflightAndActualRequest() throws Exception {
        mvc.perform(options("/api/v1/orders").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(get("/api/v1/categories").header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden());
    }
    @Test void corsHeadersArePresentOnAuthenticationError() throws Exception {
        mvc.perform(get("/api/v1/orders/1").header("Origin", "http://localhost:4200"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }
    @Test void paginationHeadersAreReadableByBrowser() throws Exception {
        mvc.perform(get("/api/v1/categories").header("Origin", "http://localhost:4200"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers", org.hamcrest.Matchers.containsString("X-Total-Pages")));
    }
    @Test void wildcardOriginsFailAndEmptyConfigurationDeniesCrossOrigin() {
        var config = new com.manh.ecom_be.components.CorsFilter();
        assertThatThrownBy(() -> config.corsConfigurationSource("*")).isInstanceOf(IllegalArgumentException.class);
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/categories");
        assertThat(config.corsConfigurationSource("").getCorsConfiguration(request).checkOrigin("https://example.test")).isNull();
    }
}
