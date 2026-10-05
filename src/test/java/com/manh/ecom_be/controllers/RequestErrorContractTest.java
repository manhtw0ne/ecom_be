package com.manh.ecom_be.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestErrorContractTest {
    @Autowired MockMvc mvc;
    @ParameterizedTest @ValueSource(strings = {"abc", "999999999999999999999999999", "1.5"})
    void invalidNumericQueryReturns400(String page) throws Exception {
        mvc.perform(get("/api/v1/categories").param("page", page))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400));
    }
    @Test void invalidPathIdReturns400() throws Exception {
        mvc.perform(get("/api/v1/categories/not-a-number"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }
    @Test void malformedJsonDoesNotExposeSubmittedSecret() throws Exception {
        mvc.perform(post("/api/v1/users/login").contentType("application/json")
                .content("{\"password\":\"secret-not-for-response\", broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-not-for-response"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("com.fasterxml"))));
    }
    @Test void absentJsonBodyReturns400() throws Exception {
        mvc.perform(post("/api/v1/users/login").contentType("application/json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }
    @Test void wrongContentTypeReturns415() throws Exception {
        mvc.perform(post("/api/v1/users/login").contentType("text/plain").content("hello"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value(415));
    }
    @Test void missingUploadFileReturns400() throws Exception {
        mvc.perform(multipart("/api/v1/users/upload-profile-image").with(user("buyer").roles("USER")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }
    @Test void unknownPublicPathReturns404() throws Exception {
        mvc.perform(get("/api/v1/categories/1/no-such-route"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
    }
    @Test void unsupportedMethodIncludesAllowHeader() throws Exception {
        mvc.perform(patch("/api/v1/categories/1").with(user("operator").roles("ADMIN")))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value(405))
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")));
    }
}
