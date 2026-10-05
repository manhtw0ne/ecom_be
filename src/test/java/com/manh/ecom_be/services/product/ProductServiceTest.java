package com.manh.ecom_be.services.product;

import java.math.BigDecimal;

import com.manh.ecom_be.components.metrics.BusinessMetrics;
import com.manh.ecom_be.dtos.ProductDTO;
import com.manh.ecom_be.dtos.ProductImageDTO;
import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.exceptions.InvalidParamException;
import com.manh.ecom_be.models.*;
import com.manh.ecom_be.repositories.*;
import com.manh.ecom_be.responses.product.ProductResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProductService Unit Tests")
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private jakarta.persistence.EntityManager entityManager;
    @Mock private com.manh.ecom_be.components.SecurityUtils securityUtils;
    @Mock private OrderDetailRepository orderDetailRepository;
    @Mock private FavoriteRepository favoriteRepository;

    // Use a real BusinessMetrics backed by SimpleMeterRegistry so that
    // Timer.record(Supplier) actually delegates to the supplier without NPE.
    // This avoids the Mockito generic-erasure issue with Timer.record(Supplier<T>).
    private BusinessMetrics businessMetrics;

    @InjectMocks
    private ProductService productService;

    private Category testCategory;
    private Product testProduct;
    private ProductDTO testProductDTO;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Build a real BusinessMetrics so Timer.record() works without mocking
        businessMetrics = new BusinessMetrics(new SimpleMeterRegistry());
        // Inject it into the @InjectMocks instance (Mockito won't inject non-@Mock/@Spy fields)
        ReflectionTestUtils.setField(productService, "businessMetrics", businessMetrics);

        testCategory = Category.builder().id(1L).name("Electronics").build();

        testProduct = Product.builder()
                .id(1L)
                .name("Test Product")
                .price(new BigDecimal("100.0"))
                .thumbnail("test.jpg")
                .description("Test description")
                .category(testCategory)
                .comments(new ArrayList<>())
                .favorites(new ArrayList<>())
                .productImages(new ArrayList<>())
                .build();

        testProductDTO = ProductDTO.builder()
                .name("Test Product")
                .price(new BigDecimal("100.0"))
                .thumbnail(null)
                .description("Test description")
                .categoryId(1L)
                .build();

        testUser = User.builder()
                .id(1L)
                .fullName("Test User")
                .build();
    }

    // ─────────────── CREATE ───────────────

    @Nested
    @DisplayName("createProduct")
    class CreateProduct {

        @Test
        @DisplayName("should create product when category exists")
        void createProduct_validDTO_shouldReturnProduct() throws Exception {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);

            Product result = productService.createProduct(testProductDTO);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Test Product");
            assertThat(result.getPrice()).isEqualByComparingTo("100.0");
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("should throw DataNotFoundException when category not found")
        void createProduct_invalidCategory_shouldThrowDataNotFound() {
            when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

            ProductDTO invalidDTO = ProductDTO.builder()
                    .name("Test")
                    .price(new BigDecimal("100.0"))
                    .categoryId(99L)
                    .build();

            assertThatThrownBy(() -> productService.createProduct(invalidDTO))
                    .isInstanceOf(DataNotFoundException.class)
                    .hasMessageContaining("Category not found");
        }
    }

    // ─────────────── GET BY ID ───────────────

    @Nested
    @DisplayName("getProductById")
    class GetProductById {

        @Test
        @DisplayName("should return product when id exists")
        void getProductById_existingId_shouldReturnProduct() throws Exception {
            when(productRepository.getDetailProduct(1L)).thenReturn(Optional.of(testProduct));

            Product result = productService.getProductById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("Test Product");
        }

        @Test
        @DisplayName("should throw DataNotFoundException when id not found")
        void getProductById_nonExistingId_shouldThrowDataNotFound() {
            when(productRepository.getDetailProduct(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductById(999L))
                    .isInstanceOf(DataNotFoundException.class)
                    .hasMessageContaining("Cannot find product");
        }
    }

    // ─────────────── GET ALL (with Timer) ───────────────

    @Test
    @DisplayName("getAllProducts should return paginated results")
    void getAllProducts_shouldReturnPage() {
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by("id").ascending());
        Page<Product> productPage = new PageImpl<>(List.of(testProduct), pageRequest, 1);

        when(productRepository.searchProducts(eq(0L), eq("test"), eq(pageRequest)))
                .thenReturn(productPage);

        Page<ProductResponse> result = productService.getAllProducts("test", 0L, pageRequest);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Test Product");
    }

    // ─────────────── UPDATE ───────────────

    @Test
    @DisplayName("updateProduct should update fields when product exists")
    void updateProduct_validDTO_shouldUpdateFields() throws Exception {
        ProductDTO updateDTO = ProductDTO.builder()
                .name("Updated Product")
                .price(new BigDecimal("200.0"))
                .description("Updated description")
                .thumbnail("updated.jpg")
                .categoryId(1L)
                .build();

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testProduct));
        when(productImageRepository.findAllForUpdate(1L)).thenReturn(List.of(
                ProductImage.builder().id(2L).product(testProduct).imageUrl("updated.jpg").build()));

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.updateProduct(1L, updateDTO);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Updated Product");
        assertThat(result.getPrice()).isEqualByComparingTo("200.0");
        // updateProduct calls save exactly once to persist the updated product
        verify(productRepository, org.mockito.Mockito.times(1)).save(any(Product.class));
    }

    // ─────────────── DELETE ───────────────

    @Test
    @DisplayName("deleteProduct hides product without cascading remove")
    void deleteProduct_existingId_shouldHideProduct() throws Exception {
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testProduct));

        productService.deleteProduct(1L);

        assertThat(testProduct.isDeleted()).isTrue();
        verify(productRepository).saveAndFlush(testProduct);
        verify(productRepository, never()).delete(any());
    }

    // ─────────────── LIKE / UNLIKE ───────────────

    @Nested
    @DisplayName("Like/Unlike")
    class LikeUnlike {

        @Test
        @DisplayName("likeProduct should save Favorite when not yet liked")
        void likeProduct_validIds_shouldSaveFavorite() throws Exception {
            // likeProduct uses existsById then existsByUserIdAndProductId then findById
            when(userRepository.existsById(1L)).thenReturn(true);
            when(productRepository.existsById(1L)).thenReturn(true);
            when(favoriteRepository.existsByUserIdAndProductId(1L, 1L)).thenReturn(false);
            when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(favoriteRepository.save(any(Favorite.class))).thenAnswer(inv -> inv.getArgument(0));

            Product result = productService.likeProduct(1L, 1L);

            assertThat(result).isNotNull();
            verify(favoriteRepository).save(any(Favorite.class));
        }

        @Test
        @DisplayName("unlikeProduct should delete Favorite when it exists")
        void unlikeProduct_existingFavorite_shouldDelete() throws Exception {
            Favorite favorite = Favorite.builder()
                    .id(1L)
                    .user(testUser)
                    .product(testProduct)
                    .build();

            // unlikeProduct uses existsById then existsByUserIdAndProductId then findByUserIdAndProductId
            when(userRepository.existsById(1L)).thenReturn(true);
            when(productRepository.existsById(1L)).thenReturn(true);
            when(favoriteRepository.existsByUserIdAndProductId(1L, 1L)).thenReturn(true);
            when(favoriteRepository.findByUserIdAndProductId(1L, 1L)).thenReturn(favorite);
            when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
            doNothing().when(favoriteRepository).delete(any(Favorite.class));

            Product result = productService.unlikeProduct(1L, 1L);

            assertThat(result).isNotNull();
            verify(favoriteRepository).delete(any(Favorite.class));
        }
    }
}
