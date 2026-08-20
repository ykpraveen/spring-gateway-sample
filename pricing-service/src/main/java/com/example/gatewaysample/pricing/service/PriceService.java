package com.example.gatewaysample.pricing.service;

import com.example.gatewaysample.pricing.domain.Price;
import com.example.gatewaysample.pricing.exception.PriceNotFoundException;
import com.example.gatewaysample.pricing.repository.PriceRepository;
import com.example.gatewaysample.pricing.web.dto.PriceRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceService {

    private final PriceRepository priceRepository;

    public PriceService(PriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }

    @Transactional
    public Price createPrice(PriceRequest request) {
        priceRepository.findByProductIdAndActiveTrue(request.productId())
                .ifPresent(current -> {
                    current.setActive(false);
                    // Flushed before the insert below: Hibernate otherwise groups all inserts
                    // ahead of updates within a flush, which would insert the new active row
                    // before this deactivation lands and violate ux_price_active_product.
                    priceRepository.saveAndFlush(current);
                });
        Price price = new Price(request.productId(), request.amount(), request.currency(), true);
        return priceRepository.save(price);
    }

    public Price findActiveForProduct(Long productId) {
        return priceRepository.findByProductIdAndActiveTrue(productId)
                .orElseThrow(() -> PriceNotFoundException.forProduct(productId));
    }
}
