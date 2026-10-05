package com.manh.ecom_be.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Decimal amounts use the same range and scale as DECIMAL(19,2). */
public final class Money {
    public static final BigDecimal MAX = new BigDecimal("99999999999999999.99");
    private Money() {}

    public static BigDecimal amount(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("Amount must be between 0 and " + MAX);
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Amount supports at most 2 decimal places", ex);
        }
    }

    public static BigDecimal productPrice(BigDecimal value) {
        BigDecimal price = amount(value);
        if (price.compareTo(new BigDecimal("10000000")) > 0) {
            throw new IllegalArgumentException("Price must be <= 10,000,000");
        }
        return price;
    }

    public static BigDecimal discount(BigDecimal amount, BigDecimal percentage) {
        BigDecimal valid = amount(amount);
        if (percentage == null || percentage.signum() < 0
                || percentage.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Coupon percentage must be between 0 and 100");
        }
        // Round the payable amount once per applied rule, not through binary floating point.
        return valid.multiply(BigDecimal.ONE.subtract(percentage.movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
