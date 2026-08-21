package com.example.gatewaysample.gateway.apikey.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table(value = "api_client", schema = "gateway")
public record ApiClient(
        @Id Long id,
        String name,
        String keyHash,
        String tier,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {}
