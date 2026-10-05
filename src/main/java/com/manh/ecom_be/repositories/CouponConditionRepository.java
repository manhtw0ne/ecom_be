package com.manh.ecom_be.repositories;


import com.manh.ecom_be.models.CouponCondition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CouponConditionRepository extends JpaRepository<CouponCondition, Long> {
    List<CouponCondition> findByCouponIdOrderByIdAsc(long couponId);
}
