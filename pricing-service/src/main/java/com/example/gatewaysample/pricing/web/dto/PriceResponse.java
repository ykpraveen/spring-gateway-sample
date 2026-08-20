package com.example.gatewaysample.pricing.web.dto;

import com.example.gatewaysample.pricing.domain.Price;
import java.math.BigDecimal;
import java.time.Instant;

public record PriceResponse(
        Long id,
        Long productId,
        BigDecimal amount,
        String currency,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static PriceResponse from(Price price) {
        return new PriceResponse(
                price.getId(),
                price.getProductId(),
                price.getAmount(),
                price.getCurrency(),
                price.isActive(),
                price.getCreatedAt(),
                price.getUpdatedAt());
    }
}
