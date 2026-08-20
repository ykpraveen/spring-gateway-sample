package com.example.gatewaysample.pricing.exception;

public class PriceNotFoundException extends RuntimeException {

    private PriceNotFoundException(String message) {
        super(message);
    }

    public static PriceNotFoundException forId(Long id) {
        return new PriceNotFoundException("Price " + id + " was not found");
    }

    public static PriceNotFoundException forProduct(Long productId) {
        return new PriceNotFoundException("No active price found for product " + productId);
    }
}
