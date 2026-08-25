package com.example.gatewaysample.apiserver.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.example.gatewaysample.apiserver.testsupport.TestJwtSupport;
import com.example.gatewaysample.apiserver.testsupport.TestSecurityConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Mirrors {@link ProductControllerIntegrationTest} for pricing-service delegation, confirming
 * {@code GET /api/prices/{productId}} maps to the by-product active-price lookup and that the
 * same cache/circuit-breaker fallback mechanics apply.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestSecurityConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PriceControllerIntegrationTest {

    private static final WireMockServer PRICING_SERVICE = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        PRICING_SERVICE.start();
    }

    @DynamicPropertySource
    static void downstreamProperties(DynamicPropertyRegistry registry) {
        registry.add("app.downstream.pricing-service.base-url", PRICING_SERVICE::baseUrl);
        // Tighter than production so the breaker opens deterministically in a handful of calls
        // instead of the production sliding window of 10.
        registry.add("app.circuit-breaker.sliding-window-size", () -> 4);
        registry.add("app.circuit-breaker.minimum-number-of-calls", () -> 2);
        registry.add("app.circuit-breaker.wait-duration-in-open-state", () -> "2s");
        registry.add("app.circuit-breaker.permitted-calls-in-half-open-state", () -> 2);
    }

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        PRICING_SERVICE.resetAll();
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void liveGetIsPassedThroughUnwrapped() {
        PRICING_SERVICE.stubFor(get(urlPathEqualTo("/internal/prices/by-product/1"))
                .willReturn(okJson(
                        """
                        {"id":10,"productId":1,"amount":9.99,"currency":"EUR","active":true,\
                        "createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}""")));

        client.get()
                .uri("/api/prices/1")
                .header("Authorization", "Bearer " + TestJwtSupport.token("test-user", "pricing.read"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.productId")
                .isEqualTo(1)
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void cachedValueIsServedDegradedOnceTheCircuitOpens() {
        String token = TestJwtSupport.token("test-user", "pricing.read");
        PRICING_SERVICE.stubFor(get(urlPathEqualTo("/internal/prices/by-product/2"))
                .willReturn(okJson(
                        """
                        {"id":20,"productId":2,"amount":19.99,"currency":"EUR","active":true,\
                        "createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}""")));

        client.get().uri("/api/prices/2").header("Authorization", "Bearer " + token).exchange().expectStatus().isOk();

        PRICING_SERVICE.stubFor(get(urlPathEqualTo("/internal/prices/by-product/2"))
                .willReturn(aResponse().withStatus(500)));
        for (int i = 0; i < 3; i++) {
            client.get().uri("/api/prices/2").header("Authorization", "Bearer " + token).exchange();
        }

        client.get()
                .uri("/api/prices/2")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.productId")
                .isEqualTo(2)
                .jsonPath("$.meta.degraded")
                .isEqualTo(true)
                .jsonPath("$.meta.reason")
                .isEqualTo("PRICING_SERVICE_CIRCUIT_OPEN");
    }
}
