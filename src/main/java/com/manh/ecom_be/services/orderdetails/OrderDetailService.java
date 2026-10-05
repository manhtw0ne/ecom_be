package com.manh.ecom_be.services.orderdetails;

import com.manh.ecom_be.components.OrderAccess;
import com.manh.ecom_be.dtos.OrderDetailDTO;
import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.models.Order;
import com.manh.ecom_be.models.OrderDetail;
import com.manh.ecom_be.repositories.OrderDetailRepository;
import com.manh.ecom_be.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderDetailService implements InterfaceOrderDetailService {
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final OrderAccess orderAccess;

    // Line items are snapshots created by OrderService alongside stock and totals.
    // Standalone writes would bypass those invariants, even for administrators.
    @Override
    public OrderDetail createOrderDetail(OrderDetailDTO dto) {
        throw standaloneWriteDenied();
    }

    @Override
    public OrderDetail updateOrderDetail(Long id, OrderDetailDTO dto) {
        throw standaloneWriteDenied();
    }

    @Override
    public void deleteById(Long id) {
        throw standaloneWriteDenied();
    }

    @Override
    public OrderDetail getOrderDetail(Long id) throws DataNotFoundException {
        orderAccess.requireUser();
        OrderDetail detail = orderDetailRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Order detail not found: " + id));
        orderAccess.requireOrder(detail.getOrder());
        return detail;
    }

    @Override
    public List<OrderDetail> findByOrderId(Long orderId) throws DataNotFoundException {
        orderAccess.requireUser();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new DataNotFoundException("Order not found: " + orderId));
        orderAccess.requireOrder(order);
        return orderDetailRepository.findByOrderId(orderId);
    }

    private AccessDeniedException standaloneWriteDenied() {
        return new AccessDeniedException("Manage line items through the order workflow");
    }
}