package com.manh.ecom_be.controllers;

import java.math.BigDecimal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.javafaker.Faker;
import com.manh.ecom_be.components.LocalizationUtils;
import com.manh.ecom_be.components.SecurityUtils;
import com.manh.ecom_be.dtos.ProductDTO;
import com.manh.ecom_be.dtos.ProductImageDTO;
import com.manh.ecom_be.models.Product;
import com.manh.ecom_be.models.ProductImage;
import com.manh.ecom_be.models.User;
import com.manh.ecom_be.repositories.ProductRepository;
import com.manh.ecom_be.responses.product.ProductListResponse;
import com.manh.ecom_be.responses.ApiResponse;
import com.manh.ecom_be.responses.product.ProductResponse;
import com.manh.ecom_be.services.product.InterfaceProductRedisService;
import com.manh.ecom_be.services.product.InterfaceProductService;
import com.manh.ecom_be.utils.MessageKeys;
import com.manh.ecom_be.utils.FileUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("${api.prefix}/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {
    private final InterfaceProductService productService;
    private final LocalizationUtils localizationUtils;
    private final InterfaceProductRedisService productRedisService;
    private final ProductRepository productRepository;
    private final SecurityUtils securityUtils;
    private final com.manh.ecom_be.services.product.image.ProductImageUploadService imageUploads;

    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Product>> createProduct(
            @Valid @RequestBody ProductDTO productDTO
    ) throws Exception {
        Product newProduct = productService.createProduct(productDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(newProduct, "Create new product successfully"));
    }

    @PostMapping(value = "/uploads/{id}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<?>> uploadImages(
            @PathVariable("id") Long productId,
            @RequestParam(value = "files", required = false) List<MultipartFile> files
    ) throws Exception {
        var productImages = imageUploads.upload(productId, files);
        return ResponseEntity.ok(ApiResponse.created(productImages, "Upload images successfully"));
    }
    @GetMapping("/images/{imageName}")
    public ResponseEntity<?> viewImage(@PathVariable String imageName) {
        return FileUtils.imageResponse(imageName);
    }
    @GetMapping("")
    public ResponseEntity<ApiResponse<ProductListResponse>> getProducts(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0", name = "category_id") Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit
    ) throws JsonProcessingException {
        if (page < 0 || page > 100000 || limit < 1 || limit > 100
                || categoryId < 0 || keyword.length() > 200) {
            throw new com.manh.ecom_be.exceptions.InvalidParamException("Invalid page, limit, category or keyword");
        }
        PageRequest pageRequest = PageRequest.of(page, limit, Sort.by("id").ascending());
        ProductListResponse productListResponse = productRedisService.getAllProducts(keyword, categoryId, pageRequest);
        if (productListResponse == null) {
            Page<ProductResponse> productPage = productService.getAllProducts(keyword, categoryId, pageRequest);
            var products = productPage.getContent();
            products.forEach(product -> product.setTotalPages(productPage.getTotalPages()));
            productListResponse = ProductListResponse.builder().products(products)
                    .totalPages(productPage.getTotalPages()).build();
            productRedisService.saveAllProducts(productListResponse, keyword, categoryId, pageRequest);
        }
        return ResponseEntity.ok(ApiResponse.success(productListResponse, "Get products successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(
            @PathVariable("id") Long productId
    ) throws Exception {
        Product existingProduct = productService.getProductById(productId);
        return ResponseEntity.ok(ApiResponse.success(
                ProductResponse.fromProduct(existingProduct),
                "Get product detail successfully"));
    }

    @GetMapping("/by-ids")
    public ResponseEntity<ApiResponse<?>> getProductsByIds(@RequestParam("ids") String ids) {
        List<Long> productIds = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .collect(Collectors.toList());
        List<Product> products = productService.findProductsByIds(productIds);
        return ResponseEntity.ok(ApiResponse.success(
                products.stream().map(ProductResponse::fromProduct).toList(),
                "Get products successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(security = {@SecurityRequirement(name = "bearer-key")})
    public ResponseEntity<ApiResponse<?>> deleteProduct(@PathVariable long id) throws com.manh.ecom_be.exceptions.DataNotFoundException {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success(null,
                String.format("Product with id = %d deleted successfully", id)));
    }

    private ResponseEntity<ApiResponse<?>> generateFakeProducts() throws Exception {
        Faker faker = new Faker();
        for (int i = 0; i < 1_000_000; i++) {
            String productName = faker.commerce().productName();
            if(productService.existsByName(productName)) {
                continue;
            }
            ProductDTO productDTO = ProductDTO.builder()
                    .name(productName)
                    .price(BigDecimal.valueOf(faker.number().numberBetween(10, 10_000_001)))
                    .description(faker.lorem().sentence())
                    .thumbnail("")
                    .categoryId((long)faker.number().numberBetween(2, 5))
            .build();
            productService.createProduct(productDTO);
        }
        return ResponseEntity.ok(ApiResponse.success(null, "Insert fake products successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(security = { @SecurityRequirement(name = "bearer-key") })
    public ResponseEntity<ApiResponse<Product>> updateProduct(
            @PathVariable long id,
            @RequestBody ProductDTO productDTO) throws Exception {
        Product updatedProduct = productService.updateProduct(id, productDTO);
        return ResponseEntity.ok(ApiResponse.success(updatedProduct, "Update product successfully"));
    }

    @PostMapping("/like/{productId}")
    @PreAuthorize("hasRole('ROLE_USER') or hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> likeProduct(@PathVariable Long productId) throws Exception {
        User loginUser = securityUtils.getLoggedInUser();
        Product likedProduct = productService.likeProduct(loginUser.getId(), productId);
        return ResponseEntity.ok(ApiResponse.success(
                ProductResponse.fromProduct(likedProduct), "Like product successfully"));
    }

    @PostMapping("/unlike/{productId}")
    @PreAuthorize("hasRole('ROLE_USER') or hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> unlikeProduct(@PathVariable Long productId)
            throws Exception {
        User loginUser = securityUtils.getLoggedInUser();
        Product unlikedProduct = productService.unlikeProduct(loginUser.getId(), productId);
        return ResponseEntity.ok(ApiResponse.success(
                ProductResponse.fromProduct(unlikedProduct), "Unlike product successfully"));
    }

    @PostMapping("/favorites-products")
    @PreAuthorize("hasRole('ROLE_USER') or hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> findFavoriteProductsByUserId() throws Exception {
        User loginUser = securityUtils.getLoggedInUser();
        List<ProductResponse> favoriteProducts = productService.findFavoriteProductsByUserId(loginUser.getId());
        return ResponseEntity.ok(ApiResponse.success(favoriteProducts, "Favorite products retrieved successfully"));
    }

    @PostMapping("/generateFakeLikes")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<?>> generateFakeLikes() throws Exception {
        productService.generateFakeLikes();
        return ResponseEntity.ok(ApiResponse.success(null, "Insert fake likes successfully"));
    }

}

