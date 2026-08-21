package com.example.gatewaysample.gateway.apikey.dto;

/** The raw API key, returned once at provisioning/rotation time; only its digest is ever persisted. */
public record IssuedApiKey(Long apiClientId, String rawKey) {}
