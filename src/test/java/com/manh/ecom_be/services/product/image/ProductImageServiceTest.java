package com.manh.ecom_be.services.product.image;
import com.manh.ecom_be.models.*;
import com.manh.ecom_be.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class ProductImageServiceTest {
    @Mock ProductImageRepository images;
    @Mock ProductRepository products;
    @Mock com.manh.ecom_be.components.SecurityUtils security;
    @Mock jakarta.persistence.EntityManager entityManager;
    @InjectMocks ProductImageService service;
    @ParameterizedTest @ValueSource(booleans={true,false})
    void deletingThumbnailReplacesItOrClearsIt(boolean hasRemaining) throws Exception {
        var product=Product.builder().id(1L).thumbnail("old.png").build();
        var removed=ProductImage.builder().id(2L).product(product).imageUrl("old.png").build();
        when(images.findByIdForUpdate(2L)).thenReturn(Optional.of(removed));
        when(images.findAllForUpdate(1L)).thenReturn(hasRemaining ? List.of(ProductImage.builder().id(3L).imageUrl("next.png").build()) : List.of());
        when(images.findProductIdByImageId(2L)).thenReturn(Optional.of(1L));
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        assertThat(service.deleteProductImage(2L)).isSameAs(removed);
        assertThat(product.getThumbnail()).isEqualTo(hasRemaining ? "next.png" : null);
        verify(images).delete(removed); verify(products).saveAndFlush(product);
    }
    @Test void deletingAnotherImagePreservesThumbnail() throws Exception {
        var product=Product.builder().thumbnail("cover.png").build();
        when(images.findByIdForUpdate(2L)).thenReturn(Optional.of(ProductImage.builder().product(product).imageUrl("other.png").build()));
        when(images.findProductIdByImageId(2L)).thenReturn(Optional.of(1L));
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        service.deleteProductImage(2L);
        assertThat(product.getThumbnail()).isEqualTo("cover.png"); verify(products, never()).saveAndFlush(any());
    }
    @Test void unknownImageDoesNotModifyProduct() {
        assertThatThrownBy(()->service.deleteProductImage(99L)).hasMessageContaining("Image not found");
        verify(products, never()).saveAndFlush(any()); verify(images,never()).deleteById(any());
    }
}