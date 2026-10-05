package com.manh.ecom_be.services.product.image;

import com.manh.ecom_be.components.SecurityUtils;
import com.manh.ecom_be.exceptions.DataNotFoundException;
import com.manh.ecom_be.exceptions.InvalidParamException;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductImageUploadService {
    private final SecurityUtils securityUtils;
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final EntityManager entityManager;

    @Transactional(rollbackFor = Exception.class)
    public List<ProductImage> upload(Long productId, List<MultipartFile> files) throws Exception {
        securityUtils.requireAdmin();
        if (files == null || files.isEmpty() || files.size() > ProductImage.MAXIMUM_IMAGES_PER_PRODUCT
                || files.stream().anyMatch(file -> file == null || file.isEmpty())) {
            throw new InvalidParamException("Provide 1 to 5 non-empty images");
        }
        var product = products.findByIdForUpdate(productId)
                .orElseThrow(() -> new DataNotFoundException("Product not found"));
        entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
        if (images.findByProductId(productId).size() + files.size() > ProductImage.MAXIMUM_IMAGES_PER_PRODUCT) {
            throw new InvalidParamException("A product can have at most 5 images");
        }
        List<String> created = new ArrayList<>();
        // Filesystem writes do not participate in the database transaction.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    for (String name : created) {
                        try { FileUtils.deleteFile(name); }
                        catch (java.io.IOException ex) { log.warn("Unable to clean up product image {}", name, ex); }
                    }
                }
            }
        });
        List<ProductImage> result = new ArrayList<>();
        for (MultipartFile file : files) {
            String name = FileUtils.storeFile(file);
            created.add(name);
            result.add(images.save(ProductImage.builder().product(product).imageUrl(name).build()));
            if (product.getThumbnail() == null) product.setThumbnail(name);
        }
        images.flush();
        products.saveAndFlush(product);
        return result;
    }
}
