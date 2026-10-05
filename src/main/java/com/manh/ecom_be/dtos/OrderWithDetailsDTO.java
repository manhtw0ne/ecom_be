package com.manh.ecom_be.dtos;

import java.math.BigDecimal;


import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.aspectj.bridge.IMessage;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class OrderWithDetailsDTO {
    @JsonProperty("user_id")
    @Min(value = 1, message = "User's ID must be > 0")
    private Long userId;

    @JsonProperty("fullname")
    private String fullname;

    private String email;

    @JsonProperty("phone_number")
    @NotBlank(message = "Phone number is required")
    @Size(min = 5, message = "Phone number must be at least 5 characters")
    private String phoneNumber;
    private String address;
    private String note;


    @JsonProperty("total_money")
    @Min(value = 0 , message = "Total money must be >= 0")
    private BigDecimal totalMoney;


    @JsonProperty("shipping_method")
    private String shippingMethod;

    @JsonProperty("shipping_address")
    private String shippingAddress;

    @JsonProperty("shipping_date")
    private LocalDate ShippingDate;

    @JsonProperty("payment_method")
    private String paymentMethod;

    @JsonProperty("order_details")
    private List<OrderDetailDTO> orderDetailDTOS;
}
