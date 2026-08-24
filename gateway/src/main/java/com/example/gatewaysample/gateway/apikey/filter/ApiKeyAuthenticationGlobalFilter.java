package com.example.gatewaysample.gateway.apikey.filter;

import com.example.gatewaysample.gateway.apikey.service.ApiKeyValidationService;
import com.example.gatewaysample.gateway.config.GatewayAttributes;
import com.example.gatewaysample.gateway.web.exception.InvalidApiKeyException;
import com.example.gatewaysample.gateway.web.exception.MissingApiKeyException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Second credential check in the fixed ordering documented under "Auth model": by the time this
 * filter runs, Spring Security has already accepted the JWT (missing/expired tokens never reach
 * it), so a missing or invalid {@code X-API-Key} header is reported as a 401 distinct from the
 * JWT failure. Runs before {@link com.example.gatewaysample.gateway.authorization.RouteAuthorizationGlobalFilter}
 * so role checks (403) only happen once credentials are fully valid.
 */
@Component
public class ApiKeyAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyValidationService validationService;

    public ApiKeyAuthenticationGlobalFilter(ApiKeyValidationService validationService) {
        this.validationService = validationService;
    }

    @Override
    public int getOrder() {
        return -200;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.error(new MissingApiKeyException());
        }
        return validationService
                .validate(apiKey)
                .switchIfEmpty(Mono.error(new InvalidApiKeyException()))
                .flatMap(principal -> {
                    exchange.getAttributes().put(GatewayAttributes.API_CLIENT_PRINCIPAL, principal);
                    return chain.filter(exchange);
                });
    }
}
