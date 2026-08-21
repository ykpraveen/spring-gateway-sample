package com.example.gatewaysample.gateway.apikey.dto;

/** Validated API-client identity, cached in Redis keyed by the API key's digest. */
public record ApiClientPrincipal(Long id, String name, String tier) {}
