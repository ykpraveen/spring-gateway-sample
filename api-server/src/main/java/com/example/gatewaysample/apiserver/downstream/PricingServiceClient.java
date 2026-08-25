package com.example.gatewaysample.apiserver.downstream;

import com.example.gatewaysample.apiserver.config.CacheProperties;
import com.example.gatewaysample.apiserver.web.dto.PriceRequest;
import com.example.gatewaysample.apiserver.web.dto.PriceResponse;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Component
public class PricingServiceClient extends AbstractDownstreamClient {

    public PricingServiceClient(
            @Qualifier("pricingServiceWebClient") WebClient webClient,
            ReactiveCircuitBreakerFactory circuitBreakerFactory,
            CacheProperties cacheProperties) {
        super(webClient, circuitBreakerFactory, "pricingService", "pricing-service", cacheProperties);
    }

    @Override
    protected String serviceCode() {
        return "PRICING_SERVICE";
    }

    public Mono<GetResult<Map<String, Object>>> list(MultiValueMap<String, String> queryParams) {
        String uri = UriComponentsBuilder.fromPath("/internal/prices")
                .queryParams(queryParams)
                .build()
                .toUriString();
        return getWithFallback("list:" + uri, exchangeGet(uri, new ParameterizedTypeReference<Map<String, Object>>() {}));
    }

    public Mono<GetResult<PriceResponse>> getActiveForProduct(Long productId, String mode) {
        String uri = UriComponentsBuilder.fromPath("/internal/prices/by-product/{productId}")
                .queryParam("mode", mode)
                .buildAndExpand(productId)
                .toUriString();
        return getWithFallback("price:by-product:" + productId, exchangeGet(uri, PriceResponse.class));
    }

    public Mono<PriceResponse> create(PriceRequest request) {
        return mutate(exchangePost("/internal/prices", request, PriceResponse.class));
    }

    public Mono<PriceResponse> update(Long id, PriceRequest request) {
        return mutate(exchangePut("/internal/prices/" + id, request, PriceResponse.class));
    }

    public Mono<Void> delete(Long id) {
        return mutate(exchangeDelete("/internal/prices/" + id));
    }
}
