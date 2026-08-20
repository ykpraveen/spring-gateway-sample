package com.example.gatewaysample.product.repository;

import com.example.gatewaysample.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
