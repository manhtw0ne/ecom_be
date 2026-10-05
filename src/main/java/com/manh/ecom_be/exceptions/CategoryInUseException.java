package com.manh.ecom_be.exceptions;

public class CategoryInUseException extends IllegalStateException {
    public CategoryInUseException() { super("Cannot delete category with associated products"); }
}
