package com.manh.ecom_be.services.product;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.javafaker.Faker;
import com.manh.ecom_be.dtos.ProductDTO;

import com.manh.ecom_be.exceptions.DataNotFoundException;

import com.manh.ecom_be.models.*;
import com.manh.ecom_be.repositories.*;
import com.manh.ecom_be.responses.product.ProductResponse;
import com.manh.ecom_be.components.metrics.BusinessMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService implements InterfaceProductService {
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    private final FavoriteRepository favoriteRepository;
    private final BusinessMetrics businessMetrics;
    private final ProductImageRepository productImageRepository;
    private final jakarta.persistence.EntityManager entityManager;
    private final com.manh.ecom_be.components.SecurityUtils securityUtils;
    private final OrderDetailRepository orderDetailRepository;

    @Override
    @Transactional
    public Product createProduct(ProductDTO productDTO) throws DataNotFoundException {
        securityUtils.requireAdmin();
        if (productDTO.getThumbnail() != null && !productDTO.getThumbnail().isBlank()) {
            throw new com.manh.ecom_be.exceptions.InvalidParamException("Create product first, then upload its images");
        }
        Category existingCategory = categoryRepository
                .findById(productDTO.getCategoryId())
                .orElseThrow(() ->
                        new DataNotFoundException(
                                "Category not found: " + productDTO.getCategoryId()));

        Product newProduct = Product.builder()
                .name(productDTO.getName())
                .price(com.manh.ecom_be.utils.Money.productPrice(productDTO.getPrice()))
                .stockQuantity(productDTO.getStockQuantity() == null ? 0 : productDTO.getStockQuantity())
                .thumbnail(null)
                .description(productDTO.getDescription())
                .category(existingCategory)
                .build();
        return productRepository.save(newProduct);
    }

    @Override
    public Product getProductById(long productId) throws Exception {
        Optional<Product> optionalProduct = productRepository.getDetailProduct(productId);
        if (optionalProduct.isPresent()) {
            Product product = optionalProduct.get();

            return product;
        }
        throw new DataNotFoundException("Cannot find product with id =" + productId);
    }

    @Override
    public List<Product> findProductsByIds(List<Long> productIds) {
        return productRepository.findProductsByIds(productIds);
    }

    @Override
    public Page<ProductResponse> getAllProducts(String keyword,
                                                Long categoryId,
                                                PageRequest pageRequest) {
        return businessMetrics.getProductSearchTimer().record(() -> {
            Page<Product> productsPage = productRepository.searchProducts(categoryId, keyword, pageRequest);
            return productsPage.map(ProductResponse::fromProduct);
        });
    }

    @Override
    @Transactional
    public Product updateProduct(long id, ProductDTO productDTO) throws Exception {
        securityUtils.requireAdmin();
        Product existingProduct = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new DataNotFoundException("Cannot find product with id =" + id));
        entityManager.refresh(existingProduct, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (productDTO.getThumbnail() != null && !productDTO.getThumbnail().isEmpty()
                && productImageRepository.findAllForUpdate(id).stream()
                .noneMatch(image -> productDTO.getThumbnail().equals(image.getImageUrl()))) {
            throw new com.manh.ecom_be.exceptions.InvalidParamException("Thumbnail must belong to this product");
        }
        if (existingProduct != null) {
            Category existingCategory = categoryRepository
                    .findById(productDTO.getCategoryId())
                    .orElseThrow(() ->
                            new DataNotFoundException(
                                    "Cannot find category not found: " + productDTO.getCategoryId()));


            if (productDTO.getName() != null && !productDTO.getName().isEmpty()) {
                existingProduct.setName(productDTO.getName());
            }

            existingProduct.setCategory(existingCategory);

            if (productDTO.getPrice() != null) {
                existingProduct.setPrice(com.manh.ecom_be.utils.Money.productPrice(productDTO.getPrice()));
            }

            if (productDTO.getStockQuantity() != null) {
                if (productDTO.getStockQuantity() < 0) {
                    throw new IllegalArgumentException("Stock quantity must be >= 0");
                }
                existingProduct.setStockQuantity(productDTO.getStockQuantity());
            }

            if (productDTO.getDescription() != null && !productDTO.getDescription().isEmpty()) {
                existingProduct.setDescription(productDTO.getDescription());
            }

            if (productDTO.getThumbnail() != null && !productDTO.getThumbnail().isEmpty()) {
                existingProduct.setThumbnail(productDTO.getThumbnail());
            }

            return productRepository.save(existingProduct);
        }
        return null;
    }


    @Override
    @Transactional
    public void deleteProduct(long id) throws DataNotFoundException {
        securityUtils.requireAdmin();
        var product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new DataNotFoundException("Product not found"));
        entityManager.refresh(product, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (orderDetailRepository.existsByProductId(id)) {
            throw new com.manh.ecom_be.exceptions.InvalidParamException(
                    "Cannot delete a product referenced by order history");
        }
        // Updating the flag avoids cascading REMOVE to images, comments and favorites.
        product.setDeleted(true);
        productRepository.saveAndFlush(product);
    }
    @Override
    public boolean existsByName(String name) {
        return productRepository.existsByName(name);
    }

    @Override
    @Transactional
    public Product likeProduct(Long userId, Long productId) throws Exception {
        if (!userRepository.existsById(userId) ||
        !productRepository.existsById(productId)) {
            throw new DataNotFoundException("User or Product not found");
        }

        if (favoriteRepository.existsByUserIdAndProductId(userId, productId)) {
        } else {
            Favorite favorite = Favorite.builder()
                    .product(productRepository.findById(productId).orElse(null))
                    .user(userRepository.findById(userId).orElse(null))
                    .build();
            favoriteRepository.save(favorite);
        }
        return productRepository.findById(productId).orElse(null);
    }

    @Override
    @Transactional
    public Product unlikeProduct(Long userId, Long productId) throws Exception {
        if (!userRepository.existsById(userId) || !productRepository.existsById(productId)) {
            throw new DataNotFoundException("User or Product not found");
        }

        if (favoriteRepository.existsByUserIdAndProductId(userId, productId)) {
            Favorite favorite = favoriteRepository.findByUserIdAndProductId(userId, productId);
            favoriteRepository.delete(favorite);
        }
        return productRepository.findById(productId).orElse(null);
    }

    @Override
    @Transactional
    public List<ProductResponse> findFavoriteProductsByUserId(Long userId) throws Exception {
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isEmpty()) {
            throw new Exception("User not found with ID: " + userId);
        }

        List<Product> favoriteProducts = productRepository.findFavoriteProductsByUserId(userId);
        return favoriteProducts.stream()
                .map(ProductResponse::fromProduct)
                .collect(Collectors.toList());
    }

    @Override
    public void generateFakeLikes() throws Exception {
        Faker faker = new Faker();
        Random random = new Random();

        List<User> users = userRepository.findByRoleId(1L);

        List<Product> products = productRepository.findAll();
        final int totalRecords = 1_000;
        final int batchSize = 100;
        List<Favorite> favorites = new ArrayList<>();
        for (int i = 0; i < totalRecords; i++) {
            // Select a random user and product
            User user = users.get(random.nextInt(users.size()));
            Product product = products.get(random.nextInt(products.size()));

            if(!favoriteRepository.existsByUserIdAndProductId(user.getId(), product.getId())) {
                // Generate a fake favorite
                Favorite favorite = Favorite.builder()
                        .user(user)
                        .product(product)
                        .build();
                favorites.add(favorite);
            }
            if(favorites.size() >= batchSize) {
                favoriteRepository.saveAll(favorites);
                favorites.clear();
            }
        }

    }







}
