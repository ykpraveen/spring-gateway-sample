package com.example.gatewaysample.gateway.apikey.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.gatewaysample.gateway.apikey.dto.ApiClientPrincipal;
import com.example.gatewaysample.gateway.apikey.service.ApiKeyValidationService;
import com.example.gatewaysample.gateway.config.GatewayAttributes;
import com.example.gatewaysample.gateway.web.exception.InvalidApiKeyException;
import com.example.gatewaysample.gateway.web.exception.MissingApiKeyException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class ApiKeyAuthenticationGlobalFilterTest {

    private final ApiKeyValidationService validationService = mock(ApiKeyValidationService.class);
    private final ApiKeyAuthenticationGlobalFilter filter = new ApiKeyAuthenticationGlobalFilter(validationService);

    @Test
    void rejectsARequestWithNoApiKeyHeader() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products").build());

        assertThatThrownBy(() -> filter.filter(exchange, ex -> Mono.empty()).block())
                .isInstanceOf(MissingApiKeyException.class);
    }

    @Test
    void rejectsARequestWithAnUnknownApiKey() {
        when(validationService.validate("bad-key")).thenReturn(Mono.empty());
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products").header("X-API-Key", "bad-key").build());

        assertThatThrownBy(() -> filter.filter(exchange, ex -> Mono.empty()).block())
                .isInstanceOf(InvalidApiKeyException.class);
    }

    @Test
    void storesTheValidatedPrincipalAndContinuesTheChainForAGoodKey() {
        ApiClientPrincipal principal = new ApiClientPrincipal(1L, "acme", "standard");
        when(validationService.validate("good-key")).thenReturn(Mono.just(principal));
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products").header("X-API-Key", "good-key").build());

        filter.filter(exchange, ex -> Mono.empty()).block();

        ApiClientPrincipal stored = exchange.getAttribute(GatewayAttributes.API_CLIENT_PRINCIPAL);
        assertThat(stored).isEqualTo(principal);
    }
}
