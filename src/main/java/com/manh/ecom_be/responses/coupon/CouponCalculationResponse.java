package com.manh.ecom_be.responses.coupon;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CouponCalculationResponse {
    @JsonProperty("result")
    private BigDecimal result;
}
