package com.manh.ecom_be.exceptions;

public class OutOfStockException extends RuntimeException {
    public OutOfStockException(Long productId) {
        super("Insufficient stock for product: " + productId);
    }
}
