package com.manh.ecom_be.services.orders;

import java.math.BigDecimal;


import com.manh.ecom_be.dtos.CartItemDTO;
import com.manh.ecom_be.dtos.OrderDTO;


import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.models.*;
import com.manh.ecom_be.repositories.CouponRepository;

import com.manh.ecom_be.repositories.OrderRepository;
import com.manh.ecom_be.repositories.ProductRepository;
import com.manh.ecom_be.repositories.UserRepository;
import com.manh.ecom_be.responses.order.OrderResponse;
import com.manh.ecom_be.components.metrics.BusinessMetrics;
import com.manh.ecom_be.components.TransactionCallbacks;
import com.manh.ecom_be.exceptions.OutOfStockException;
import com.manh.ecom_be.services.email.OrderPlaced;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService implements InterfaceOrderService {
    private final com.manh.ecom_be.components.OrderAccess orderAccess;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;


    private final ModelMapper modelMapper;
    private final BusinessMetrics businessMetrics;
    private final ApplicationEventPublisher eventPublisher;
    private final com.manh.ecom_be.services.coupon.InterfaceCouponService couponService;
    private final com.manh.ecom_be.services.product.InterfaceProductRedisService productRedisService;

    /**
     * Optional Redisson client for distributed locking.
     * If Redis/Redisson is disabled (e.g., test environment), this is null
     * and stock deduction falls back to database-level transaction isolation.
     */
    @Autowired(required = false)
    private RedissonClient redissonClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(OrderDTO orderDTO) throws Exception {
        orderAccess.requireOwnerOrAdmin(orderDTO.getUserId());
        if ((orderDTO.getPaymentMethod() != null && !orderDTO.getPaymentMethod().isBlank()
                && !"cod".equalsIgnoreCase(orderDTO.getPaymentMethod()))
                || (orderDTO.getVnpTxnRef() != null && !orderDTO.getVnpTxnRef().isBlank())) {
            throw new com.manh.ecom_be.exceptions.InvalidParamException("Only COD checkout is supported");
        }
        orderDTO.setPaymentMethod("cod");
        User user = userRepository
                .findById(orderDTO.getUserId())
                .orElseThrow(() -> new DataNotFoundException("User not found: " + orderDTO.getUserId()));

        modelMapper.typeMap(OrderDTO.class, Order.class)
                .addMappings(mapper -> mapper.skip(Order::setId));
        Order order = new Order();
        modelMapper.map(orderDTO, order);
        order.setUser(user);
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING);

        LocalDate shippingDate = orderDTO.getShippingDate() == null
                ? LocalDate.now() : orderDTO.getShippingDate();
        if (shippingDate.isBefore(LocalDate.now())) {
            throw new DataNotFoundException("Date must be at least today");
        }
        order.setShippingDate(shippingDate);
        order.setActive(true);

        order.setTotalMoney(BigDecimal.ZERO);

        if (orderDTO.getVnpTxnRef() != null) {
            order.setVnpTxnRef(orderDTO.getVnpTxnRef());
        }

        if (orderDTO.getShippingAddress() == null) {
            order.setShippingAddress(orderDTO.getAddress());
        }

        if (orderDTO.getCartItems() == null || orderDTO.getCartItems().isEmpty()) {
            throw new IllegalArgumentException("Cart must not be empty");
        }
        // Aggregate duplicates and lock in a stable order to avoid cross-cart deadlocks.
        java.util.Map<Long, Integer> quantities = new java.util.TreeMap<>();
        for (CartItemDTO item : orderDTO.getCartItems()) {
            if (item == null || item.getProductId() == null || item.getQuantity() == null
                    || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Product and positive quantity are required");
            }
            try {
                quantities.merge(item.getProductId(), item.getQuantity(), Math::addExact);
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException("Combined product quantity is too large", ex);
            }
        }
        List<OrderDetail> orderDetails = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var item : quantities.entrySet()) {
            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setOrder(order);

            Long productId = item.getKey();
            int quantity = item.getValue();

            // Watchdog renewal avoids a fixed lease expiring during a slow transaction.
            // A database row lock also protects writes if a Redis lock is ever lost.
            RLock lock = (redissonClient != null)
                    ? redissonClient.getLock("order:stock:" + productId)
                    : null;
            if (lock != null) {
                try {
                    if (!lock.tryLock(5, TimeUnit.SECONDS)) {
                        throw new IllegalArgumentException("Product is busy; retry the order");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void afterCompletion(int status) {
                            if (lock.isHeldByCurrentThread()) lock.unlock();
                        }
                    });
                }
            }
            try {
                Product product = productRepository.findByIdForUpdate(productId)
                        .orElseThrow(() -> new DataNotFoundException("Product not found with id: " + productId));
                if (product.getStockQuantity() < quantity) throw new OutOfStockException(productId);
                product.setStockQuantity(product.getStockQuantity() - quantity);
                orderDetail.setProduct(product);
                orderDetail.setProductNameSnapshot(product.getName());
                orderDetail.setProductThumbnailSnapshot(product.getThumbnail());
                orderDetail.setNumberOfProducts(quantity);
                orderDetail.setPrice(product.getPrice());
                BigDecimal lineTotal = com.manh.ecom_be.utils.Money.amount(product.getPrice())
                        .multiply(BigDecimal.valueOf(quantity));
                orderDetail.setTotalMoney(com.manh.ecom_be.utils.Money.amount(lineTotal));
                total = total.add(lineTotal);
                orderDetails.add(orderDetail);
            } finally {
                if (lock != null && !TransactionSynchronizationManager.isSynchronizationActive()
                        && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }

        String couponCode = orderDTO.getCouponCode();
        if (couponCode != null && !couponCode.isBlank()) {
            Coupon coupon = couponRepository.findByCode(couponCode)
                    .orElseThrow(() -> new IllegalArgumentException("Coupon not found"));
            if (!coupon.isActive()) {
                throw new IllegalArgumentException("Coupon is not active");
            }
            order.setCoupon(coupon);
            total = couponService.calculateCouponValue(couponCode, total);
        } else {
            order.setCoupon(null);
        }

        order.setTotalMoney(com.manh.ecom_be.utils.Money.amount(total));
        order.setOrderDetails(orderDetails);
        orderRepository.save(order);
        eventPublisher.publishEvent(new OrderPlaced(order.getEmail(), order.getId(), order.getTotalMoney()));
        TransactionCallbacks.afterCommit(businessMetrics::incrementOrdersCreated);
        log.info("Order created successfully: orderId={}, userId={}", order.getId(), order.getUser().getId());
        return order;
    }

    @Override
    public Order getOrderById(Long orderId) throws DataNotFoundException {
        orderAccess.requireUser();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new DataNotFoundException("Order not found: " + orderId));
        orderAccess.requireOrder(order);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order cancelOrder(Long id) throws DataNotFoundException {
        orderAccess.requireUser();
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new DataNotFoundException("Order not found: " + id));
        // Check ownership while holding the same lock used for the transition.
        orderAccess.requireOrder(order);
        applyStatus(order, OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order updateOrder(Long id, OrderDTO orderDTO) throws DataNotFoundException {
        orderAccess.requireAdmin();
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new DataNotFoundException("Order not found: " + id));
        User existingUser = userRepository.findById(
                orderDTO.getUserId()).orElseThrow(() ->
                new DataNotFoundException("Cannot find user with id: " + id));

        if (orderDTO.getUserId() != null) {
            User user = new User();
            user.setId(orderDTO.getUserId());
            order.setUser(user);
        }

        if (orderDTO.getFullName() != null && !orderDTO.getFullName().trim().isEmpty()) {
            order.setFullName(orderDTO.getFullName().trim());
        }

        if (orderDTO.getEmail() != null && !orderDTO.getEmail().trim().isEmpty()) {
            order.setEmail(orderDTO.getEmail().trim());
        }

        if (orderDTO.getPhoneNumber() != null && !orderDTO.getPhoneNumber().trim().isEmpty()) {
            order.setPhoneNumber(orderDTO.getPhoneNumber().trim());
        }

        if (orderDTO.getStatus() != null && !orderDTO.getStatus().trim().isEmpty()) {
            applyStatus(order, orderDTO.getStatus().trim());
        }

        if (orderDTO.getAddress() != null && !orderDTO.getAddress().trim().isEmpty()) {
            order.setAddress(orderDTO.getAddress().trim());
        }

        if (orderDTO.getNote() != null && !orderDTO.getNote().trim().isEmpty()) {
            order.setNote(orderDTO.getNote().trim());
        }

        if (orderDTO.getShippingMethod() != null && !orderDTO.getShippingMethod().trim().isEmpty()) {
            order.setShippingMethod(orderDTO.getShippingMethod().trim());
        }

        if (orderDTO.getShippingAddress() != null && !orderDTO.getShippingAddress().trim().isEmpty()) {
            order.setShippingAddress(orderDTO.getShippingAddress().trim());
        }

        if (orderDTO.getShippingDate() != null) {
            order.setShippingDate(orderDTO.getShippingDate());
        }

        if (orderDTO.getPaymentMethod() != null && !orderDTO.getPaymentMethod().trim().isEmpty()) {
            order.setPaymentMethod(orderDTO.getPaymentMethod().trim());
        }

        order.setUser(existingUser);
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public void deleteOrder(Long orderId) {
        orderAccess.requireAdmin();
        Order order = orderRepository.findById(orderId).orElse(null);

        if (order != null) {
            order.setActive(false);
            orderRepository.save(order);
        }
    }

    @Override
    public List<OrderResponse> findByUserId(Long userId) {
        orderAccess.requireOwnerOrAdmin(userId);
        List<Order> orders = orderRepository.findByUserId(userId);
        return orders.stream().map(order -> OrderResponse.fromOrder(order)).toList();
    }

    @Override
    public Page<Order> getOrdersByKeyword(String keyword, Pageable pageable) {
        orderAccess.requireAdmin();
        return orderRepository.findByKeyword(keyword, pageable);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order updateOrderStatus(Long id, String status) throws DataNotFoundException, IllegalArgumentException {
        orderAccess.requireAdmin();
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new DataNotFoundException("Order not found: " + id));
        applyStatus(order, status);
        return orderRepository.save(order);
    }

    private void applyStatus(Order order, String status) throws DataNotFoundException {
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Status cannot be null or empty");
        }

        if (!OrderStatus.VALID_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Invalid status " + status);
        }

        String currentStatus = order.getStatus();
        if (status.equals(currentStatus)) return;
        boolean allowed = switch (currentStatus) {
            case OrderStatus.PENDING -> status.equals(OrderStatus.PROCESSING) || status.equals(OrderStatus.CANCELLED);
            case OrderStatus.PROCESSING -> status.equals(OrderStatus.SHIPPED) || status.equals(OrderStatus.CANCELLED);
            case OrderStatus.SHIPPED -> status.equals(OrderStatus.DELIVERED);
            default -> false;
        };
        if (!allowed) throw new IllegalArgumentException("Invalid order transition: " + currentStatus + " -> " + status);
        if (OrderStatus.CANCELLED.equals(status)) {
            // The order row lock makes repeated/concurrent cancellation idempotent.
            for (OrderDetail detail : order.getOrderDetails().stream()
                    .sorted(java.util.Comparator.comparing(d -> d.getProduct().getId())).toList()) {
                if (productRepository.restoreStock(detail.getProduct().getId(), detail.getNumberOfProducts()) != 1) {
                    throw new DataNotFoundException("Product no longer exists");
                }
            }
            productRedisService.clear();
        }
        order.setStatus(status);
        if (OrderStatus.CANCELLED.equals(status)) {
            TransactionCallbacks.afterCommit(businessMetrics::incrementOrdersCancelled);
            log.info("Order cancelled: orderId={}", order.getId());
        }
    }
}
