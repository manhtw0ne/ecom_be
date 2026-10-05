package com.manh.ecom_be.services.category;

import com.manh.ecom_be.dtos.CategoryDTO;
import com.manh.ecom_be.models.Category;
import com.manh.ecom_be.models.Product;
import com.manh.ecom_be.repositories.CategoryRepository;
import com.manh.ecom_be.repositories.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService implements InterfaceCategoryService {
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final com.manh.ecom_be.components.SecurityUtils securityUtils;

    @Override
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "categories", allEntries = true)
    public Category createCategory(CategoryDTO categoryDTO) {
        securityUtils.requireAdmin();
        Category newCategory = Category
                .builder()
                .name(categoryDTO.getName()).build();
        return categoryRepository.save(newCategory);
    }

    @Override
    public Category getCategoryById(long id) {
        return categoryRepository.findById(id)
                .orElseThrow(com.manh.ecom_be.exceptions.CategoryNotFoundException::new);
    }

    @Override
    @org.springframework.cache.annotation.Cacheable(value = "categories", key = "'all'")
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public org.springframework.data.domain.Page<Category> getCategories(int page, int limit) {
        if (page < 0 || page > 100000 || limit < 1 || limit > 100) {
            throw new com.manh.ecom_be.exceptions.InvalidParamException("page must be 0..100000 and limit 1..100");
        }
        return categoryRepository.findAll(org.springframework.data.domain.PageRequest.of(page, limit,
                org.springframework.data.domain.Sort.by("id").ascending()));
    }

    @Override
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "categories", allEntries = true)
    public Category updateCategory(long categoryId,
                                   CategoryDTO categoryDTO) {
        securityUtils.requireAdmin();
        Category existingCategory = getCategoryById(categoryId);
        existingCategory.setName(categoryDTO.getName());
        categoryRepository.save(existingCategory);
        return existingCategory;
    }

    @Override
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "categories", allEntries = true)
    public Category deleteCategory(long id) throws Exception {
        securityUtils.requireAdmin();
        Category category = categoryRepository.findById(id)
                .orElseThrow(com.manh.ecom_be.exceptions.CategoryNotFoundException::new);
        if (productRepository.countAllByCategoryId(id) > 0) {
            throw new com.manh.ecom_be.exceptions.CategoryInUseException();
        } else {
            categoryRepository.deleteById(id);
            return category;
        }
    }
}
