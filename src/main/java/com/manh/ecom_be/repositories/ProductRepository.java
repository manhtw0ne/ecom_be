package com.manh.ecom_be.repositories;

import com.manh.ecom_be.models.Category;
import com.manh.ecom_be.models.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;


public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query(value = "select count(*) from products where category_id = :categoryId", nativeQuery = true)
    long countAllByCategoryId(@Param("categoryId") Long categoryId);
    @org.springframework.data.jpa.repository.Modifying
    @Query("update Product p set p.stockQuantity = p.stockQuantity + :quantity where p.id = :id")
    int restoreStock(@Param("id") Long id, @Param("quantity") int quantity);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    boolean existsByName(String name);


    Page<Product> findAll(Pageable pageable);
    List<Product> findByCategory(Category category);


    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "category")
    @Query("SELECT p FROM Product p WHERE " +
            "(:categoryId IS NULL OR :categoryId = 0 OR p.category.id = :categoryId) " +
            "AND (:keyword IS NULL OR :keyword = '' " +
            "OR p.name LIKE %:keyword% " +
            "OR p.description LIKE %:keyword%)")
    Page<Product> searchProducts(
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.productImages WHERE p.id = :productId")
    Optional<Product> getDetailProduct(@Param("productId") Long productId);

    @Query("SELECT p FROM Product p WHERE p.id IN :productIds")
    List<Product> findProductsByIds(@Param("productIds") List<Long> productIds);

    @Query("SELECT p FROM Product p JOIN p.favorites f WHERE f.user.id = :userId")
    List<Product> findFavoriteProductsByUserId(@Param("userId") Long userId);
}


