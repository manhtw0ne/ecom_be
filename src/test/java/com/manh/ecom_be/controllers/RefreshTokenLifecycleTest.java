package com.manh.ecom_be.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manh.ecom_be.components.JwtTokenUtils;
import com.manh.ecom_be.dtos.UpdateUserDTO;
import com.manh.ecom_be.models.*;
import com.manh.ecom_be.repositories.*;
import com.manh.ecom_be.services.token.TokenService;
import com.manh.ecom_be.services.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Commits real service transactions so rotation and racing requests exercise row locks. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenLifecycleTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TokenService service;
    @Autowired UserService userService;
    @Autowired JwtTokenUtils jwt;
    @Autowired TokenRepository tokens;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Value("${jwt.secretKey}") String secret;
    User owner;
    Token session;

    @BeforeEach void seed() throws Exception {
        var role = roles.save(Role.builder().name("USER").build());
        owner = users.save(User.builder().email("refresh-" + System.nanoTime() + "@example.test")
                .password("unused").active(true).role(role).build());
        session = service.addToken(owner, jwt.generateToken(owner), false);
    }

    private String body(String refresh) throws Exception {
        return json.writeValueAsString(Map.of("refreshToken", refresh));
    }

    @Test void expiredAccessCanRefreshWithoutAuthorizationHeader() throws Exception {
        String expired = io.jsonwebtoken.Jwts.builder().subject(owner.getUsername())
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        io.jsonwebtoken.io.Decoders.BASE64.decode(secret))).compact();
        session.setToken(expired);
        session.setExpirationDate(LocalDateTime.now().minusMinutes(1));
        tokens.saveAndFlush(session);
        mvc.perform(post("/api/v1/users/details").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        String response = mvc.perform(post("/api/v1/users/refreshToken").contentType("application/json")
                        .content(body(session.getRefreshToken())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(owner.getId()))
                .andReturn().getResponse().getContentAsString();
        String access = json.readTree(response).path("data").path("token").asText();
        mvc.perform(post("/api/v1/users/details").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
    }

    @Test void rotationRejectsOldRefreshAndAccessAndKeepsAbsoluteDeadline() throws Exception {
        String oldAccess = session.getToken();
        String oldRefresh = session.getRefreshToken();
        var deadline = tokens.findById(session.getId()).orElseThrow().getRefreshExpirationDate();
        var response = mvc.perform(post("/api/v1/users/refreshToken").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("refresh_token", oldRefresh, "user_id", 99999))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var data = json.readTree(response).path("data");
        assertThat(data.path("token").asText()).isNotEqualTo(oldAccess);
        assertThat(data.path("refresh_token").asText()).isNotEqualTo(oldRefresh);
        assertThat(data.path("id").asLong()).isEqualTo(owner.getId());
        assertThat(tokens.findById(session.getId()).orElseThrow().getRefreshExpirationDate()).isEqualTo(deadline);
        mvc.perform(post("/api/v1/users/refreshToken").contentType("application/json").content(body(oldRefresh)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/users/details").header("Authorization", "Bearer " + oldAccess))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings = {"revoked", "expired", "deadline", "nullDeadline", "blocked", "deleted"})
    void invalidSessionsCannotRotate(String reason) throws Exception {
        switch (reason) {
            case "revoked" -> session.setRevoked(true);
            case "expired" -> session.setExpired(true);
            case "deadline" -> session.setRefreshExpirationDate(LocalDateTime.now().minusSeconds(1));
            case "nullDeadline" -> session.setRefreshExpirationDate(null);
            case "blocked" -> { owner.setActive(false); users.saveAndFlush(owner); }
            case "deleted" -> { owner.setDeleted(true); users.saveAndFlush(owner); }
        }
        if (!"deleted".equals(reason)) tokens.saveAndFlush(session);
        mvc.perform(post("/api/v1/users/refreshToken").contentType("application/json")
                        .content(body(session.getRefreshToken())))
                .andExpect(status().isUnauthorized());
        if (!"deleted".equals(reason)) {
            assertThat(tokens.findById(session.getId()).orElseThrow().getRefreshToken())
                    .isEqualTo(session.getRefreshToken());
        }
    }

    @Test void missingOrBlankCredentialHasPredictableStatus() throws Exception {
        mvc.perform(post("/api/v1/users/refreshToken").contentType("application/json").content(body("unknown")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/users/refreshToken").contentType("application/json").content(body(" ")))
                .andExpect(status().isBadRequest());
    }

    @Test void simultaneousRotationHasExactlyOneWinner() throws Exception {
        String refresh = session.getRefreshToken();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> attempt = () -> {
                start.await();
                try { service.refreshToken(refresh); return true; }
                catch (BadCredentialsException ex) { return false; }
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            start.countDown();
            int wins = (first.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(wins).isEqualTo(1);
        }
        assertThat(tokens.findByUser(owner)).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings = {"reset", "change", "block"})
    void accountSecurityChangesInvalidateBothCredentials(String action) throws Exception {
        User actor = "change".equals(action) ? owner : User.builder().id(Long.MAX_VALUE)
                .active(true).role(Role.builder().name("ADMIN").build()).build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        actor, null, actor.getAuthorities()));
        try {
        switch (action) {
            case "reset" -> userService.resetPassword(owner.getId(), "new-secret");
            case "change" -> userService.updateUser(owner.getId(), UpdateUserDTO.builder()
                    .password("new-secret").retypePassword("new-secret").build());
            case "block" -> {
                userService.blockOrEnable(owner.getId(), false);
                userService.blockOrEnable(owner.getId(), true);
            }
        }
        } finally {
            org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        }
        mvc.perform(post("/api/v1/users/refreshToken").contentType("application/json")
                        .content(body(session.getRefreshToken())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/users/details").header("Authorization", "Bearer " + session.getToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test void expiredFlagAlsoRejectsAccessToken() throws Exception {
        session.setExpired(true);
        tokens.saveAndFlush(session);
        mvc.perform(post("/api/v1/users/details").header("Authorization", "Bearer " + session.getToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test void tokensIssuedWithinTheSameSecondAreDistinct() throws Exception {
        assertThat(jwt.generateToken(owner)).isNotEqualTo(jwt.generateToken(owner));
    }
}