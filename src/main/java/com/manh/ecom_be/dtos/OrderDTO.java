package com.manh.ecom_be.dtos;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter
@Schema(description = "Request body to create a new order")
public class OrderDTO {

    @JsonProperty("user_id")
    @Schema(description = "User ID (set automatically from JWT token)", hidden = true)
    private Long userId;

    @JsonProperty("fullname")
    @Schema(description = "Recipient full name", example = "Nguyen Van A")
    private String fullName;

    @Schema(description = "Contact email", example = "user@example.com")
    private String email;

    @JsonProperty("phone_number")
    @NotBlank(message = "Phone number is required")
    @Size(min = 9, max = 15, message = "Phone number must be 9-15 characters")
    @Schema(description = "Contact phone number", example = "0901234567")
    private String phoneNumber;

    @JsonProperty("status")
    @Schema(description = "Order status (managed by system)", hidden = true)
    private String status;

    @NotBlank(message = "Delivery address is required")
    @Schema(description = "Delivery address", example = "123 Nguyen Hue, Ho Chi Minh City")
    private String address;

    @Schema(description = "Additional notes for the order", example = "Leave at the door")
    private String note;

    @JsonProperty("total_money")
    @Min(value = 0, message = "Total money must be >= 0")
    @Schema(description = "Total order amount in VND", example = "250000")
    private BigDecimal totalMoney;

    @JsonProperty("shipping_method")
    @Schema(description = "Shipping method", example = "express")
    private String shippingMethod;

    @JsonProperty("shipping_address")
    @Schema(description = "Shipping address (defaults to address if not set)")
    private String shippingAddress;

    @JsonProperty("shipping_date")
    @Schema(description = "Desired shipping date (must be today or later)", example = "2026-10-01")
    private LocalDate shippingDate;

    @JsonProperty("payment_method")
    @Schema(description = "COD only; omitted value defaults to cod", example = "cod", allowableValues = {"cod"})
    private String paymentMethod;

    @JsonProperty("coupon_code")
    @Schema(description = "Optional discount coupon code", example = "SUMMER10")
    private String couponCode;

    @JsonProperty("vnp_txn_ref")
    @Schema(description = "VNPay transaction reference (for online payment flow)", hidden = true)
    private String vnpTxnRef;

    @Valid
    @NotEmpty(message = "Cart must have at least one item")
    @JsonProperty("cart_items")
    @Schema(description = "List of products and quantities to order")
    private List<CartItemDTO> cartItems;
}
