package com.example.gatewaysample.product.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description,
        Boolean active) {

    public boolean activeOrDefault() {
        return active == null || active;
    }
}
