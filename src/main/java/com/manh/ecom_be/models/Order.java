package com.manh.ecom_be.models;

import java.math.BigDecimal;


import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE orders SET is_deleted = 1 WHERE id = ?")
@SQLRestriction("is_deleted = 0")
public class Order extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "fullname", length = 100)
    private String fullName;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "phone_number", nullable = false, length = 100)
    private String phoneNumber;

    @Column(name = "address", length = 100)
    private String address;

    private String note;

    @Column(name = "order_date")
    private LocalDateTime orderDate;

    @Column(name = "status")
    private String status;

    @Column(name = "total_money", precision = 19, scale = 2)
    private BigDecimal totalMoney;

    @Column(name = "shipping_method")
    @Builder.Default
    private String shippingMethod = "";

    @Column(name = "shipping_address")
    @Builder.Default
    private String shippingAddress = "";

    @Column(name = "shipping_date")
    private LocalDate shippingDate;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "payment_method")
    @Builder.Default
    private String paymentMethod = "";

    @Column(name = "is_active")
    private Boolean active;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "vnp_txn_ref", nullable = true)
    private String vnpTxnRef;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<OrderDetail> orderDetails;

    @ManyToOne
    @JoinColumn(name = "coupon_id", nullable = true)
    @JsonBackReference
    private Coupon coupon = null;
}
