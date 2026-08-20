package com.example.gatewaysample.pricing.web;

import com.example.gatewaysample.pricing.domain.Price;
import com.example.gatewaysample.pricing.exception.PriceNotFoundException;
import com.example.gatewaysample.pricing.repository.PriceRepository;
import com.example.gatewaysample.pricing.service.PriceService;
import com.example.gatewaysample.pricing.web.dto.PriceRequest;
import com.example.gatewaysample.pricing.web.dto.PriceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/prices")
@Tag(name = "Prices")
public class PriceController {

    private final PriceRepository priceRepository;
    private final PriceService priceService;

    public PriceController(PriceRepository priceRepository, PriceService priceService) {
        this.priceRepository = priceRepository;
        this.priceService = priceService;
    }

    @GetMapping
    @Operation(summary = "List prices, optionally filtered by product")
    public PagedModel<PriceResponse> list(
            @RequestParam(required = false) Long productId, Pageable pageable) {
        Page<Price> page = productId == null
                ? priceRepository.findAll(pageable)
                : priceRepository.findByProductId(productId, pageable);
        return new PagedModel<>(page.map(PriceResponse::from));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a price by id")
    public PriceResponse get(@PathVariable Long id) {
        return PriceResponse.from(findOrThrow(id));
    }

    @GetMapping("/by-product/{productId}")
    @Operation(summary = "Get the current active price for a product")
    public PriceResponse getActiveForProduct(@PathVariable Long productId) {
        return PriceResponse.from(priceService.findActiveForProduct(productId));
    }

    @PostMapping
    @Operation(summary = "Create a new active price for a product, superseding any existing one")
    public ResponseEntity<PriceResponse> create(@Valid @RequestBody PriceRequest request) {
        Price saved = priceService.createPrice(request);
        return ResponseEntity.created(URI.create("/internal/prices/" + saved.getId()))
                .body(PriceResponse.from(saved));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a price record's amount and currency")
    public PriceResponse update(@PathVariable Long id, @Valid @RequestBody PriceRequest request) {
        Price price = findOrThrow(id);
        price.setAmount(request.amount());
        price.setCurrency(request.currency());
        return PriceResponse.from(priceRepository.save(price));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a price record")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Price price = findOrThrow(id);
        priceRepository.delete(price);
        return ResponseEntity.noContent().build();
    }

    private Price findOrThrow(Long id) {
        return priceRepository.findById(id).orElseThrow(() -> PriceNotFoundException.forId(id));
    }
}
