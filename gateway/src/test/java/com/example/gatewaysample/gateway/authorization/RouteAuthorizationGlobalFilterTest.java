package com.example.gatewaysample.gateway.authorization;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.gatewaysample.gateway.web.exception.InsufficientRoleException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class RouteAuthorizationGlobalFilterTest {

    private final RouteAuthorizationGlobalFilter filter = new RouteAuthorizationGlobalFilter();

    @Test
    void allowsTheRequestWhenTheRouteHasNoRequiredAuthority() {
        Route route = mock(Route.class);
        when(route.getMetadata()).thenReturn(Map.of());

        assertThatNoException().isThrownBy(() -> request(route, "catalog.read").block());
    }

    @Test
    void rejectsWithForbiddenWhenTheJwtLacksTheRequiredAuthority() {
        Route route = mock(Route.class);
        when(route.getMetadata()).thenReturn(Map.of("required-authority", "catalog.write"));

        assertThatThrownBy(() -> request(route, "catalog.read").block()).isInstanceOf(InsufficientRoleException.class);
    }

    @Test
    void allowsTheRequestWhenTheJwtHasTheRequiredAuthority() {
        Route route = mock(Route.class);
        when(route.getMetadata()).thenReturn(Map.of("required-authority", "catalog.write"));

        assertThatNoException().isThrownBy(() -> request(route, "catalog.write").block());
    }

    private Mono<Void> request(Route route, String authority) {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products").build());
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);

        return filter.filter(exchange, ex -> Mono.empty())
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        new TestingAuthenticationToken("test-user", null, authority)));
    }
}
