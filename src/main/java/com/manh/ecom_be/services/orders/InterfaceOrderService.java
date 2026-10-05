package com.manh.ecom_be.services.orders;

import com.manh.ecom_be.dtos.OrderDTO;
import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.models.Order;
import com.manh.ecom_be.responses.order.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface InterfaceOrderService {
    Order createOrder(OrderDTO orderDTO) throws Exception;
    Order getOrderById(Long orderId) throws DataNotFoundException;
    Order cancelOrder(Long id) throws DataNotFoundException;
    Order updateOrder(Long id, OrderDTO orderDTO) throws DataNotFoundException;
    void deleteOrder(Long orderId);
    List<OrderResponse> findByUserId(Long userId);
    Page<Order> getOrdersByKeyword(String keyword, Pageable pageable);
    Order updateOrderStatus(Long id, String status) throws DataNotFoundException;
}
