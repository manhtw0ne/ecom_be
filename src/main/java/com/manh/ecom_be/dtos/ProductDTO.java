package com.manh.ecom_be.dtos;

import java.math.BigDecimal;


import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductDTO {
    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 200, message = "Title must be between 3 and 200 characters")
    private String name;

    @Min(value = 0, message = "Price must be >= 0")
    @Max(value = 10000000, message = "Price must be <= 10,000,000")
    @jakarta.validation.constraints.NotNull(message = "Price is required")
    @jakarta.validation.constraints.Digits(integer = 8, fraction = 2, message = "Price supports at most 2 decimal places")
    private BigDecimal price;

    @Min(value = 0, message = "Stock quantity must be >= 0")
    @JsonProperty("stock_quantity")
    private Integer stockQuantity;

    private String thumbnail;

    private String description;

    @JsonProperty("category_id")
    private Long categoryId;
}
