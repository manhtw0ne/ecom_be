package com.manh.ecom_be.utils;

import com.manh.ecom_be.exceptions.InvalidImageException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class ImageStorageTest {
    @TempDir Path directory;
    Object previous;
    @BeforeEach void isolate() {
        previous = ReflectionTestUtils.getField(FileUtils.class, "UPLOADS_FOLDER");
        ReflectionTestUtils.setField(FileUtils.class, "UPLOADS_FOLDER", directory.resolve("uploads").toString());
    }
    @AfterEach void restore() { ReflectionTestUtils.setField(FileUtils.class, "UPLOADS_FOLDER", previous); }

    public static byte[] picture(String format, int width) throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, 2, BufferedImage.TYPE_INT_RGB), format, output);
        return output.toByteArray();
    }

    @Test void detectsPixelsInsteadOfExtensionAndStripsAppendedPayload() throws Exception {
        var input = new ByteArrayOutputStream();
        input.write(picture("png", 2));
        input.write("<script>APPENDED_PAYLOAD</script>".getBytes());
        String name = FileUtils.storeAvatar(new MockMultipartFile("file", "../../fake.html", "text/html", input.toByteArray()), 12L);
        assertThat(name).startsWith("avatar-12-").endsWith(".png");
        assertThat(Files.readAllBytes(directory.resolve("uploads").resolve(name)))
                .isEqualTo(picture("png", 2));
        var response = FileUtils.imageResponse(name);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(FileUtils.isManagedAvatar(name, 13L)).isFalse();
    }

    @Test void jpegKeepsCorrectMediaType() throws Exception {
        String name = FileUtils.storeFile(new MockMultipartFile("file", "a.png", "image/png", picture("jpeg", 2)));
        assertThat(name).endsWith(".jpg");
        assertThat(FileUtils.imageResponse(name).getHeaders().getContentType().toString()).isEqualTo("image/jpeg");
    }

    @Test void rejectsForgedAndTruncatedImages() throws Exception {
        for (byte[] bytes : new byte[][] {"<svg/>".getBytes(), java.util.Arrays.copyOf(picture("png", 2), 25)}) {
            var file = new MockMultipartFile("file", "a.jpg", "image/jpeg", bytes);
            assertThatThrownBy(() -> FileUtils.storeFile(file)).isInstanceOf(InvalidImageException.class);
            assertThat(FileUtils.isImageFile(file)).isFalse();
        }
    }

    @Test void rejectsExcessiveDimensionsBeforeSaving() throws Exception {
        var file = new MockMultipartFile("file", picture("png", 4097));
        assertThatThrownBy(() -> FileUtils.storeFile(file)).isInstanceOf(InvalidImageException.class);
    }

    @Test void boundsActualStreamEvenWhenReportedSizeIsFalse() {
        var file = new MockMultipartFile("file", new byte[FileUtils.MAX_BYTES + 1]) {
            @Override public long getSize() { return 1; }
        };
        assertThatThrownBy(() -> FileUtils.storeFile(file)).isInstanceOf(MaxUploadSizeExceededException.class);
    }

    @Test void traversalCannotReadOrDeleteOutsideFile() throws Exception {
        Path outside = directory.resolve("outside.png");
        Files.write(outside, picture("png", 2));
        for (String name : new String[]{"../outside.png", "..\\outside.png", outside.toString(), "C:\\outside.png", "x:y.png"}) {
            assertThat(FileUtils.imageResponse(name).getStatusCode().value()).isEqualTo(404);
            assertThatThrownBy(() -> FileUtils.deleteFile(name)).isInstanceOf(IOException.class);
        }
        assertThat(outside).exists();
        assertThat(FileUtils.imageResponse("missing.png").getStatusCode().value()).isEqualTo(404);
    }
}
