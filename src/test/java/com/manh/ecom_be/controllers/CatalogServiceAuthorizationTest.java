package com.manh.ecom_be.controllers;

import com.manh.ecom_be.dtos.*;
import com.manh.ecom_be.models.*;
import com.manh.ecom_be.repositories.*;
import com.manh.ecom_be.services.category.CategoryService;
import com.manh.ecom_be.services.product.ProductService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogServiceAuthorizationTest {
    @Autowired ProductService productService;
    @Autowired CategoryService categoryService;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired jakarta.persistence.EntityManager entityManager;
    Category category;
    Product product;

    @BeforeEach void seed() {
        category = categories.save(Category.builder().name("Original category").build());
        product = products.save(Product.builder().name("Original product").category(category)
                .price(BigDecimal.TEN).stockQuantity(3).build());
    }
    @AfterEach void clear() { TestSecurityContextHolder.clearContext(); }
    void authenticate(String kind) {
        TestSecurityContextHolder.clearContext();
        if (kind.equals("anonymous")) return;
        var role = roles.save(Role.builder().name(kind.equals("buyer") ? "USER" : "ADMIN").build());
        var actor = users.save(User.builder().email("catalog" + System.nanoTime() + "@example.test")
                .password("hash").role(role).active(!kind.equals("inactive"))
                .deleted(kind.equals("deleted")).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, actor.getAuthorities()));
    }
    ProductDTO payload() {
        return ProductDTO.builder().name("Changed product").categoryId(category.getId())
                .price(BigDecimal.ONE).stockQuantity(8).build();
    }

    @ParameterizedTest @ValueSource(strings = {"anonymous", "buyer", "inactive", "deleted"})
    void directWritesRequireActiveAdmin(String actor) {
        authenticate(actor);
        long productCount = products.count(), categoryCount = categories.count();
        assertThatThrownBy(() -> productService.createProduct(payload())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> productService.updateProduct(product.getId(), payload())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> productService.deleteProduct(product.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> categoryService.createCategory(new CategoryDTO("Changed"))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> categoryService.updateCategory(category.getId(), new CategoryDTO("Changed"))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> categoryService.deleteCategory(category.getId())).isInstanceOf(AccessDeniedException.class);
        assertThat(products.count()).isEqualTo(productCount);
        assertThat(categories.count()).isEqualTo(categoryCount);
        entityManager.refresh(product);
        entityManager.refresh(category);
        assertThat(product.getName()).isEqualTo("Original product");
        assertThat(product.getStockQuantity()).isEqualTo(3);
        assertThat(category.getName()).isEqualTo("Original category");
    }

    @Test void activeAdminCanManageCatalog() throws Exception {
        authenticate("admin");
        var created = productService.createProduct(payload());
        assertThat(created.getId()).isNotNull();
        assertThat(productService.updateProduct(product.getId(), payload()).getName()).isEqualTo("Changed product");
        productService.deleteProduct(created.getId());
        assertThat(created.isDeleted()).isTrue();
        var newCategory = categoryService.createCategory(new CategoryDTO("New"));
        assertThat(categoryService.updateCategory(newCategory.getId(), new CategoryDTO("Updated")).getName()).isEqualTo("Updated");
        categoryService.deleteCategory(newCategory.getId());
        assertThat(categories.findById(newCategory.getId())).isEmpty();
    }

    @Test void anonymousServiceReadsStillWork() {
        authenticate("anonymous");
        assertThat(categoryService.getCategoryById(category.getId()).getName()).isEqualTo("Original category");
        assertThat(productService.findProductsByIds(java.util.List.of(product.getId()))).hasSize(1);
    }
}
