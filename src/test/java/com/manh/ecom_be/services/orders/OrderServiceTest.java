package com.manh.ecom_be.services.orders;

import java.math.BigDecimal;

import com.manh.ecom_be.dtos.CartItemDTO;
import com.manh.ecom_be.dtos.OrderDTO;
import com.manh.ecom_be.components.metrics.BusinessMetrics;
import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.models.*;
import com.manh.ecom_be.repositories.*;
import com.manh.ecom_be.responses.order.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Unit Tests")
class OrderServiceTest {

    @Mock private com.manh.ecom_be.components.OrderAccess orderAccess;
    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CouponRepository couponRepository;
    @Mock private OrderDetailRepository orderDetailRepository;
    @Mock private ModelMapper modelMapper;
    @Mock private BusinessMetrics businessMetrics;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private com.manh.ecom_be.services.coupon.InterfaceCouponService couponService;
    @Mock private com.manh.ecom_be.services.product.InterfaceProductRedisService productRedisService;

    @InjectMocks
    private OrderService orderService;

    private User testUser;
    private Product testProduct;
    private Order testOrder;
    private OrderDTO testOrderDTO;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .fullName("Test User")
                .phoneNumber("0123456789")
                .build();

        testProduct = Product.builder()
                .id(1L)
                .name("Test Product")
                .price(new BigDecimal("100.0"))
                .stockQuantity(5)
                .comments(new ArrayList<>())
                .favorites(new ArrayList<>())
                .productImages(new ArrayList<>())
                .build();

        testOrder = Order.builder()
                .id(1L)
                .user(testUser)
                .fullName("Test User")
                .phoneNumber("0123456789")
                .status(OrderStatus.PENDING)
                .active(true)
                .totalMoney(new BigDecimal("100.0"))
                .orderDetails(new ArrayList<>())
                .build();

        testOrderDTO = OrderDTO.builder()
                .userId(1L)
                .fullName("Test User")
                .phoneNumber("0123456789")
                .address("123 Test St")
                .totalMoney(new BigDecimal("100.0"))
                .shippingMethod("express")
                .shippingAddress("123 Test St")
                .shippingDate(LocalDate.now().plusDays(1))
                .paymentMethod("cod")
                .couponCode("")
                .cartItems(List.of(
                        CartItemDTO.builder().productId(1L).quantity(2).build()
                ))
                .build();
    }

    // ─────────────── CREATE ───────────────

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("should create order successfully with valid data")
        void createOrder_validDTO_shouldReturnOrder() throws Exception {
            // Mock ModelMapper typeMap
            TypeMap<OrderDTO, Order> typeMap = mock(TypeMap.class);
            when(modelMapper.typeMap(OrderDTO.class, Order.class)).thenReturn(typeMap);
            doNothing().when(modelMapper).map(any(OrderDTO.class), any(Order.class));

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testProduct));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            Order result = orderService.createOrder(testOrderDTO);

            assertThat(result).isNotNull();
            assertThat(result.getUser()).isEqualTo(testUser);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(result.getActive()).isTrue();
            verify(orderRepository).save(any(Order.class));
            assertThat(testProduct.getStockQuantity()).isEqualTo(3);
            assertThat(result.getTotalMoney()).isEqualByComparingTo("200");
            assertThat(result.getOrderDetails()).hasSize(1);
            assertThat(result.getOrderDetails().getFirst().getTotalMoney()).isEqualByComparingTo("200");
        }

        @Test
        @DisplayName("should throw DataNotFoundException when user does not exist")
        void createOrder_invalidUser_shouldThrowDataNotFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.createOrder(testOrderDTO))
                    .isInstanceOf(DataNotFoundException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("should throw exception when shipping date is in the past")
        void createOrder_pastShippingDate_shouldThrowException() {
            testOrderDTO.setShippingDate(LocalDate.now().minusDays(1));

            // Mock ModelMapper
            TypeMap<OrderDTO, Order> typeMap = mock(TypeMap.class);
            when(modelMapper.typeMap(OrderDTO.class, Order.class)).thenReturn(typeMap);
            doNothing().when(modelMapper).map(any(OrderDTO.class), any(Order.class));

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            assertThatThrownBy(() -> orderService.createOrder(testOrderDTO))
                    .isInstanceOf(DataNotFoundException.class)
                    .hasMessageContaining("Date must be at least today");
        }
    }

    // ─────────────── READ ───────────────

    @Test
    @DisplayName("getOrderById should return order when it exists")
    void getOrderById_existingId_shouldReturnOrder() throws Exception {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        Order result = orderService.getOrderById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    // ─────────────── UPDATE ───────────────

    @Test
    @DisplayName("updateOrder should update fields correctly")
    void updateOrder_validDTO_shouldUpdateFields() throws Exception {
        OrderDTO updateDTO = OrderDTO.builder()
                .userId(1L)
                .fullName("Updated Name")
                .phoneNumber("0987654321")
                .status(OrderStatus.PROCESSING)
                .build();

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testOrder));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.updateOrder(1L, updateDTO);

        assertThat(result).isNotNull();
        assertThat(result.getFullName()).isEqualTo("Updated Name");
        assertThat(result.getPhoneNumber()).isEqualTo("0987654321");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    // ─────────────── DELETE ───────────────

    @Test
    @DisplayName("deleteOrder should set active to false (soft delete)")
    void deleteOrder_shouldSetActiveFalse() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.deleteOrder(1L);

        assertThat(testOrder.getActive()).isFalse();
        verify(orderRepository).save(testOrder);
    }

    // ─────────────── STATUS ───────────────

    @Nested
    @DisplayName("updateOrderStatus")
    class UpdateOrderStatus {

        @Test
        @DisplayName("should update status with valid transition")
        void updateOrderStatus_validTransition_shouldUpdate() throws Exception {
            testOrder.setStatus(OrderStatus.PENDING);

            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Order result = orderService.updateOrderStatus(1L, OrderStatus.PROCESSING);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.PROCESSING);
            verify(orderRepository).save(testOrder);
        }

        @Test
        @DisplayName("should throw exception for invalid status value")
        void updateOrderStatus_invalidStatus_shouldThrowException() {
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testOrder));

            assertThatThrownBy(() -> orderService.updateOrderStatus(1L, "invalid_status"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid status");
        }
    }
}
