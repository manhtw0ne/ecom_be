package com.manh.ecom_be.controllers;

import java.math.BigDecimal;


import com.manh.ecom_be.responses.ApiResponse;
import com.manh.ecom_be.responses.coupon.CouponCalculationResponse;
import com.manh.ecom_be.services.coupon.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.prefix}/coupons")
@RequiredArgsConstructor
public class CouponController {
    private final CouponService couponService;

    @GetMapping("/calculate")
    public ResponseEntity<ApiResponse<CouponCalculationResponse>> calculateCouponValue(
            @RequestParam("couponCode") String couponCode,
            @RequestParam("totalAmount") BigDecimal totalAmount) {
        BigDecimal finalAmount = couponService.calculateCouponValue(couponCode, totalAmount);
        CouponCalculationResponse couponCalculationResponse = CouponCalculationResponse.builder()
                .result(finalAmount)
                .build();
        return ResponseEntity.ok(ApiResponse.success(couponCalculationResponse, "Calculate coupon successfully"));
    }
}
