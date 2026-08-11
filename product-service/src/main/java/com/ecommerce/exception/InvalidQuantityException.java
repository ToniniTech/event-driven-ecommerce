package com.ecommerce.exception;

public class InvalidQuantityException extends RuntimeException {
    public InvalidQuantityException(int requestedQuantity) {
        super("Requested quantity must be greater that zero. Received: " + requestedQuantity);
    }
}
