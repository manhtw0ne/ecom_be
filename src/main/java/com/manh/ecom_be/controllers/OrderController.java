package com.manh.ecom_be.controllers;


import com.manh.ecom_be.components.LocalizationUtils;
import com.manh.ecom_be.components.OrderAccess;
import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.dtos.OrderDTO;
import com.manh.ecom_be.models.Order;

import com.manh.ecom_be.models.User;
import com.manh.ecom_be.responses.ApiResponse;
import com.manh.ecom_be.responses.order.OrderResponse;
import com.manh.ecom_be.responses.order.OrderListResponse;
import com.manh.ecom_be.services.orders.InterfaceOrderService;
import com.manh.ecom_be.utils.MessageKeys;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${api.prefix}/orders")
@RequiredArgsConstructor
public class OrderController {
    private final InterfaceOrderService orderService;
    private final LocalizationUtils localizationUtils;
    private final OrderAccess orderAccess;

    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_USER') or hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Order>> createOrder(
            @Valid @RequestBody OrderDTO orderDTO
    ) throws Exception {
        User loginUser = orderAccess.requireUser();
        orderDTO.setUserId(loginUser.getId());
        Order orderResponse = orderService.createOrder(orderDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(orderResponse, "Insert order successfully"));
    }

    @GetMapping("/user/{user_id}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrders(
            @Valid @PathVariable("user_id") Long userId
    ) {
        User loginUser = orderAccess.requireUser();
        boolean isUserIdBlank = userId == null || userId == 0;
        List<OrderResponse> orderResponses = orderService.findByUserId(isUserIdBlank ? loginUser.getId() : userId);
        return ResponseEntity.ok(ApiResponse.success(orderResponses, "Get list of orders successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable("id") Long orderId) throws DataNotFoundException {
        Order existingOrder = orderService.getOrderById(orderId);
        OrderResponse orderResponse = OrderResponse.fromOrder(existingOrder);
        return ResponseEntity.ok(ApiResponse.success(orderResponse, "Get order successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Order>> updateOrder(
            @Valid @PathVariable long id,
            @Valid @RequestBody OrderDTO orderDTO
    ) throws Exception {
        Order order = orderService.updateOrder(id, orderDTO);
        return ResponseEntity.ok(ApiResponse.success(order, "Update order successfully"));
    }

    @PutMapping("/cancel/{id}")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<ApiResponse<?>> cancelOrder(
            @Valid @PathVariable long id
    ) throws Exception {
        Order order = orderService.cancelOrder(id);
        return ResponseEntity.ok(ApiResponse.success(order, "Cancel order successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<?>> deleteOrder(@Valid @PathVariable Long id) {
        orderService.deleteOrder(id);
        String message = localizationUtils.getLocalizedMessage(
            MessageKeys.DELETE_ORDER_SUCCESSFULLY, id);
        return ResponseEntity.ok(ApiResponse.success(null, message));
    }

    @GetMapping("/get-orders-by-keyword")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<OrderListResponse>> getOrdersByKeyword(
            @RequestParam(defaultValue = "", required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit
    ) {
        if (page < 0 || page > 100000 || limit < 1 || limit > 100 || keyword.length() > 200) {
            throw new com.manh.ecom_be.exceptions.InvalidParamException("Invalid page, limit or keyword");
        }
        PageRequest pageRequest = PageRequest.of(
                page, limit,
                Sort.by("id").ascending()
        );
        Page<OrderResponse> orderPage = orderService
                .getOrdersByKeyword(keyword, pageRequest)
                .map(OrderResponse::fromOrder);

        OrderListResponse response = OrderListResponse.builder()
                .orders(orderPage.getContent())
                .totalPages(orderPage.getTotalPages())
                .currentPage(page)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Get orders successfully"));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @Valid @PathVariable Long id,
            @RequestParam String status) throws Exception {
        Order updatedOrder = orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success(
                OrderResponse.fromOrder(updatedOrder), "Update order status successfully"));
    }
}

