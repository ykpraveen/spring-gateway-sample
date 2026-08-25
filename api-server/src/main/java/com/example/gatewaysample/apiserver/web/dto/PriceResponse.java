package com.example.gatewaysample.apiserver.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Mirrors pricing-service's wire contract ({@code PriceResponse}) for WebClient deserialization. */
public record PriceResponse(
        Long id, Long productId, BigDecimal amount, String currency, boolean active, Instant createdAt, Instant updatedAt) {}
