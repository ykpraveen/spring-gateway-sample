package com.example.gatewaysample.product.web;

import com.example.gatewaysample.product.domain.Product;
import com.example.gatewaysample.product.exception.ProductNotFoundException;
import com.example.gatewaysample.product.repository.ProductRepository;
import com.example.gatewaysample.product.web.dto.ProductRequest;
import com.example.gatewaysample.product.web.dto.ProductResponse;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/products")
@Tag(name = "Products")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping
    @Operation(summary = "List products")
    public PagedModel<ProductResponse> list(Pageable pageable) {
        Page<ProductResponse> page = productRepository.findAll(pageable).map(ProductResponse::from);
        return new PagedModel<>(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product by id")
    public ProductResponse get(@PathVariable Long id) {
        return ProductResponse.from(findOrThrow(id));
    }

    @PostMapping
    @Operation(summary = "Create a product")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        Product product = new Product(request.sku(), request.name(), request.description(), request.activeOrDefault());
        Product saved = productRepository.save(product);
        return ResponseEntity.created(URI.create("/internal/products/" + saved.getId()))
                .body(ProductResponse.from(saved));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a product")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        Product product = findOrThrow(id);
        product.setSku(request.sku());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setActive(request.activeOrDefault());
        return ProductResponse.from(productRepository.save(product));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a product")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Product product = findOrThrow(id);
        productRepository.delete(product);
        return ResponseEntity.noContent().build();
    }

    private Product findOrThrow(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }
}
