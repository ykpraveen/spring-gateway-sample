package com.example.gatewaysample.apiserver.downstream;

import com.example.gatewaysample.apiserver.config.CacheProperties;
import com.example.gatewaysample.apiserver.web.dto.ProductRequest;
import com.example.gatewaysample.apiserver.web.dto.ProductResponse;
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
public class ProductServiceClient extends AbstractDownstreamClient {

    public ProductServiceClient(
            @Qualifier("productServiceWebClient") WebClient webClient,
            ReactiveCircuitBreakerFactory circuitBreakerFactory,
            CacheProperties cacheProperties) {
        super(webClient, circuitBreakerFactory, "productService", "product-service", cacheProperties);
    }

    @Override
    protected String serviceCode() {
        return "PRODUCT_SERVICE";
    }

    public Mono<GetResult<Map<String, Object>>> list(MultiValueMap<String, String> queryParams) {
        String uri = UriComponentsBuilder.fromPath("/internal/products")
                .queryParams(queryParams)
                .build()
                .toUriString();
        return getWithFallback("list:" + uri, exchangeGet(uri, new ParameterizedTypeReference<Map<String, Object>>() {}));
    }

    public Mono<GetResult<ProductResponse>> get(Long id, String mode) {
        String uri = UriComponentsBuilder.fromPath("/internal/products/{id}")
                .queryParam("mode", mode)
                .buildAndExpand(id)
                .toUriString();
        return getWithFallback("product:" + id, exchangeGet(uri, ProductResponse.class));
    }

    public Mono<ProductResponse> create(ProductRequest request) {
        return mutate(exchangePost("/internal/products", request, ProductResponse.class));
    }

    public Mono<ProductResponse> update(Long id, ProductRequest request) {
        return mutate(exchangePut("/internal/products/" + id, request, ProductResponse.class));
    }

    public Mono<Void> delete(Long id) {
        return mutate(exchangeDelete("/internal/products/" + id));
    }
}
