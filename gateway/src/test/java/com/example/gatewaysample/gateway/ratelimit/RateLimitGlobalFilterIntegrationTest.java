package com.example.gatewaysample.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.gatewaysample.gateway.AbstractIntegrationTest;
import com.example.gatewaysample.gateway.web.exception.RateLimitExceededException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Proves the route/client/IP buckets are independent and that refund-on-reject leaves earlier
 * buckets untouched by a later rejection, exercising the actual {@link RateLimitGlobalFilter}
 * rather than the raw {@link TokenBucketRateLimiter} primitives.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "app.rate-limit.route.read.replenish-rate=1",
            "app.rate-limit.route.read.burst-capacity=5",
            "app.rate-limit.client.replenish-rate=1",
            "app.rate-limit.client.burst-capacity=1",
            "app.rate-limit.ip.replenish-rate=100",
            "app.rate-limit.ip.burst-capacity=100"
        })
class RateLimitGlobalFilterIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RateLimitGlobalFilter filter;

    @Test
    void aRejectedClientRequestRefundsTheRouteBucketWithoutAffectingOtherClients() {
        Route route = mock(Route.class);
        when(route.getId()).thenReturn("test-route-" + UUID.randomUUID());
        when(route.getMetadata()).thenReturn(Map.of("rate-limit-category", "read"));

        // Client A's first request succeeds: consumes 1 from route (4 left) and 1 from client (0 left).
        request(route, "client-a").block();

        // Client A's second request is rejected on the exhausted client bucket; the route
        // consumption from this attempt must be refunded, leaving the route bucket at 4 (not 3).
        assertThatThrownBy(() -> request(route, "client-a").block())
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> assertThat(((RateLimitExceededException) ex).scope()).isEqualTo(RateLimitScope.CLIENT));

        // Different clients can still consume the route bucket's remaining 4 tokens. If the
        // refund above had leaked, only 3 would be available here.
        request(route, "client-b").block();
        request(route, "client-c").block();
        request(route, "client-d").block();
        request(route, "client-e").block();
        assertThatThrownBy(() -> request(route, "client-f").block())
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> assertThat(((RateLimitExceededException) ex).scope()).isEqualTo(RateLimitScope.ROUTE));
    }

    private Mono<Void> request(Route route, String jwtSubject) {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/products").build());
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);

        return filter.filter(exchange, ex -> Mono.empty())
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new TestingAuthenticationToken(jwtSubject, null)));
    }
}
