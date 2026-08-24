package com.example.gatewaysample.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

class CorsConfigTest {

    @Test
    void allowsTheConfiguredOriginAndTheJwtAndApiKeyHeaders() {
        CorsConfigurationSource source =
                new CorsConfig().corsConfigurationSource(new String[] {"http://localhost:5173"});

        CorsConfiguration configuration = source.getCorsConfiguration(
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/products").build()));

        assertThat(configuration.getAllowedOrigins()).containsExactly("http://localhost:5173");
        assertThat(configuration.getAllowedHeaders()).contains("Authorization", "X-API-Key");
        assertThat(configuration.getAllowCredentials()).isTrue();
    }
}
