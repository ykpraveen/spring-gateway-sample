package com.example.gatewaysample.apiserver.web.dto;

import java.time.Instant;

/** Mirrors product-service's wire contract ({@code ProductResponse}) for WebClient deserialization. */
public record ProductResponse(
        Long id, String sku, String name, String description, boolean active, Instant createdAt, Instant updatedAt) {}
