package com.example.gatewaysample.gateway.config;

/** Well-known {@link org.springframework.web.server.ServerWebExchange} attribute keys shared across gateway filters. */
public final class GatewayAttributes {

    /** Holds the {@link com.example.gatewaysample.gateway.apikey.dto.ApiClientPrincipal} once the API key has been validated. */
    public static final String API_CLIENT_PRINCIPAL = "gateway.apiClientPrincipal";

    /** Route metadata key selecting which route-category bucket ({@code read}/{@code write}) applies. */
    public static final String ROUTE_METADATA_RATE_LIMIT_CATEGORY = "rate-limit-category";

    /** Route metadata key naming the JWT realm role required to use the route. */
    public static final String ROUTE_METADATA_REQUIRED_AUTHORITY = "required-authority";

    private GatewayAttributes() {}
}
