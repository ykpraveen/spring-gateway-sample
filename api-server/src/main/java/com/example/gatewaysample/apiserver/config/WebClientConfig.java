package com.example.gatewaysample.apiserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring Boot 4.1 no longer auto-configures a {@code WebClient.Builder} bean (WebClient's
 * autoconfiguration support was dropped in favor of {@code RestClient} for blocking use cases), so
 * each downstream client is built directly from {@link WebClient#builder()}.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient productServiceWebClient(DownstreamProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.productService().baseUrl())
                .filter(forwardBearerToken())
                .build();
    }

    @Bean
    public WebClient pricingServiceWebClient(DownstreamProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.pricingService().baseUrl())
                .filter(forwardBearerToken())
                .build();
    }

    /**
     * product-service/pricing-service validate the JWT themselves as defense in depth (see
     * PLAN.md "Authentication and Authorization"), so the inbound bearer token must be forwarded
     * on every downstream call or they reject it as unauthenticated. Reads the current
     * authentication from the Reactor context that Spring Security's WebFlux filter chain already
     * populates, rather than threading the token through every controller/client method.
     */
    private ExchangeFilterFunction forwardBearerToken() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(JwtAuthenticationToken.class::cast)
                .map(auth -> ClientRequest.from(request)
                        .headers(headers -> headers.setBearerAuth(auth.getToken().getTokenValue()))
                        .build())
                .defaultIfEmpty(request));
    }
}
