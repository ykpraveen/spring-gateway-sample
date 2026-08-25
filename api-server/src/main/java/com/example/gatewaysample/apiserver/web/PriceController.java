package com.example.gatewaysample.apiserver.web;

import com.example.gatewaysample.apiserver.downstream.GetResult;
import com.example.gatewaysample.apiserver.downstream.PricingServiceClient;
import com.example.gatewaysample.apiserver.web.dto.DegradedMeta;
import com.example.gatewaysample.apiserver.web.dto.DegradedResponse;
import com.example.gatewaysample.apiserver.web.dto.PriceRequest;
import com.example.gatewaysample.apiserver.web.dto.PriceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Public one-to-one delegation to pricing-service, matching the gateway's {@code /api/prices/**}
 * routes. {@code GET /api/prices/{productId}} maps to pricing-service's active-price-for-product
 * lookup (not a price record id) per PLAN.md's Public API table; {@code PUT}/{@code DELETE} use
 * the price record id.
 */
@RestController
@RequestMapping("/api/prices")
public class PriceController {

    private final PricingServiceClient client;

    public PriceController(PricingServiceClient client) {
        this.client = client;
    }

    @GetMapping
    public Mono<ResponseEntity<Object>> list(@RequestParam MultiValueMap<String, String> params) {
        return client.list(params).map(PriceController::toResponseEntity);
    }

    @GetMapping("/{productId}")
    public Mono<ResponseEntity<Object>> get(
            @PathVariable Long productId, @RequestParam(defaultValue = "normal") String mode) {
        return client.getActiveForProduct(productId, mode).map(PriceController::toResponseEntity);
    }

    @PostMapping
    public Mono<ResponseEntity<PriceResponse>> create(@Valid @RequestBody PriceRequest request) {
        return client.create(request).map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created));
    }

    @PutMapping("/{id}")
    public Mono<PriceResponse> update(@PathVariable Long id, @Valid @RequestBody PriceRequest request) {
        return client.update(id, request);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable Long id) {
        return client.delete(id).thenReturn(ResponseEntity.noContent().<Void>build());
    }

    private static <T> ResponseEntity<Object> toResponseEntity(GetResult<T> result) {
        return switch (result) {
            case GetResult.Live<T> live -> ResponseEntity.<Object>ok(live.value());
            case GetResult.Degraded<T> degraded -> ResponseEntity.<Object>ok(
                    new DegradedResponse<>(degraded.value(), DegradedMeta.cache(degraded.reason(), degraded.cachedAt())));
        };
    }
}
