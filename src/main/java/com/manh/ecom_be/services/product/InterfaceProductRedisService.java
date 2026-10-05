package com.manh.ecom_be.services.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.manh.ecom_be.responses.product.ProductResponse;
import com.manh.ecom_be.responses.product.ProductListResponse;
import org.springframework.data.domain.PageRequest;

import java.util.List;

public interface InterfaceProductRedisService {
    void clear();
    ProductListResponse getAllProducts(
            String keyword,
            Long categoryId,
            PageRequest pageRequest) throws JsonProcessingException;
    void saveAllProducts(ProductListResponse productResponses,
                         String keyword,
                         Long categoryId,
                         PageRequest pageRequest) throws JsonProcessingException;
}
