package com.example.gatewaysample.gateway.ratelimit;

import com.example.gatewaysample.gateway.apikey.dto.ApiClientPrincipal;
import com.example.gatewaysample.gateway.config.GatewayAttributes;
import com.example.gatewaysample.gateway.web.exception.RateLimitExceededException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Applies three independent Redis token-bucket policies (route, user/client, IP), consumed
 * sequentially in that order with refund-on-reject: if a later bucket is empty, tokens already
 * consumed from earlier buckets in this request are returned so a rejected request doesn't
 * deplete other clients' or routes' quota. Runs after the API-key and role checks, so
 * unauthenticated/unauthorized requests never consume quota.
 */
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private final TokenBucketRateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final ClientIpResolver clientIpResolver;

    public RateLimitGlobalFilter(
            TokenBucketRateLimiter rateLimiter, RateLimitProperties properties, ClientIpResolver clientIpResolver) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public int getOrder() {
        return -180;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return chain.filter(exchange);
        }

        RateLimitProperties.Bucket routeBucket = routeBucketFor(route);
        String routeId = route.getId();
        ApiClientPrincipal apiClient = exchange.getAttribute(GatewayAttributes.API_CLIENT_PRINCIPAL);
        String clientIp = clientIpResolver.resolve(exchange);

        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication().getName())
                .defaultIfEmpty("anonymous")
                .flatMap(jwtSubject -> {
                    String routeKey = "rl:" + routeId + ":route";
                    String clientKey = "rl:" + routeId + ":client:" + apiClientId(apiClient) + ":user:" + jwtSubject;
                    String ipKey = "rl:" + routeId + ":ip:" + clientIp;

                    return consumeSequentially(routeKey, routeBucket, clientKey, properties.client(), ipKey, properties.ip())
                            .flatMap(allowed -> chain.filter(exchange));
                });
    }

    private Mono<Boolean> consumeSequentially(
            String routeKey,
            RateLimitProperties.Bucket routeBucket,
            String clientKey,
            RateLimitProperties.Bucket clientBucket,
            String ipKey,
            RateLimitProperties.Bucket ipBucket) {
        return rateLimiter.tryConsume(routeKey, routeBucket).flatMap(routeAllowed -> {
            if (!routeAllowed) {
                return Mono.error(new RateLimitExceededException(RateLimitScope.ROUTE));
            }
            return rateLimiter.tryConsume(clientKey, clientBucket).flatMap(clientAllowed -> {
                if (!clientAllowed) {
                    return rateLimiter
                            .refund(routeKey, routeBucket)
                            .then(Mono.error(new RateLimitExceededException(RateLimitScope.CLIENT)));
                }
                return rateLimiter.tryConsume(ipKey, ipBucket).flatMap(ipAllowed -> {
                    if (!ipAllowed) {
                        return rateLimiter
                                .refund(routeKey, routeBucket)
                                .then(rateLimiter.refund(clientKey, clientBucket))
                                .then(Mono.error(new RateLimitExceededException(RateLimitScope.IP)));
                    }
                    return Mono.just(true);
                });
            });
        });
    }

    private RateLimitProperties.Bucket routeBucketFor(Route route) {
        Object category = route.getMetadata().get(GatewayAttributes.ROUTE_METADATA_RATE_LIMIT_CATEGORY);
        return "write".equals(category != null ? category.toString() : null)
                ? properties.route().write()
                : properties.route().read();
    }

    private String apiClientId(ApiClientPrincipal apiClient) {
        return apiClient != null ? String.valueOf(apiClient.id()) : "none";
    }
}
