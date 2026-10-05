package com.manh.ecom_be.models;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "order_details")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id")
    @JsonBackReference
    private Order order;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "product_name_snapshot", length = 350, updatable = false)
    private String productNameSnapshot;

    @Column(name = "product_thumbnail_snapshot", length = 300, updatable = false)
    private String productThumbnailSnapshot;

    @Column(name = "price", precision = 19, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "number_of_products", nullable = false)
    private int numberOfProducts;

    @Column(name = "total_money", precision = 19, scale = 2, nullable = false)
    private BigDecimal totalMoney;


    @Column(name = "color")
    private String color;

    @ManyToOne
    @JoinColumn(name = "coupon_id", nullable = true)
    @JsonBackReference
    private Coupon coupon;
}
