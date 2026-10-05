package com.manh.ecom_be.services.email;

import java.math.BigDecimal;

public record OrderPlaced(String email, Long orderId, BigDecimal totalMoney) {}
