package com.manh.ecom_be.controllers;

import java.math.BigDecimal;

import com.manh.ecom_be.components.JwtTokenUtils;
import com.manh.ecom_be.models.*;
import com.manh.ecom_be.repositories.*;
import com.manh.ecom_be.services.orders.OrderService;
import com.manh.ecom_be.services.token.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real filters, authorization, services and H2 persistence; no mocked order service. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderAuthorizationTest {
    @Autowired MockMvc mvc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired OrderRepository orders;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired OrderDetailRepository details;
    @Autowired OrderService service;
    @Autowired JwtTokenUtils jwt;
    @Autowired TokenService tokens;
    User owner, stranger, admin;
    Order order;
    Product product;
    OrderDetail detail;

    @BeforeEach void seed() {
        var buyerRole = roles.save(Role.builder().name("USER").build());
        var adminRole = roles.save(Role.builder().name("ADMIN").build());
        owner = account("owner", buyerRole);
        stranger = account("stranger", buyerRole);
        admin = account("admin", adminRole);
        var category = categories.save(Category.builder().name("Authorization test").build());
        product = products.save(Product.builder().name("Test product").price(new BigDecimal("100"))
                .stockQuantity(3).category(category).build());
        order = orders.save(Order.builder().user(owner).phoneNumber("0901234567")
                .totalMoney(new BigDecimal("200")).status(OrderStatus.PENDING).active(true)
                .orderDetails(new ArrayList<>()).build());
        detail = details.save(OrderDetail.builder().order(order).product(product)
                .price(new BigDecimal("100")).numberOfProducts(2).totalMoney(new BigDecimal("200")).build());
        order.getOrderDetails().add(detail);
    }

    private User account(String name, Role role) {
        return users.save(User.builder().email(name + System.nanoTime() + "@example.test")
                .password("not-a-real-password").active(true).role(role).build());
    }

    private String path(String kind) {
        return switch (kind) {
            case "order" -> "/api/v1/orders/" + order.getId();
            case "list" -> "/api/v1/orders/user/" + owner.getId();
            case "detail" -> "/api/v1/order-details/" + detail.getId();
            case "details" -> "/api/v1/order-details/order/" + order.getId();
            default -> "/api/v1/orders/get-orders-by-keyword";
        };
    }

    @ParameterizedTest @ValueSource(strings = {"order", "list", "detail", "details", "search"})
    void anonymousCannotReadOrders(String kind) throws Exception {
        mvc.perform(get(path(kind))).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings = {"order", "list", "detail", "details", "search"})
    void anotherBuyerCannotReadOrders(String kind) throws Exception {
        mvc.perform(get(path(kind)).with(user(stranger)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
    }

    @ParameterizedTest @ValueSource(strings = {"order", "list", "detail", "details"})
    void ownerCanReadOwnOrders(String kind) throws Exception {
        mvc.perform(get(path(kind)).with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
    }

    @ParameterizedTest @ValueSource(strings = {"order", "list", "detail", "details", "search"})
    void adminCanReadOrders(String kind) throws Exception {
        mvc.perform(get(path(kind)).with(user(admin))).andExpect(status().isOk());
    }

    @Test void ownerCannotSearchAllOrders() throws Exception {
        mvc.perform(get(path("search")).with(user(owner))).andExpect(status().isForbidden());
    }

    @Test void ownerListDoesNotIncludeOtherBuyers() throws Exception {
        orders.save(Order.builder().user(stranger).phoneNumber("0901234567")
                .totalMoney(new BigDecimal("0")).status(OrderStatus.PENDING).active(true).build());
        mvc.perform(get(path("list")).with(user(owner)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].user_id").value(owner.getId()));
    }

    @Test void missingIdsReturn404InsteadOfNullPointer() throws Exception {
        for (String path : new String[]{"/orders/", "/order-details/", "/order-details/order/"}) {
            mvc.perform(get("/api/v1" + path + Long.MAX_VALUE).with(user(owner)))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(put("/api/v1/orders/cancel/" + Long.MAX_VALUE).with(user(owner)))
                .andExpect(status().isNotFound());
    }

    @Test void cancellationByAnotherBuyerDoesNotChangeStockOrStatus() throws Exception {
        mvc.perform(put("/api/v1/orders/cancel/" + order.getId()).with(user(stranger)))
                .andExpect(status().isForbidden());
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(3);
    }

    @Test void ownerCancellationIsIdempotent() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/v1/orders/cancel/" + order.getId()).with(user(owner)))
                    .andExpect(status().isOk());
        }
        // Bulk stock updates bypass the persistence context: read the database, not the seeded entity.
        entityManager.flush();
        entityManager.clear();
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test void shippedOrderCannotBeCancelled() throws Exception {
        order.setStatus(OrderStatus.SHIPPED);
        orders.saveAndFlush(order);
        mvc.perform(put("/api/v1/orders/cancel/" + order.getId()).with(user(owner)))
                .andExpect(status().isBadRequest());
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(3);
    }

    @Test void detailWritesAreDisabledForBuyerAndAdmin() throws Exception {
        String body = "{\"order_id\":" + order.getId() + ",\"product_id\":" + product.getId()
                + ",\"price\":1,\"number_of_products\":1,\"total_money\":1}";
        for (User actor : new User[]{owner, stranger, admin}) {
            mvc.perform(post("/api/v1/order-details").with(user(actor)).contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(put(path("detail")).with(user(actor)).contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(delete(path("detail")).with(user(actor))).andExpect(status().isForbidden());
        }
        assertThat(details.findById(detail.getId()).orElseThrow().getPrice()).isEqualByComparingTo("100");
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(3);
    }

    @Test void createIgnoresClientOwnerAndPrice() throws Exception {
        String body = "{\"user_id\":" + stranger.getId()
                + ",\"phone_number\":\"0901234567\",\"address\":\"Test address\",\"total_money\":1,"
                + "\"cart_items\":[{\"product_id\":" + product.getId() + ",\"quantity\":1}]}";
        mvc.perform(post("/api/v1/orders").with(user(owner)).contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.id").value(owner.getId()))
                .andExpect(jsonPath("$.data.user.password").doesNotExist())
                .andExpect(jsonPath("$.data.totalMoney").value(100));
    }

    @ParameterizedTest @ValueSource(strings = {"\"payment_method\":\"vnpay\"", "\"vnp_txn_ref\":\"forged-payment\""})
    void unsupportedPaymentCannotCreateOrderOrDeductStock(String field) throws Exception {
        long count = orders.count();
        String body = "{\"user_id\":" + owner.getId() + ",\"phone_number\":\"0901234567\",\"address\":\"Test\","
                + field + ",\"cart_items\":[{\"product_id\":" + product.getId() + ",\"quantity\":1}]}";
        mvc.perform(post("/api/v1/orders").with(user(owner)).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        assertThat(orders.count()).isEqualTo(count);
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(3);
    }

    @Test void experimentalPaymentEndpointsAreDisabledByDefault() throws Exception {
        mvc.perform(post("/api/v1/payments/refund").with(user(admin)).contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void checkoutKeepsProductSnapshotAfterCatalogChanges(boolean hadImage) throws Exception {
        product.setThumbnail(hadImage ? "original.png" : null);
        products.saveAndFlush(product);
        String body = "{\"user_id\":" + owner.getId()
                + ",\"phone_number\":\"0901234567\",\"address\":\"Test address\","
                + "\"cart_items\":[{\"product_id\":" + product.getId() + ",\"quantity\":1}]}";
        mvc.perform(post("/api/v1/orders").with(user(owner)).contentType("application/json").content(body))
                .andExpect(status().isCreated());
        var created = details.findAll().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()) && !item.getId().equals(detail.getId()))
                .findFirst().orElseThrow();
        Long createdId = created.getId();
        assertThat(created.getProductNameSnapshot()).isEqualTo("Test product");
        assertThat(created.getProductThumbnailSnapshot()).isEqualTo(hadImage ? "original.png" : null);
        product.setName("Renamed product");
        product.setThumbnail("new.png");
        product.setPrice(new BigDecimal("999"));
        products.saveAndFlush(product);
        entityManager.clear();
        var response = mvc.perform(get("/api/v1/order-details/" + createdId).with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.product_name").value("Test product"))
                .andExpect(jsonPath("$.data.price").value(100));
        if (hadImage) response.andExpect(jsonPath("$.data.thumbnail").value("original.png"));
        else response.andExpect(jsonPath("$.data.thumbnail").isEmpty());
    }

    @Test void legacyDetailWithoutSnapshotStillHasReadableResponse() throws Exception {
        mvc.perform(get(path("detail")).with(user(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.product_name").value("Test product"));
    }

    @Test void realJwtCannotReadAnotherBuyersOrder() throws Exception {
        String token = jwt.generateToken(stranger);
        tokens.addToken(stranger, token, false);
        mvc.perform(get(path("order")).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test void directServiceCallsCannotBypassControllerAuthorization() {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(stranger, null, stranger.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            assertThatThrownBy(() -> service.getOrderById(order.getId())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.findByUserId(owner.getId())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.cancelOrder(order.getId())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.updateOrderStatus(order.getId(), OrderStatus.SHIPPED))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.deleteOrder(order.getId())).isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
    @Test void inactiveUserIsDeniedEvenWithAnAuthenticationObject() throws Exception {
        owner.setActive(false);
        mvc.perform(get(path("order")).with(user(owner))).andExpect(status().isForbidden());
    }

    @Test void userCannotUseAdministrativeWrites() throws Exception {
        mvc.perform(put("/api/v1/orders/" + order.getId() + "/status")
                .param("status", OrderStatus.SHIPPED).with(user(owner)))
                .andExpect(status().isForbidden());
        mvc.perform(delete(path("order")).with(user(owner))).andExpect(status().isForbidden());
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test void ownerCanReadThroughRealJwtFilter() throws Exception {
        String token = jwt.generateToken(owner);
        tokens.addToken(owner, token, false);
        mvc.perform(get(path("order")).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user_id").value(owner.getId()));
    }
}
