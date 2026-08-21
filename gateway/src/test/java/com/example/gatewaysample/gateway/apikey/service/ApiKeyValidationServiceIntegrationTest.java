package com.example.gatewaysample.gateway.apikey.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.gatewaysample.gateway.AbstractIntegrationTest;
import com.example.gatewaysample.gateway.apikey.dto.ApiClientPrincipal;
import com.example.gatewaysample.gateway.apikey.dto.IssuedApiKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Uses a short cache TTL so the revocation-latency behavior can be observed without a long sleep. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "app.security.api-key.cache-ttl=2s")
class ApiKeyValidationServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApiKeyProvisioningService provisioningService;

    @Autowired
    private ApiKeyValidationService validationService;

    @Test
    void validatesAKnownActiveKey() {
        IssuedApiKey issued = provisioningService.provision("valid-client", "partner").block();

        ApiClientPrincipal principal = validationService.validate(issued.rawKey()).block();

        assertThat(principal.id()).isEqualTo(issued.apiClientId());
        assertThat(principal.name()).isEqualTo("valid-client");
        assertThat(principal.tier()).isEqualTo("partner");
    }

    @Test
    void rejectsAnUnknownKey() {
        ApiClientPrincipal principal = validationService.validate("ak_never-issued").block();

        assertThat(principal).isNull();
    }

    @Test
    void rejectsAKeyForAClientThatWasNeverCached() {
        IssuedApiKey issued = provisioningService.provision("pre-revoked-client", "standard").block();
        provisioningService.revoke(issued.apiClientId()).block();

        ApiClientPrincipal principal = validationService.validate(issued.rawKey()).block();

        assertThat(principal).isNull();
    }

    @Test
    void anOldKeyStopsValidatingAfterRotationWhenNeverCached() {
        IssuedApiKey issued = provisioningService.provision("rotated-client", "standard").block();
        provisioningService.rotate(issued.apiClientId()).block();

        ApiClientPrincipal principal = validationService.validate(issued.rawKey()).block();

        assertThat(principal).isNull();
    }

    @Test
    void aRevokedKeyStaysValidUntilTheCacheEntryExpires() throws InterruptedException {
        IssuedApiKey issued = provisioningService.provision("cached-then-revoked", "standard").block();
        // Populates the cache while the client is still active.
        assertThat(validationService.validate(issued.rawKey()).block()).isNotNull();

        provisioningService.revoke(issued.apiClientId()).block();

        // Served from cache: revocation has not propagated yet.
        assertThat(validationService.validate(issued.rawKey()).block()).isNotNull();

        Thread.sleep(2_500);

        // Cache entry expired: the database now rejects the revoked client.
        assertThat(validationService.validate(issued.rawKey()).block()).isNull();
    }
}
