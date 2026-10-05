package com.manh.ecom_be.utils;

import com.manh.ecom_be.exceptions.InvalidImageException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.file.*;
import java.util.UUID;

public final class FileUtils {
    private static String UPLOADS_FOLDER = "uploads";
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final long MAX_PIXELS = 16_000_000;
    private FileUtils() {}

    private record Decoded(BufferedImage image, String format) {}

    private static Path root() throws IOException {
        Path path = Paths.get(UPLOADS_FOLDER).toAbsolutePath().normalize();
        Files.createDirectories(path);
        return path.toRealPath();
    }

    private static Path resolve(String name) throws IOException {
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,199}") || name.contains("..")) {
            throw new IOException("Invalid image name");
        }
        Path root = root();
        Path file = root.resolve(name).normalize();
        if (!root.equals(file.getParent())) throw new IOException("Invalid image path");
        return file;
    }

    public static void deleteFile(String name) throws IOException {
        Path file = resolve(name);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Not a regular image");
        Files.delete(file);
    }

    private static byte[] limitedBytes(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new MaxUploadSizeExceededException(MAX_BYTES);
        return bytes;
    }

    private static Decoded decode(byte[] bytes) {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new InvalidImageException();
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new InvalidImageException();
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg")) throw new InvalidImageException();
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 4096 || height > 4096
                        || (long) width * height > MAX_PIXELS) throw new InvalidImageException();
                BufferedImage image = reader.read(0);
                if (image == null) throw new InvalidImageException();
                return new Decoded(image, format);
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException ex) {
            throw new InvalidImageException();
        }
    }

    public static boolean isImageFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) return false;
        try (var input = file.getInputStream()) {
            decode(limitedBytes(input));
            return true;
        } catch (IOException | RuntimeException ex) { return false; }
    }

    public static String storeFile(MultipartFile file) throws IOException {
        return store(file, "");
    }

    public static String storeAvatar(MultipartFile file, Long ownerId) throws IOException {
        if (ownerId == null || ownerId <= 0) throw new IllegalArgumentException("Invalid owner");
        return store(file, "avatar-" + ownerId + "-");
    }

    private static String store(MultipartFile file, String prefix) throws IOException {
        if (file == null || file.isEmpty()) throw new InvalidImageException();
        if (file.getSize() > MAX_BYTES) throw new MaxUploadSizeExceededException(MAX_BYTES);
        Decoded decoded;
        try (var input = file.getInputStream()) { decoded = decode(limitedBytes(input)); }
        String name = prefix + UUID.randomUUID() + (decoded.format().equals("png") ? ".png" : ".jpg");
        Path destination = resolve(name);
        // Re-encode decoded pixels: never keep the supplied filename, metadata or appended payload.
        boolean created = false;
        try {
            var stream = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            created = true;
            try (var output = stream) {
                if (!ImageIO.write(decoded.image(), decoded.format(), output)) throw new InvalidImageException();
            }
        } catch (IOException | RuntimeException ex) {
            if (created) Files.deleteIfExists(destination);
            throw ex;
        }
        if (Files.size(destination) > MAX_BYTES) {
            Files.delete(destination);
            throw new MaxUploadSizeExceededException(MAX_BYTES);
        }
        return name;
    }

    public static boolean isManagedAvatar(String name, Long userId) {
        return name != null && name.matches("avatar-" + userId
                + "-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(png|jpg)");
    }

    public static ResponseEntity<?> imageResponse(String name) {
        try {
            Path path = resolve(name);
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return ResponseEntity.notFound().build();
            byte[] bytes;
            try (var channel = Files.newByteChannel(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
                 var input = Channels.newInputStream(channel)) {
                bytes = limitedBytes(input);
            }
            Decoded decoded = decode(bytes);
            return ResponseEntity.ok().header("X-Content-Type-Options", "nosniff")
                    .contentType(decoded.format().equals("png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                    .body(new ByteArrayResource(bytes));
        } catch (IOException | RuntimeException ex) { return ResponseEntity.notFound().build(); }
    }
}
