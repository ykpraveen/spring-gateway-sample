package com.example.gatewaysample.pricing.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.gatewaysample.pricing.AbstractIntegrationTest;
import com.example.gatewaysample.pricing.domain.Price;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PriceRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PriceRepository priceRepository;

    @Test
    void databaseRejectsTwoActivePricesForTheSameProduct() {
        Long productId = 9001L;
        priceRepository.save(new Price(productId, new BigDecimal("10.00"), "EUR", true));

        assertThatThrownBy(() -> priceRepository.saveAndFlush(new Price(productId, new BigDecimal("20.00"), "EUR", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
