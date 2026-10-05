package com.manh.ecom_be.services.coupon;

import com.manh.ecom_be.models.Coupon;
import com.manh.ecom_be.models.CouponCondition;
import com.manh.ecom_be.repositories.CouponConditionRepository;
import com.manh.ecom_be.repositories.CouponRepository;
import com.manh.ecom_be.utils.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;

@RequiredArgsConstructor
@Service
public class CouponService implements InterfaceCouponService {
    private final CouponRepository couponRepository;
    private final CouponConditionRepository couponConditionRepository;

    @Override
    public BigDecimal calculateCouponValue(String couponCode, BigDecimal totalAmount) {
        BigDecimal remaining = Money.amount(totalAmount);
        Coupon coupon = couponRepository.findByCode(couponCode)
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found"));
        if (!coupon.isActive()) throw new IllegalArgumentException("Coupon is not active");
        // Stable ordering matters: conditions compare against the remaining payable amount.
        for (CouponCondition condition : couponConditionRepository.findByCouponIdOrderByIdAsc(coupon.getId())) {
            BigDecimal discounted = Money.discount(remaining, condition.getDiscountAmount());
            boolean applies = switch (condition.getAttribute()) {
                case "minimum_amount" -> ">".equals(condition.getOperator())
                        && remaining.compareTo(Money.amount(new BigDecimal(condition.getValue()))) > 0;
                case "applicable_date" -> "BETWEEN".equalsIgnoreCase(condition.getOperator())
                        && LocalDate.now().equals(LocalDate.parse(condition.getValue()));
                default -> false;
            };
            if (applies) remaining = discounted;
        }
        return remaining;
    }
}