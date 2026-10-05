package com.manh.ecom_be.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Represents a product and quantity in the shopping cart")
public class CartItemDTO {

    @NotNull(message = "Product ID is required")
    @JsonProperty("product_id")
    @Schema(description = "ID of the product", example = "1")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @JsonProperty("quantity")
    @Schema(description = "Number of items to order", example = "2")
    private Integer quantity;
}
