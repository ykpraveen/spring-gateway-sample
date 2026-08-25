package com.example.gatewaysample.apiserver.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mirrors product-service's wire contract ({@code ProductRequest}); validated here too as defense in depth. */
public record ProductRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description,
        Boolean active) {}
