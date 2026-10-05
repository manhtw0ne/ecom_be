package com.manh.ecom_be.services.coupon;

import com.manh.ecom_be.models.*;
import com.manh.ecom_be.repositories.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CouponServiceTest {
    @Test void sequentialDiscountsAreAppliedOnceEach() {
        CouponRepository coupons = mock(CouponRepository.class);
        CouponConditionRepository conditions = mock(CouponConditionRepository.class);
        Coupon coupon = Coupon.builder().id(1L).code("SAVE").active(true).build();
        CouponCondition tenPercent = CouponCondition.builder().attribute("minimum_amount").operator(">")
                .value("0").discountAmount(BigDecimal.TEN).build();
        when(coupons.findByCode("SAVE")).thenReturn(Optional.of(coupon));
        when(conditions.findByCouponIdOrderByIdAsc(1L)).thenReturn(List.of(tenPercent, tenPercent, tenPercent));
        assertThat(new CouponService(coupons, conditions).calculateCouponValue("SAVE", new BigDecimal("100")))
                .isEqualByComparingTo("72.90");
    }
    @Test void inactiveCouponIsRejected() {
        CouponRepository coupons = mock(CouponRepository.class);
        when(coupons.findByCode("OFF")).thenReturn(Optional.of(Coupon.builder().active(false).build()));
        assertThatThrownBy(() -> new CouponService(coupons, mock(CouponConditionRepository.class))
                .calculateCouponValue("OFF", new BigDecimal("100"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void minimumThresholdUsesExactDecimalsAndStrictComparison() {
        CouponRepository coupons = mock(CouponRepository.class);
        CouponConditionRepository conditions = mock(CouponConditionRepository.class);
        when(coupons.findByCode("SAVE")).thenReturn(Optional.of(Coupon.builder().id(1L).active(true).build()));
        when(conditions.findByCouponIdOrderByIdAsc(1L)).thenReturn(List.of(
                CouponCondition.builder().attribute("minimum_amount").operator(">")
                        .value("0.30").discountAmount(new BigDecimal("50")).build()));
        var service = new CouponService(coupons, conditions);
        assertThat(service.calculateCouponValue("SAVE", new BigDecimal("0.10").add(new BigDecimal("0.20"))))
                .isEqualByComparingTo("0.30");
        assertThat(service.calculateCouponValue("SAVE", new BigDecimal("0.31")))
                .isEqualByComparingTo("0.16");
    }}
