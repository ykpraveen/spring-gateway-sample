package com.example.gatewaysample.apiserver.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Both {@code productService} and {@code pricingService} circuit breakers use identical
 * thresholds (see PLAN.md "Circuit Breakers and Cached Fallbacks"), so one default configuration
 * covers every instance id created by {@link
 * com.example.gatewaysample.apiserver.downstream.AbstractDownstreamClient}.
 */
@Configuration
public class Resilience4jConfig {

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCircuitBreakerCustomizer(
            CircuitBreakerProperties properties) {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(properties.slidingWindowSize())
                        .minimumNumberOfCalls(properties.minimumNumberOfCalls())
                        .failureRateThreshold(properties.failureRateThreshold())
                        .slowCallDurationThreshold(properties.slowCallDurationThreshold())
                        .slowCallRateThreshold(properties.slowCallRateThreshold())
                        .waitDurationInOpenState(properties.waitDurationInOpenState())
                        .permittedNumberOfCallsInHalfOpenState(properties.permittedCallsInHalfOpenState())
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(properties.timeLimiterTimeout())
                        .build())
                .build());
    }
}
