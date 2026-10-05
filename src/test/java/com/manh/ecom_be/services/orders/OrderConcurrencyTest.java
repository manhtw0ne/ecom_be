package com.manh.ecom_be.services.orders;

import java.math.BigDecimal;

import com.manh.ecom_be.dtos.CartItemDTO;
import com.manh.ecom_be.dtos.OrderDTO;
import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.exceptions.OutOfStockException;
import com.manh.ecom_be.models.*;
import com.manh.ecom_be.repositories.*;
import com.manh.ecom_be.services.email.OrderPlaced;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(OrderConcurrencyTest.EventConfig.class)
class OrderConcurrencyTest {
    // These tests isolate transaction/inventory behavior; HTTP tests exercise real authorization.
    @org.springframework.boot.test.mock.mockito.MockBean com.manh.ecom_be.components.OrderAccess orderAccess;
    @Autowired OrderService service;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CategoryRepository categories;
    @Autowired OrderRepository orders;
    @Autowired CouponRepository coupons;
    @Autowired CouponConditionRepository couponConditions;
    @Autowired CommittedEvents events;
    private User user;
    private Product product;

    @BeforeEach
    void seed() {
        Role role = roles.save(Role.builder().name("USER").build());
        user = users.save(User.builder().fullName("Buyer").phoneNumber("0123456789")
                .email("buyer@example.test").password("unused").active(true).role(role).build());
        Category category = categories.save(Category.builder().name("Category " + System.nanoTime()).build());
        product = products.save(Product.builder().name("Limited product").price(new BigDecimal("100"))
                .stockQuantity(5).category(category).build());
        events.count.set(0);
    }

    private OrderDTO cart(int quantity) {
        return OrderDTO.builder().userId(user.getId()).phoneNumber("0123456789")
                .email("buyer@example.test").totalMoney(new BigDecimal("1"))
                .cartItems(List.of(CartItemDTO.builder().productId(product.getId()).quantity(quantity).build()))
                .build();
    }

    @Test
    void twentyConcurrentOrdersCannotSellMoreThanFiveUnits() throws Exception {
        long before = orders.count();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
            var futures = new java.util.ArrayList<Future<Boolean>>();
            for (int i = 0; i < 20; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try { service.createOrder(cart(1)); return true; }
                    catch (OutOfStockException ex) { return false; }
                }));
            }
            start.countDown();
            int successful = 0;
            for (Future<Boolean> result : futures) if (result.get(30, TimeUnit.SECONDS)) successful++;
            assertThat(successful).isEqualTo(5);
        }
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isZero();
        assertThat(orders.count() - before).isEqualTo(5);
        assertThat(events.count.get()).isEqualTo(5);
    }

    @Test
    void missingSecondProductRollsBackPreviouslyDeductedStockAndDoesNotSendEmail() {
        OrderDTO dto = cart(2);
        dto.setCartItems(List.of(dto.getCartItems().getFirst(),
                CartItemDTO.builder().productId(Long.MAX_VALUE).quantity(1).build()));
        long before = orders.count();
        assertThatThrownBy(() -> service.createOrder(dto)).isInstanceOf(DataNotFoundException.class);
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
        assertThat(orders.count()).isEqualTo(before);
        assertThat(events.count.get()).isZero();
    }

    @Test
    void duplicateItemsAreAggregatedAndClientTotalIsIgnored() throws Exception {
        OrderDTO dto = cart(2);
        dto.setCartItems(List.of(dto.getCartItems().getFirst(), dto.getCartItems().getFirst()));
        Order order = service.createOrder(dto);
        assertThat(order.getTotalMoney()).isEqualByComparingTo("400");
        assertThat(order.getOrderDetails()).hasSize(1);
        assertThat(order.getOrderDetails().getFirst().getTotalMoney()).isEqualByComparingTo("400");
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(1);
    }

    @Test
    void cancellationRestocksExactlyOnceUnderConcurrentRequests() throws Exception {
        Long id = service.createOrder(cart(2)).getId();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<Void> cancel = () -> { start.await(); service.cancelOrder(id); return null; };
            Future<Void> first = pool.submit(cancel);
            Future<Void> second = pool.submit(cancel);
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
        assertThat(orders.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.updateOrderStatus(id, OrderStatus.PROCESSING))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeQuantityCannotIncreaseStock() {
        assertThatThrownBy(() -> service.createOrder(cart(-2))).isInstanceOf(IllegalArgumentException.class);
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
    }

    @TestConfiguration
    static class EventConfig {
        @Bean CommittedEvents committedEvents() { return new CommittedEvents(); }
    }
    static class CommittedEvents {
        final AtomicInteger count = new AtomicInteger();
        @TransactionalEventListener
        public void record(OrderPlaced event) { count.incrementAndGet(); }
    }
    @Test
    void decimalOrderPersistsExactSnapshotAndIgnoresClientTotal() throws Exception {
        product.setPrice(new BigDecimal("9999999.99"));
        products.saveAndFlush(product);
        Order created = service.createOrder(cart(3));
        Order stored = orders.findById(created.getId()).orElseThrow();
        assertThat(stored.getTotalMoney()).isEqualByComparingTo("29999999.97");
        assertThat(created.getOrderDetails().getFirst().getPrice()).isEqualByComparingTo("9999999.99");
        assertThat(created.getOrderDetails().getFirst().getTotalMoney()).isEqualByComparingTo("29999999.97");
        product = products.findById(product.getId()).orElseThrow();
        product.setPrice(new BigDecimal("1.00"));
        products.saveAndFlush(product);
        assertThat(orders.findById(created.getId()).orElseThrow().getTotalMoney())
                .isEqualByComparingTo("29999999.97");
    }

    @Test
    void invalidCouponRollsBackDeductedStockAndOrder() {
        OrderDTO dto = cart(2);
        dto.setCouponCode("DOES-NOT-EXIST");
        long before = orders.count();
        assertThatThrownBy(() -> service.createOrder(dto)).isInstanceOf(IllegalArgumentException.class);
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
        assertThat(orders.count()).isEqualTo(before);
        assertThat(events.count.get()).isZero();
    }    @Test
    void couponUsesExactSubtotalAndPersistsRoundedPayableAmount() throws Exception {
        product.setPrice(new BigDecimal("19.99"));
        products.saveAndFlush(product);
        var coupon = coupons.save(Coupon.builder().code("DECIMAL-" + System.nanoTime()).active(true).build());
        couponConditions.save(CouponCondition.builder().coupon(coupon).attribute("minimum_amount")
                .operator(">").value("0").discountAmount(new BigDecimal("12.5")).build());
        var dto = cart(3);
        dto.setCouponCode(coupon.getCode());
        var created = service.createOrder(dto);
        assertThat(created.getOrderDetails().getFirst().getTotalMoney()).isEqualByComparingTo("59.97");
        assertThat(orders.findById(created.getId()).orElseThrow().getTotalMoney()).isEqualByComparingTo("52.47");
    }    @Test
    void duplicateQuantityOverflowIsRejectedBeforeStockWrites() {
        var dto = cart(Integer.MAX_VALUE);
        dto.setCartItems(List.of(dto.getCartItems().getFirst(), dto.getCartItems().getFirst()));
        long before = orders.count();
        assertThatThrownBy(() -> service.createOrder(dto))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("quantity is too large");
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
        assertThat(orders.count()).isEqualTo(before);
    }}
