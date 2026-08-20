package com.example.gatewaysample.pricing.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PriceRequest(
        @NotNull Long productId,
        @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal amount,
        @NotNull @Size(min = 3, max = 3) String currency) {
}
