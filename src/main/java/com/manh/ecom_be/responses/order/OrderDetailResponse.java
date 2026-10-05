package com.manh.ecom_be.responses.order;

import java.math.BigDecimal;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.manh.ecom_be.models.OrderDetail;
import lombok.*;

@Getter @Setter @AllArgsConstructor
@NoArgsConstructor @Builder
public class OrderDetailResponse {
    private Long id;

    @JsonProperty("order_id")
    private Long orderId;

    @JsonProperty("product_id")
    private Long productId;

    @JsonProperty("product_name")
    private String productName;

    @JsonProperty("thumbnail")
    private String thumbnail;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("number_of_products")
    private int numberOfProducts;

    @JsonProperty("total_money")
    private BigDecimal totalMoney;

    private String color;

    public static OrderDetailResponse fromOrderDetail(OrderDetail orderDetail) {
        return OrderDetailResponse
                .builder()
                .id(orderDetail.getId())
                .orderId(orderDetail.getOrder().getId())
                .productId(orderDetail.getProduct().getId())
                .productName(orderDetail.getProductNameSnapshot() != null
                        ? orderDetail.getProductNameSnapshot() : orderDetail.getProduct().getName())
                .thumbnail(orderDetail.getProductNameSnapshot() != null
                        ? orderDetail.getProductThumbnailSnapshot() : orderDetail.getProduct().getThumbnail())
                .price(orderDetail.getPrice())
                .numberOfProducts(orderDetail.getNumberOfProducts())
                .totalMoney(orderDetail.getTotalMoney())
                .color(orderDetail.getColor())
                .build();
    }
}
