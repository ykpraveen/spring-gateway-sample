package com.example.gatewaysample.gateway.authorization;

import com.example.gatewaysample.gateway.config.GatewayAttributes;
import com.example.gatewaysample.gateway.web.exception.InsufficientRoleException;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Third and final credential check in the fixed ordering documented under "Auth model": runs
 * after the JWT (Spring Security) and API-key ({@link
 * com.example.gatewaysample.gateway.apikey.filter.ApiKeyAuthenticationGlobalFilter}) checks have
 * both passed, so a role mismatch is reported as 403 rather than masking a credential failure.
 */
@Component
public class RouteAuthorizationGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return -190;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        Object required = route != null ? route.getMetadata().get(GatewayAttributes.ROUTE_METADATA_REQUIRED_AUTHORITY) : null;
        if (required == null) {
            return chain.filter(exchange);
        }
        String requiredAuthority = required.toString();

        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication().getAuthorities())
                .defaultIfEmpty(List.of())
                .flatMap(authorities -> {
                    boolean hasAuthority =
                            authorities.stream().map(GrantedAuthority::getAuthority).anyMatch(requiredAuthority::equals);
                    if (!hasAuthority) {
                        return Mono.error(new InsufficientRoleException(requiredAuthority));
                    }
                    return chain.filter(exchange);
                });
    }
}
