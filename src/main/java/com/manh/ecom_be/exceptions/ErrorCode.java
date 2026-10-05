package com.manh.ecom_be.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // ─────────────── General (0xxx) ───────────────
    UNCATEGORIZED(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_FAILED(0001, "Validation failed", HttpStatus.BAD_REQUEST),
    INVALID_PARAM(0002, "Invalid parameter", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(0003, "Unauthorized", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(0004, "You do not have permission", HttpStatus.FORBIDDEN),
    METHOD_NOT_ALLOWED(0005, "HTTP method not supported", HttpStatus.METHOD_NOT_ALLOWED),

    // ─────────────── Product (1xxx) ───────────────
    PRODUCT_NOT_FOUND(1001, "Product not found", HttpStatus.NOT_FOUND),
    PRODUCT_IMAGE_LIMIT_EXCEEDED(1002, "Number of product images exceeds the maximum limit", HttpStatus.BAD_REQUEST),
    PRODUCT_ALREADY_EXISTS(1003, "Product already exists", HttpStatus.CONFLICT),

    // ─────────────── Category (1xxx) ───────────────
    CATEGORY_NOT_FOUND(1101, "Category not found", HttpStatus.NOT_FOUND),
    CATEGORY_HAS_PRODUCTS(1102, "Cannot delete category with associated products", HttpStatus.BAD_REQUEST),

    // ─────────────── User (2xxx) ───────────────
    USER_NOT_FOUND(2001, "User not found", HttpStatus.NOT_FOUND),
    INVALID_PASSWORD(2002, "Invalid password", HttpStatus.BAD_REQUEST),
    PASSWORD_NOT_MATCH(2003, "Passwords do not match", HttpStatus.BAD_REQUEST),
    USER_ALREADY_EXISTS(2004, "User already exists", HttpStatus.CONFLICT),
    TOKEN_EXPIRED(2005, "Token has expired", HttpStatus.UNAUTHORIZED),

    // ─────────────── Order (3xxx) ───────────────
    ORDER_NOT_FOUND(3001, "Order not found", HttpStatus.NOT_FOUND),
    ORDER_CANNOT_CANCEL(3002, "Order cannot be cancelled in current status", HttpStatus.BAD_REQUEST),
    INVALID_ORDER_STATUS(3003, "Invalid order status transition", HttpStatus.BAD_REQUEST),

    // ─────────────── Coupon (4xxx) ───────────────
    INVALID_COUPON(4001, "Coupon is invalid or expired", HttpStatus.BAD_REQUEST),

    // ─────────────── File Upload (5xxx) ───────────────
    FILE_TOO_LARGE(5001, "File size exceeds the maximum allowed limit", HttpStatus.PAYLOAD_TOO_LARGE),
    INVALID_FILE_TYPE(5002, "Uploaded file must be an image", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    // ─────────────── Data (6xxx) ───────────────
    DATA_INTEGRITY_VIOLATION(6001, "Data integrity violation: duplicate or invalid data", HttpStatus.CONFLICT),

    // ─────────────── Rate Limiting (7xxx) ───────────────
    TOO_MANY_REQUESTS(7001, "Too many requests. Please slow down.", HttpStatus.TOO_MANY_REQUESTS),
    ;

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
