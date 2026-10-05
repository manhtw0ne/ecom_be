package com.manh.ecom_be.controllers;

import com.manh.ecom_be.models.*;
import com.manh.ecom_be.repositories.*;
import com.manh.ecom_be.services.product.image.ProductImageUploadService;
import com.manh.ecom_be.utils.FileUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductImageBatchTest {
    @Autowired ProductImageUploadService uploads;
    @Autowired com.manh.ecom_be.services.product.ProductService productService;
    @Autowired com.manh.ecom_be.services.product.image.ProductImageService deletions;
    @Autowired ProductRepository products;
    @Autowired ProductImageRepository images;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired OrderDetailRepository orderDetails;
    @TempDir Path directory;
    Object previous;
    Product product;
    User admin;
    MockMultipartFile png;

    @BeforeEach void seed() throws Exception {
        previous = ReflectionTestUtils.getField(FileUtils.class, "UPLOADS_FOLDER");
        ReflectionTestUtils.setField(FileUtils.class, "UPLOADS_FOLDER", directory.toString());
        product = products.save(Product.builder().name("Batch test").price(BigDecimal.TEN).stockQuantity(2)
                .category(categories.save(Category.builder().name("Batch").build())).build());
        admin = users.save(User.builder().email("batch" + System.nanoTime() + "@example.test")
                .active(true).password("hash").role(roles.save(Role.builder().name("ADMIN").build())).build());
        authenticate();
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        png = new MockMultipartFile("files", "image.png", "image/png", output.toByteArray());
    }
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
    }
    @AfterEach void restore() {
        TestSecurityContextHolder.clearContext();
        ReflectionTestUtils.setField(FileUtils.class, "UPLOADS_FOLDER", previous);
    }
    List<String> filenames() throws Exception {
        try (var files = Files.list(directory)) { return files.map(p -> p.getFileName().toString()).sorted().toList(); }
    }
    void assertEmpty() throws Exception {
        assertThat(images.findByProductId(product.getId())).isEmpty();
        assertThat(products.findById(product.getId()).orElseThrow().getThumbnail()).isNull();
        assertThat(filenames()).isEmpty();
    }

    @Test void httpBatchStoresBothImagesAndFirstThumbnail() throws Exception {
        mvc.perform(multipart("/api/v1/products/uploads/" + product.getId()).file(png).file(png).with(user(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        var saved = images.findByProductId(product.getId());
        assertThat(saved).hasSize(2);
        assertThat(filenames()).containsExactlyInAnyOrderElementsOf(saved.stream().map(ProductImage::getImageUrl).toList());
        assertThat(products.findById(product.getId()).orElseThrow().getThumbnail())
                .isIn(saved.stream().map(ProductImage::getImageUrl).toArray());
    }

    @Test void invalidSecondImageRollsBackFirstFileAndMetadata() throws Exception {
        var fake = new MockMultipartFile("files", "fake.png", "image/png", "not an image".getBytes());
        mvc.perform(multipart("/api/v1/products/uploads/" + product.getId()).file(png).file(fake).with(user(admin)))
                .andExpect(status().isUnsupportedMediaType());
        assertEmpty();
    }

    @Test void outerRollbackCleansEveryCreatedFile() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            try { uploads.upload(product.getId(), List.of(png, png)); }
            catch (Exception ex) { throw new RuntimeException(ex); }
            status.setRollbackOnly();
        });
        assertEmpty();
    }

    @Test void capacityFailurePreservesExistingImages() throws Exception {
        uploads.upload(product.getId(), List.of(png, png, png, png));
        var before = filenames();
        mvc.perform(multipart("/api/v1/products/uploads/" + product.getId()).file(png).file(png).with(user(admin)))
                .andExpect(status().isBadRequest());
        assertThat(images.findByProductId(product.getId())).hasSize(4);
        assertThat(filenames()).isEqualTo(before);
    }

    @Test void emptyBatchIsRejected() throws Exception {
        mvc.perform(multipart("/api/v1/products/uploads/" + product.getId()).with(user(admin)))
                .andExpect(status().isBadRequest());
        assertEmpty();
    }

    @Test void buyerCannotBypassControllerThroughService() throws Exception {
        admin.setRole(Role.builder().name("USER").build());
        authenticate();
        assertThatThrownBy(() -> uploads.upload(product.getId(), List.of(png)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertEmpty();
    }

    @Test void deletionCommitsBeforeRemovingFileAndReplacesThumbnail() throws Exception {
        var saved = uploads.upload(product.getId(), List.of(png, png));
        mvc.perform(delete("/api/v1/product-images/" + saved.get(0).getId()).with(user(admin)))
                .andExpect(status().isOk());
        assertThat(directory.resolve(saved.get(0).getImageUrl())).doesNotExist();
        assertThat(directory.resolve(saved.get(1).getImageUrl())).exists();
        assertThat(products.findById(product.getId()).orElseThrow().getThumbnail()).isEqualTo(saved.get(1).getImageUrl());
        mvc.perform(delete("/api/v1/product-images/" + saved.get(0).getId()).with(user(admin)))
                .andExpect(status().isNotFound());
    }

    @Test void deletionRollbackKeepsFileMetadataAndThumbnail() throws Exception {
        var saved = uploads.upload(product.getId(), List.of(png)).getFirst();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            try { deletions.deleteProductImage(saved.getId()); }
            catch (Exception ex) { throw new RuntimeException(ex); }
            assertThat(directory.resolve(saved.getImageUrl())).exists();
            status.setRollbackOnly();
        });
        assertThat(images.existsById(saved.getId())).isTrue();
        assertThat(directory.resolve(saved.getImageUrl())).exists();
        assertThat(products.findById(product.getId()).orElseThrow().getThumbnail()).isEqualTo(saved.getImageUrl());
    }

    @Test void deletionRequiresAdminAtService() throws Exception {
        var saved = uploads.upload(product.getId(), List.of(png)).getFirst();
        admin.setRole(Role.builder().name("USER").build());
        authenticate();
        assertThatThrownBy(() -> deletions.deleteProductImage(saved.getId()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(directory.resolve(saved.getImageUrl())).exists();
        assertThat(images.existsById(saved.getId())).isTrue();
    }

    @Test void deletingLastImageClearsThumbnail() throws Exception {
        var saved = uploads.upload(product.getId(), List.of(png)).getFirst();
        deletions.deleteProductImage(saved.getId());
        assertEmpty();
    }

    @Test void productDeletionHidesProductButRetainsImageMetadataAndFile() throws Exception {
        var image = uploads.upload(product.getId(), List.of(png)).getFirst();
        mvc.perform(delete("/api/v1/products/" + product.getId()).with(user(admin))).andExpect(status().isOk());
        assertThat(products.findById(product.getId())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from product_images where id = ?", Integer.class, image.getId())).isEqualTo(1);
        assertThat(directory.resolve(image.getImageUrl())).exists();
        mvc.perform(delete("/api/v1/products/" + product.getId()).with(user(admin))).andExpect(status().isNotFound());
        authenticate();
        assertThatThrownBy(() -> uploads.upload(product.getId(), List.of(png)))
                .isInstanceOf(com.manh.ecom_be.exceptions.DataNotFoundException.class);
    }

    @Test void productDeletionRollbackRestoresVisibility() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            try { productService.deleteProduct(product.getId()); }
            catch (Exception ex) { throw new RuntimeException(ex); }
            status.setRollbackOnly();
        });
        assertThat(products.findById(product.getId())).isPresent();
    }

    @Test void productReferencedByOrderDetailCannotBeDeleted() throws Exception {
        orderDetails.saveAndFlush(OrderDetail.builder().product(product).price(java.math.BigDecimal.TEN)
                .numberOfProducts(1).totalMoney(java.math.BigDecimal.TEN).build());
        mvc.perform(delete("/api/v1/products/" + product.getId()).with(user(admin)))
                .andExpect(status().isBadRequest());
        assertThat(products.findById(product.getId())).isPresent();
    }

    @Test void buyerCannotDeleteProductThroughService() {
        admin.setRole(Role.builder().name("USER").build());
        authenticate();
        assertThatThrownBy(() -> productService.deleteProduct(product.getId()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(products.findById(product.getId())).isPresent();
    }

    com.manh.ecom_be.dtos.ProductDTO update(String thumbnail) {
        return com.manh.ecom_be.dtos.ProductDTO.builder().name("Edited product")
                .categoryId(product.getCategory().getId()).price(java.math.BigDecimal.TEN)
                .thumbnail(thumbnail).build();
    }

    @Test void canSelectAnUploadedImageAsThumbnail() throws Exception {
        var saved = uploads.upload(product.getId(), List.of(png, png));
        productService.updateProduct(product.getId(), update(saved.get(1).getImageUrl()));
        assertThat(products.findById(product.getId()).orElseThrow().getThumbnail()).isEqualTo(saved.get(1).getImageUrl());
    }

    @Test void foreignMissingAndDeletedImagesCannotBecomeThumbnail() throws Exception {
        var saved = uploads.upload(product.getId(), List.of(png)).getFirst();
        var other = products.save(Product.builder().name("Other").price(java.math.BigDecimal.TEN)
                .category(product.getCategory()).stockQuantity(1).build());
        var foreign = uploads.upload(other.getId(), List.of(png)).getFirst();
        for (String name : List.of(foreign.getImageUrl(), "missing.png", "../secret", "https://example.test/a.png")) {
            assertThatThrownBy(() -> productService.updateProduct(product.getId(), update(name)))
                    .isInstanceOf(com.manh.ecom_be.exceptions.InvalidParamException.class);
            assertThat(products.findById(product.getId()).orElseThrow().getName()).isEqualTo("Batch test");
        }
        deletions.deleteProductImage(saved.getId());
        assertThatThrownBy(() -> productService.updateProduct(product.getId(), update(saved.getImageUrl())))
                .isInstanceOf(com.manh.ecom_be.exceptions.InvalidParamException.class);
    }

    @Test void creationCannotAttachArbitraryThumbnail() {
        assertThatThrownBy(() -> productService.createProduct(update("foreign.png")))
                .isInstanceOf(com.manh.ecom_be.exceptions.InvalidParamException.class);
    }

    @Test void selectingAndDeletingSameImageCannotLeaveDanglingThumbnail() throws Exception {
        var saved = uploads.upload(product.getId(), List.of(png, png));
        var selected = saved.get(1);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var selection = pool.submit(() -> {
                authenticate();
                try {
                    start.await();
                    productService.updateProduct(product.getId(), update(selected.getImageUrl()));
                } catch (com.manh.ecom_be.exceptions.InvalidParamException expected) { /* Delete won. */ }
                finally { SecurityContextHolder.clearContext(); }
                return true;
            });
            var deletion = pool.submit(() -> {
                authenticate();
                try { start.await(); return deletions.deleteProductImage(selected.getId()).getId(); }
                finally { SecurityContextHolder.clearContext(); }
            });
            start.countDown();
            selection.get(15, TimeUnit.SECONDS);
            deletion.get(15, TimeUnit.SECONDS);
        }
        assertThat(products.findById(product.getId()).orElseThrow().getThumbnail()).isEqualTo(saved.get(0).getImageUrl());
    }

    @Test void uploadAndDeletionKeepThumbnailPointingToRemainingImage() throws Exception {
        var old = uploads.upload(product.getId(), List.of(png)).getFirst();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var upload = pool.submit(() -> {
                authenticate();
                try { start.await(); return uploads.upload(product.getId(), List.of(png)).getFirst().getImageUrl(); }
                finally { SecurityContextHolder.clearContext(); }
            });
            var deletion = pool.submit(() -> {
                authenticate();
                try { start.await(); return deletions.deleteProductImage(old.getId()).getId(); }
                finally { SecurityContextHolder.clearContext(); }
            });
            start.countDown();
            String current = upload.get(15, TimeUnit.SECONDS);
            deletion.get(15, TimeUnit.SECONDS);
            assertThat(filenames()).containsExactly(current);
            assertThat(images.findByProductId(product.getId())).hasSize(1);
            assertThat(products.findById(product.getId()).orElseThrow().getThumbnail()).isEqualTo(current);
        }
    }

    @Test void concurrentBatchesCannotExceedFiveImages() throws Exception {
        uploads.upload(product.getId(), List.of(png, png, png, png));
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task = () -> {
                authenticate();
                try {
                    start.await();
                    uploads.upload(product.getId(), List.of(png));
                    return true;
                } catch (com.manh.ecom_be.exceptions.InvalidParamException expected) { return false; }
                finally { SecurityContextHolder.clearContext(); }
            };
            var a = pool.submit(task);
            var b = pool.submit(task);
            start.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(images.findByProductId(product.getId())).hasSize(5);
        assertThat(filenames()).hasSize(5);
    }
}
