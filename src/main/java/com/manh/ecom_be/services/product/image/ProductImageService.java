package com.manh.ecom_be.services.product.image;

import com.manh.ecom_be.components.SecurityUtils;
import com.manh.ecom_be.components.TransactionCallbacks;
import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.models.ProductImage;
import com.manh.ecom_be.repositories.ProductImageRepository;
import com.manh.ecom_be.repositories.ProductRepository;
import com.manh.ecom_be.utils.FileUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductImageService implements InterfaceProductImageService {
    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;
    private final SecurityUtils securityUtils;
    private final EntityManager entityManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductImage deleteProductImage(Long id) throws DataNotFoundException {
        securityUtils.requireAdmin();
        Long productId = productImageRepository.findProductIdByImageId(id)
                .orElseThrow(() -> new DataNotFoundException("Image not found"));
        // Same lock order as upload: product first, then its image rows.
        var product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new DataNotFoundException("Product not found"));
        entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
        var image = productImageRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new DataNotFoundException("Image not found"));
        entityManager.refresh(image, LockModeType.PESSIMISTIC_WRITE);
        // Keep both sides consistent: CascadeType.ALL could otherwise persist the deleted child again.
        if (product.getProductImages() != null) {
            product.getProductImages().removeIf(existing -> Objects.equals(existing.getId(), id));
        }
        productImageRepository.delete(image);
        productImageRepository.flush();
        if (Objects.equals(image.getImageUrl(), product.getThumbnail())) {
            var remaining = productImageRepository.findAllForUpdate(productId);
            product.setThumbnail(remaining.stream().min(Comparator.comparing(ProductImage::getId))
                    .map(ProductImage::getImageUrl).orElse(null));
            productRepository.saveAndFlush(product);
        }
        String filename = image.getImageUrl();
        // Only generated product filenames are owned by this upload flow; preserve legacy/shared paths.
        if (filename != null && filename.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(png|jpg)")) {
            TransactionCallbacks.afterCommit(() -> {
                try { FileUtils.deleteFile(filename); }
                catch (java.io.IOException ex) { log.warn("Unable to delete committed product image {}", filename, ex); }
            });
        }
        return image;
    }
}
