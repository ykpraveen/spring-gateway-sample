package com.example.gatewaysample.apiserver.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.circuit-breaker")
public record CircuitBreakerProperties(
        int slidingWindowSize,
        int minimumNumberOfCalls,
        float failureRateThreshold,
        Duration slowCallDurationThreshold,
        float slowCallRateThreshold,
        Duration waitDurationInOpenState,
        int permittedCallsInHalfOpenState,
        Duration timeLimiterTimeout) {}
