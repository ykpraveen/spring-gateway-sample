package com.example.gatewaysample.gateway.ratelimit;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis token-bucket capacities for the gateway's three independent rate-limit policies (route,
 * user/client, IP), plus the proxies allowed to have their {@code X-Forwarded-For} header trusted
 * when resolving a client IP.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(RouteBuckets route, Bucket client, Bucket ip, List<String> trustedProxies) {

    public RateLimitProperties {
        trustedProxies = trustedProxies == null ? List.of() : trustedProxies;
    }

    public record RouteBuckets(Bucket read, Bucket write) {}

    /** Replenish rate in tokens/second, and the maximum burst capacity a bucket can hold. */
    public record Bucket(long replenishRate, long burstCapacity) {}
}
