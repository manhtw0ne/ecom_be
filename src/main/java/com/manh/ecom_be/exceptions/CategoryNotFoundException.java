package com.manh.ecom_be.exceptions;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException() { super("Category not found"); }
}
