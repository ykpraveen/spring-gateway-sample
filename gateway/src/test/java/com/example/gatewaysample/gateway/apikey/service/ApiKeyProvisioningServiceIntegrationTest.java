package com.example.gatewaysample.gateway.apikey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.gatewaysample.gateway.AbstractIntegrationTest;
import com.example.gatewaysample.gateway.apikey.domain.ApiClient;
import com.example.gatewaysample.gateway.apikey.dto.IssuedApiKey;
import com.example.gatewaysample.gateway.apikey.exception.ApiClientNotFoundException;
import com.example.gatewaysample.gateway.apikey.repository.ApiClientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiKeyProvisioningServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApiKeyProvisioningService provisioningService;

    @Autowired
    private ApiClientRepository apiClientRepository;

    @Autowired
    private ApiKeyHasher hasher;

    @Test
    void provisionPersistsAnActiveClientWithTheKeyDigestOnly() {
        IssuedApiKey issued = provisioningService.provision("acme", "standard").block();

        ApiClient stored = apiClientRepository.findById(issued.apiClientId()).block();

        assertThat(stored.name()).isEqualTo("acme");
        assertThat(stored.tier()).isEqualTo("standard");
        assertThat(stored.active()).isTrue();
        assertThat(stored.keyHash()).isEqualTo(hasher.digest(issued.rawKey()));
    }

    @Test
    void rotateReplacesTheKeyHashAndInvalidatesTheOldRawKey() {
        IssuedApiKey issued = provisioningService.provision("rotate-me", "standard").block();
        String oldRawKey = issued.rawKey();

        IssuedApiKey rotated = provisioningService.rotate(issued.apiClientId()).block();

        ApiClient stored = apiClientRepository.findById(issued.apiClientId()).block();
        assertThat(rotated.rawKey()).isNotEqualTo(oldRawKey);
        assertThat(stored.keyHash()).isEqualTo(hasher.digest(rotated.rawKey()));
        assertThat(stored.keyHash()).isNotEqualTo(hasher.digest(oldRawKey));
    }

    @Test
    void revokeDeactivatesTheClientWithoutDeletingIt() {
        IssuedApiKey issued = provisioningService.provision("revoke-me", "standard").block();

        provisioningService.revoke(issued.apiClientId()).block();

        ApiClient stored = apiClientRepository.findById(issued.apiClientId()).block();
        assertThat(stored.active()).isFalse();
    }

    @Test
    void rotatingAnUnknownClientFails() {
        assertThatThrownBy(() -> provisioningService.rotate(-1L).block())
                .isInstanceOf(ApiClientNotFoundException.class);
    }

    @Test
    void revokingAnUnknownClientFails() {
        assertThatThrownBy(() -> provisioningService.revoke(-1L).block())
                .isInstanceOf(ApiClientNotFoundException.class);
    }
}
