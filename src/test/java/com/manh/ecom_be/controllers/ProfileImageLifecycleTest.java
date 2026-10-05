package com.manh.ecom_be.controllers;

import com.manh.ecom_be.models.*;
import com.manh.ecom_be.repositories.*;
import com.manh.ecom_be.services.user.ProfileImageService;
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
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileImageLifecycleTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ProfileImageService service;
    @Autowired PlatformTransactionManager transactions;
    @TempDir Path directory;
    Object previous;
    User owner;
    MockMultipartFile image;
    @BeforeEach void prepare() throws Exception {
        previous = ReflectionTestUtils.getField(FileUtils.class, "UPLOADS_FOLDER");
        ReflectionTestUtils.setField(FileUtils.class, "UPLOADS_FOLDER", directory.toString());
        owner = users.save(User.builder().email("image" + System.nanoTime() + "@example.test")
                .fullName("Image test").password("hash").active(true)
                .role(roles.save(Role.builder().name("USER").build())).build());
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        image = new MockMultipartFile("file", "test.png", "image/png", output.toByteArray());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities()));
    }
    @AfterEach void restore() {
        TestSecurityContextHolder.clearContext();
        ReflectionTestUtils.setField(FileUtils.class, "UPLOADS_FOLDER", previous);
    }
    String stored() { return users.findById(owner.getId()).orElseThrow().getProfileImage(); }

    @Test void replacementCommitsBeforeRemovingOldAvatar() throws Exception {
        String first = service.replace(image);
        assertThat(directory.resolve(first)).exists();
        String second = service.replace(image);
        assertThat(stored()).isEqualTo(second);
        assertThat(directory.resolve(first)).doesNotExist();
        assertThat(directory.resolve(second)).exists();
    }

    @Test void rollbackPreservesOldAvatarAndRemovesNewFile() throws Exception {
        String first = service.replace(image);
        var created = new AtomicReference<String>();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            try { created.set(service.replace(image)); }
            catch (Exception ex) { throw new RuntimeException(ex); }
            assertThat(directory.resolve(first)).exists();
            status.setRollbackOnly();
        });
        assertThat(stored()).isEqualTo(first);
        assertThat(directory.resolve(first)).exists();
        assertThat(directory.resolve(created.get())).doesNotExist();
    }

    @Test void sharedLegacyAvatarIsPreserved() throws Exception {
        Files.write(directory.resolve("default.png"), image.getBytes());
        owner.setProfileImage("default.png");
        users.saveAndFlush(owner);
        service.replace(image);
        assertThat(directory.resolve("default.png")).exists();
    }

    @Test void uploadAndPublicReadUseDetectedContentType() throws Exception {
        mvc.perform(multipart("/api/v1/users/upload-profile-image").file(image).with(user(owner)))
                .andExpect(status().isOk());
        TestSecurityContextHolder.clearContext();
        mvc.perform(get("/api/v1/users/profile-images/" + stored()))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test void rejectsInvalidEmptyAndOversizedUploadsWithoutChangingMetadata() throws Exception {
        String first = service.replace(image);
        mvc.perform(multipart("/api/v1/users/upload-profile-image")
                .file(new MockMultipartFile("file", "fake.png", "image/png", "<html/>".getBytes())).with(user(owner)))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/api/v1/users/upload-profile-image")
                .file(new MockMultipartFile("file", new byte[0])).with(user(owner)))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/users/upload-profile-image")
                .file(new MockMultipartFile("file", new byte[FileUtils.MAX_BYTES + 1])).with(user(owner)))
                .andExpect(status().isPayloadTooLarge());
        assertThat(stored()).isEqualTo(first);
        assertThat(directory.resolve(first)).exists();
    }

    @Test void anonymousCannotUpload() throws Exception {
        TestSecurityContextHolder.clearContext();
        mvc.perform(multipart("/api/v1/users/upload-profile-image").file(image))
                .andExpect(status().isUnauthorized());
    }

    @Test void concurrentReplacementsLeaveOnlyCommittedCurrentAvatar() throws Exception {
        service.replace(image);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> replace = () -> {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities()));
                try {
                    start.await();
                    return service.replace(image);
                } finally { SecurityContextHolder.clearContext(); }
            };
            var first = executor.submit(replace);
            var second = executor.submit(replace);
            start.countDown();
            String a = first.get(15, java.util.concurrent.TimeUnit.SECONDS);
            String b = second.get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(stored()).isIn(a, b);
            try (var files = Files.list(directory)) {
                assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactly(stored());
            }
        }
    }
}
