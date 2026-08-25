package com.example.gatewaysample.apiserver.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Mirrors pricing-service's wire contract ({@code PriceRequest}); validated here too as defense in depth. */
public record PriceRequest(
        @NotNull Long productId,
        @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal amount,
        @NotNull @Size(min = 3, max = 3) String currency) {}
