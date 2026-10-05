package com.manh.ecom_be.exceptions;

public class InvalidImageException extends RuntimeException {
    public InvalidImageException() {
        super("A valid PNG/JPEG image up to 4096 pixels per side and 16 megapixels is required");
    }
}