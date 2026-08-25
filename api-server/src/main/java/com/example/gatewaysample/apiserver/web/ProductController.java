package com.example.gatewaysample.apiserver.web;

import com.example.gatewaysample.apiserver.downstream.GetResult;
import com.example.gatewaysample.apiserver.downstream.ProductServiceClient;
import com.example.gatewaysample.apiserver.web.dto.DegradedMeta;
import com.example.gatewaysample.apiserver.web.dto.DegradedResponse;
import com.example.gatewaysample.apiserver.web.dto.ProductRequest;
import com.example.gatewaysample.apiserver.web.dto.ProductResponse;
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

/** Public one-to-one delegation to product-service, matching the gateway's {@code /api/products/**} routes. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductServiceClient client;

    public ProductController(ProductServiceClient client) {
        this.client = client;
    }

    @GetMapping
    public Mono<ResponseEntity<Object>> list(@RequestParam MultiValueMap<String, String> params) {
        return client.list(params).map(ProductController::toResponseEntity);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Object>> get(@PathVariable Long id, @RequestParam(defaultValue = "normal") String mode) {
        return client.get(id, mode).map(ProductController::toResponseEntity);
    }

    @PostMapping
    public Mono<ResponseEntity<ProductResponse>> create(@Valid @RequestBody ProductRequest request) {
        return client.create(request).map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created));
    }

    @PutMapping("/{id}")
    public Mono<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
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
