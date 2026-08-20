package com.example.gatewaysample.pricing.repository;

import com.example.gatewaysample.pricing.domain.Price;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceRepository extends JpaRepository<Price, Long> {

    Optional<Price> findByProductIdAndActiveTrue(Long productId);

    Page<Price> findByProductId(Long productId, Pageable pageable);
}
