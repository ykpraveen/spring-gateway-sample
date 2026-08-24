package com.example.gatewaysample.gateway.ratelimit;

/** Which of the three independent Redis token buckets rejected a request. */
public enum RateLimitScope {
    ROUTE,
    CLIENT,
    IP;

    /** RFC 9457 Problem Details {@code code} extension member for this scope. */
    public String problemCode() {
        return name() + "_LIMIT_EXCEEDED";
    }
}
