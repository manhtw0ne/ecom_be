package com.manh.ecom_be.services.category;

import com.manh.ecom_be.dtos.CategoryDTO;
import com.manh.ecom_be.models.Category;
import com.manh.ecom_be.models.Product;
import com.manh.ecom_be.repositories.CategoryRepository;
import com.manh.ecom_be.repositories.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService Unit Tests")
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductRepository productRepository;
    @Mock private com.manh.ecom_be.components.SecurityUtils securityUtils;

    @InjectMocks
    private CategoryService categoryService;

    private Category testCategory;
    private CategoryDTO testCategoryDTO;

    @BeforeEach
    void setUp() {
        testCategory = Category.builder()
                .id(1L)
                .name("Electronics")
                .build();

        testCategoryDTO = new CategoryDTO();
        testCategoryDTO.setName("Electronics");
    }

    // ─────────────── CREATE ───────────────

    @Test
    @DisplayName("should create category successfully")
    void createCategory_validDTO_shouldReturnCategory() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        Category result = categoryService.createCategory(testCategoryDTO);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Electronics");
        verify(categoryRepository).save(any(Category.class));
    }

    // ─────────────── READ ───────────────

    @Test
    @DisplayName("should return category when it exists")
    void getCategoryById_existingId_shouldReturnCategory() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));

        Category result = categoryService.getCategoryById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Electronics");
    }

    @Test
    @DisplayName("should throw RuntimeException when category does not exist")
    void getCategoryById_nonExistingId_shouldThrowException() {
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Category not found");
    }

    @Test
    @DisplayName("should return all categories")
    void getAllCategories_shouldReturnList() {
        Category category2 = Category.builder().id(2L).name("Books").build();
        when(categoryRepository.findAll()).thenReturn(List.of(testCategory, category2));

        List<Category> result = categoryService.getAllCategories();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Category::getName)
                .containsExactly("Electronics", "Books");
    }

    // ─────────────── UPDATE ───────────────

    @Test
    @DisplayName("should update category name")
    void updateCategory_shouldUpdateName() {
        CategoryDTO updateDTO = new CategoryDTO();
        updateDTO.setName("Updated Electronics");

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category result = categoryService.updateCategory(1L, updateDTO);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Updated Electronics");
        verify(categoryRepository).save(testCategory);
    }

    // ─────────────── DELETE ───────────────

    @Test
    @DisplayName("should throw exception when deleting category with products")
    void deleteCategory_withProducts_shouldThrowException() {
        Product product = Product.builder().id(1L).name("Phone").category(testCategory).build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(productRepository.countAllByCategoryId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> categoryService.deleteCategory(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot delete category with associated products");

        verify(categoryRepository, never()).deleteById(1L);
    }
}
