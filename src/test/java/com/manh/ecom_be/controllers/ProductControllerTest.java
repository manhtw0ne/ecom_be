package com.manh.ecom_be.controllers;

import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.models.Product;
import com.manh.ecom_be.services.product.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ProductService products;
    @MockBean com.manh.ecom_be.services.product.ProductRedisService productCache;

    @Test void emptyCachedPageKeepsPaginationMetadataWithoutQueryingDatabase() throws Exception {
        when(productCache.getAllProducts(anyString(), anyLong(), any())).thenReturn(
                new com.manh.ecom_be.responses.product.ProductListResponse(List.of(), 4));
        mvc.perform(get("/api/v1/products").param("page", "9"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.products").isEmpty())
                .andExpect(jsonPath("$.data.totalPages").value(4));
        verifyNoInteractions(products);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"-1,10", "0,0", "0,101", "100001,10"})
    void excessivePaginationIsRejectedBeforeCacheOrDatabase(int page, int limit) throws Exception {
        mvc.perform(get("/api/v1/products").param("page", Integer.toString(page)).param("limit", Integer.toString(limit)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(products, productCache);
    }
    private static final String VALID = "{\"name\":\"Test product\",\"price\":100,\"category_id\":1,\"stock_quantity\":5}";

    @Test void publicListingReturnsPaginatedEnvelope() throws Exception {
        when(products.getAllProducts(anyString(), anyLong(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.products").isArray());
    }
    @Test void anonymousCannotCreateProduct() throws Exception {
        mvc.perform(post("/api/v1/products").contentType("application/json").content(VALID))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(products);
    }
    @Test @WithMockUser(roles = "USER")
    void buyerCannotCreateProduct() throws Exception {
        mvc.perform(post("/api/v1/products").contentType("application/json").content(VALID))
                .andExpect(status().isForbidden());
        verifyNoInteractions(products);
    }
    @Test @WithMockUser(roles = "ADMIN")
    void adminCreateReturns201() throws Exception {
        when(products.createProduct(any())).thenReturn(Product.builder().id(1L).name("Test product").stockQuantity(5).build());
        mvc.perform(post("/api/v1/products").contentType("application/json").content(VALID))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(201));
    }
    @Test @WithMockUser(roles = "ADMIN")
    void invalidStockReturns400() throws Exception {
        mvc.perform(post("/api/v1/products").contentType("application/json")
                        .content(VALID.replace("\"stock_quantity\":5", "\"stock_quantity\":-1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(products);
    }
    @Test void missingProductReturns404() throws Exception {
        when(products.getProductById(999L)).thenThrow(new DataNotFoundException("Product not found"));
        mvc.perform(get("/api/v1/products/999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
    @Autowired com.manh.ecom_be.repositories.UserRepository users;
    @Autowired com.manh.ecom_be.repositories.RoleRepository roles;
    @Autowired com.manh.ecom_be.components.JwtTokenUtils jwt;
    @Autowired com.manh.ecom_be.services.token.TokenService tokens;

    @Test void localJwtCanAccessProtectedApiWithoutGoogleIntrospection() throws Exception {
        var role = roles.save(com.manh.ecom_be.models.Role.builder().name("USER").build());
        var user = users.save(com.manh.ecom_be.models.User.builder()
                .email("jwt-" + System.nanoTime() + "@example.test").password("unused")
                .active(true).role(role).build());
        String token = jwt.generateToken(user);
        tokens.addToken(user, token, false);
        mvc.perform(post("/api/v1/users/details").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
    }    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"-0.01", "1.001", "10000000.01", "null"})
    @WithMockUser(roles = "ADMIN")
    void invalidDecimalPriceReturns400(String price) throws Exception {
        mvc.perform(post("/api/v1/products").contentType("application/json")
                .content(VALID.replace("\"price\":100", "\"price\":" + price)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(products);
    }}
