package com.manh.ecom_be.services.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.manh.ecom_be.dtos.ProductDTO;

import com.manh.ecom_be.models.Product;

import com.manh.ecom_be.responses.product.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

public interface InterfaceProductService {
    Product createProduct(ProductDTO productDTO) throws Exception;
    Product getProductById(long id) throws Exception;

    Page<ProductResponse> getAllProducts(String keyword,
                                         Long categoryId, PageRequest pageRequest)
            throws JsonProcessingException;
    Product updateProduct(long id, ProductDTO productDTO) throws Exception;
    void deleteProduct(long id) throws com.manh.ecom_be.exceptions.DataNotFoundException;
    boolean existsByName(String name);



    List<Product> findProductsByIds(List<Long> productIds);

    Product likeProduct(Long userId, Long productId) throws Exception;
    Product unlikeProduct(Long userId, Long productId) throws Exception;
    List<ProductResponse> findFavoriteProductsByUserId(Long userId) throws Exception;
    void generateFakeLikes() throws Exception;
}
