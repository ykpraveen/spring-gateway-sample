package com.example.gatewaysample.product.web.dto;

import com.example.gatewaysample.product.domain.Product;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
