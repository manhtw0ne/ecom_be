package com.manh.ecom_be.services.coupon;

import java.math.BigDecimal;

public interface InterfaceCouponService {
    BigDecimal calculateCouponValue(String couponCode, BigDecimal totalAmount);
}
