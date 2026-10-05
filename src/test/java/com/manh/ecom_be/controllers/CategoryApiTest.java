package com.manh.ecom_be.controllers;

import com.manh.ecom_be.models.*;
import com.manh.ecom_be.repositories.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CategoryApiTest {
    @Autowired MockMvc mvc;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    Category category;
    User admin;
    @BeforeEach void seed() {
        category = categories.save(Category.builder().name("Category API").build());
        categories.save(Category.builder().name("Second").build());
        admin = users.save(User.builder().email("category" + System.nanoTime() + "@example.test")
                .password("hash").active(true).role(roles.save(Role.builder().name("ADMIN").build())).build());
    }
    @AfterEach void clear() { org.springframework.security.test.context.TestSecurityContextHolder.clearContext(); }

    @Test void listingUsesStableDatabasePagesAndKeepsArrayEnvelope() throws Exception {
        var expected = categories.findAll(org.springframework.data.domain.Sort.by("id"));
        mvc.perform(get("/api/v1/categories").param("page", "1").param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(expected.get(1).getId()))
                .andExpect(header().string("X-Total-Count", Long.toString(categories.count())))
                .andExpect(header().string("X-Total-Pages", Long.toString(categories.count())));
        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
    }

    @ParameterizedTest @CsvSource({"-1,10", "0,0", "0,101", "100001,10"})
    void invalidPagingReturns400(int page, int limit) throws Exception {
        mvc.perform(get("/api/v1/categories").param("page", Integer.toString(page)).param("limit", Integer.toString(limit)))
                .andExpect(status().isBadRequest());
    }

    @Test void missingCategoryReturns404ForReadUpdateDelete() throws Exception {
        mvc.perform(get("/api/v1/categories/9223372036854775807")).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/categories/9223372036854775807").with(user(admin))
                .contentType("application/json").content("{\"name\":\"Changed\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/categories/9223372036854775807").with(user(admin)))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void cannotDeleteCategoryReferencedByActiveOrHiddenProduct(boolean hidden) throws Exception {
        products.saveAndFlush(Product.builder().name("Referenced").category(category).price(BigDecimal.TEN)
                .stockQuantity(1).deleted(hidden).build());
        mvc.perform(delete("/api/v1/categories/" + category.getId()).with(user(admin)))
                .andExpect(status().isConflict());
        assertThat(categories.existsById(category.getId())).isTrue();
    }

    @Test void emptyCategoryCanBeDeleted() throws Exception {
        mvc.perform(delete("/api/v1/categories/" + category.getId()).with(user(admin))).andExpect(status().isOk());
        assertThat(categories.existsById(category.getId())).isFalse();
    }
}
