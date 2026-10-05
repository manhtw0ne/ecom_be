package com.manh.ecom_be.repositories;

import com.manh.ecom_be.models.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    @org.springframework.data.jpa.repository.Query("select i.product.id from ProductImage i where i.id = :id")
    java.util.Optional<Long> findProductIdByImageId(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select i from ProductImage i where i.id = :id")
    java.util.Optional<ProductImage> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    List<ProductImage> findByProductId(Long productId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select i from ProductImage i where i.product.id = :productId order by i.id")
    List<ProductImage> findAllForUpdate(@org.springframework.data.repository.query.Param("productId") Long productId);
}
