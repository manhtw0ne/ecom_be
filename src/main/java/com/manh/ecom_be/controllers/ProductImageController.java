package com.manh.ecom_be.controllers;


import com.manh.ecom_be.models.ProductImage;
import com.manh.ecom_be.responses.ApiResponse;
import com.manh.ecom_be.services.product.ProductService;
import com.manh.ecom_be.services.product.image.InterfaceProductImageService;
import com.manh.ecom_be.utils.FileUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.prefix}/product-images")
@RequiredArgsConstructor
public class ProductImageController {
    private final InterfaceProductImageService productImageService;
    private final ProductService productService;

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<ProductImage>> delete(
            @PathVariable Long id
    ) throws Exception {
        ProductImage productImage = productImageService.deleteProductImage(id);
        return ResponseEntity.ok(ApiResponse.success(productImage, "Delete product image successfully"));
    }
}
