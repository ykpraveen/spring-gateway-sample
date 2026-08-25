package com.example.gatewaysample.apiserver.web.dto;

/**
 * Envelope used only for degraded (cache-fallback) GET responses; live responses return the
 * resource body unwrapped, preserving the one-to-one downstream passthrough shape.
 */
public record DegradedResponse<T>(T data, DegradedMeta meta) {}
