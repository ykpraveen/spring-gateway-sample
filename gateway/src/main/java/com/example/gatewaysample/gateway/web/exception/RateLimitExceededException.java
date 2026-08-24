package com.example.gatewaysample.gateway.web.exception;

import com.example.gatewaysample.gateway.ratelimit.RateLimitScope;

/** Thrown when one of the three Redis token buckets (route, client, IP) is exhausted. */
public class RateLimitExceededException extends RuntimeException {

    private final RateLimitScope scope;

    public RateLimitExceededException(RateLimitScope scope) {
        super("Rate limit exceeded: " + scope.name().toLowerCase() + " policy");
        this.scope = scope;
    }

    public RateLimitScope scope() {
        return scope;
    }
}
