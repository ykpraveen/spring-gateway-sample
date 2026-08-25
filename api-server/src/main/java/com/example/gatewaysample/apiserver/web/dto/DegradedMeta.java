package com.example.gatewaysample.apiserver.web.dto;

import java.time.Instant;

public record DegradedMeta(boolean degraded, String source, String reason, Instant cachedAt) {

    public static DegradedMeta cache(String reason, Instant cachedAt) {
        return new DegradedMeta(true, "cache", reason, cachedAt);
    }
}
