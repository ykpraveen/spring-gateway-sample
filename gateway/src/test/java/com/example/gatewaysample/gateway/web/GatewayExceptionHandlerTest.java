package com.example.gatewaysample.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.gatewaysample.gateway.ratelimit.RateLimitScope;
import com.example.gatewaysample.gateway.web.exception.InsufficientRoleException;
import com.example.gatewaysample.gateway.web.exception.InvalidApiKeyException;
import com.example.gatewaysample.gateway.web.exception.MissingApiKeyException;
import com.example.gatewaysample.gateway.web.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class GatewayExceptionHandlerTest {

    private final GatewayExceptionHandler handler = new GatewayExceptionHandler();

    @Test
    void missingApiKeyIsUnauthorizedWithADistinctCode() {
        String body = handle(new MissingApiKeyException(), HttpStatus.UNAUTHORIZED);
        assertThat(body).contains("\"code\":\"MISSING_API_KEY\"");
    }

    @Test
    void invalidApiKeyIsUnauthorizedWithADistinctCode() {
        String body = handle(new InvalidApiKeyException(), HttpStatus.UNAUTHORIZED);
        assertThat(body).contains("\"code\":\"INVALID_API_KEY\"");
    }

    @Test
    void insufficientRoleIsForbidden() {
        String body = handle(new InsufficientRoleException("catalog.write"), HttpStatus.FORBIDDEN);
        assertThat(body).contains("\"code\":\"INSUFFICIENT_ROLE\"");
    }

    @Test
    void rateLimitExceededIsTooManyRequestsWithScopedCodeAndRetryAfter() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products").build());

        handler.handle(exchange, new RateLimitExceededException(RateLimitScope.CLIENT)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isNotBlank();
        assertThat(bodyOf(exchange)).contains("\"code\":\"CLIENT_LIMIT_EXCEEDED\"");
    }

    private String handle(Throwable ex, HttpStatus expectedStatus) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products").build());

        handler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(expectedStatus);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        return bodyOf(exchange);
    }

    private String bodyOf(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }
}
