package com.manh.ecom_be.controllers;

import com.manh.ecom_be.components.LocalizationUtils;
import com.manh.ecom_be.dtos.OrderDetailDTO;
import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.models.OrderDetail;
import com.manh.ecom_be.responses.ApiResponse;
import com.manh.ecom_be.responses.order.OrderDetailResponse;
import com.manh.ecom_be.services.orderdetails.OrderDetailService;
import com.manh.ecom_be.utils.MessageKeys;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("${api.prefix}/order-details")
@RequiredArgsConstructor
public class OrderDetailController {
    private final OrderDetailService orderDetailService;
    private final LocalizationUtils localizationUtils;

    @PostMapping("")
    @PreAuthorize("denyAll()")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> createOrderDetail(
            @Valid @RequestBody OrderDetailDTO orderDetailDTO) throws Exception {
        OrderDetail newOrderDetail = orderDetailService.createOrderDetail(orderDetailDTO);
        OrderDetailResponse orderDetailResponse = OrderDetailResponse.fromOrderDetail(newOrderDetail);
        return ResponseEntity.ok(ApiResponse.created(orderDetailResponse, "Create order detail successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getOrderDetail(
            @Valid @PathVariable("id") Long id) throws DataNotFoundException {
        OrderDetail orderDetail = orderDetailService.getOrderDetail(id);
        OrderDetailResponse orderDetailResponse = OrderDetailResponse.fromOrderDetail(orderDetail);
        return ResponseEntity.ok(ApiResponse.success(orderDetailResponse, "Get order detail successfully"));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<List<OrderDetailResponse>>> getOrderDetails(
            @Valid @PathVariable("orderId") Long orderId
    ) throws DataNotFoundException {
        List<OrderDetail> details = orderDetailService.findByOrderId(orderId);
        List<OrderDetailResponse> orderDetailResponses = details.stream()
                .map(OrderDetailResponse::fromOrderDetail)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(orderDetailResponses, "Get order details by orderId successfully"));
    }

    @PutMapping("/{id}")
    @Operation(security = {@SecurityRequirement(name = "bearer-key")})
    @PreAuthorize("denyAll()")
    public ResponseEntity<ApiResponse<OrderDetail>> updateOrderDetail(
            @Valid @PathVariable("id") Long id,
            @RequestBody OrderDetailDTO orderDetailDTO
    ) throws DataNotFoundException, Exception {
        OrderDetail orderDetail = orderDetailService.updateOrderDetail(id, orderDetailDTO);
        return ResponseEntity.ok(ApiResponse.success(orderDetail, "Update order detail successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(security = {@SecurityRequirement(name = "bearer-key")})
    @PreAuthorize("denyAll()")
    public ResponseEntity<ApiResponse<?>> deleteOrderDetail(
            @Valid @PathVariable("id") Long id) {
        orderDetailService.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null,
                localizationUtils.getLocalizedMessage(MessageKeys.DELETE_ORDER_DETAIL_SUCCESSFULLY)));
    }
}
