package com.example.gatewaysample.apiserver.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.example.gatewaysample.apiserver.testsupport.TestJwtSupport;
import com.example.gatewaysample.apiserver.testsupport.TestSecurityConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.Map;
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
 * Verifies api-server's Phase 6 delegation to product-service: live passthrough, downstream 4xx
 * passthrough, Caffeine-cached degraded fallback once the circuit opens, and 503 for a failed
 * mutation. Uses WireMock rather than Testcontainers, since api-server has no database of its
 * own and only needs an HTTP stub for product-service.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestSecurityConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProductControllerIntegrationTest {

    private static final WireMockServer PRODUCT_SERVICE = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        PRODUCT_SERVICE.start();
    }

    @DynamicPropertySource
    static void downstreamProperties(DynamicPropertyRegistry registry) {
        registry.add("app.downstream.product-service.base-url", PRODUCT_SERVICE::baseUrl);
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
        PRODUCT_SERVICE.resetAll();
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void liveGetIsPassedThroughUnwrapped() {
        PRODUCT_SERVICE.stubFor(get(urlPathEqualTo("/internal/products/1"))
                .willReturn(okJson(
                        """
                        {"id":1,"sku":"SKU-1","name":"Desk Lamp","description":"d","active":true,\
                        "createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}""")));

        client.get()
                .uri("/api/products/1")
                .header("Authorization", "Bearer " + TestJwtSupport.token("test-user", "catalog.read"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(1)
                .jsonPath("$.sku")
                .isEqualTo("SKU-1")
                .jsonPath("$.data")
                .doesNotExist();
    }

    @Test
    void downstreamNotFoundIsPassedThroughAsIs() {
        PRODUCT_SERVICE.stubFor(get(urlPathEqualTo("/internal/products/99"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody(
                                """
                                {"type":"about:blank","title":"Not Found","status":404,\
                                "detail":"Product 99 was not found","code":"PRODUCT_NOT_FOUND"}""")));

        client.get()
                .uri("/api/products/99")
                .header("Authorization", "Bearer " + TestJwtSupport.token("test-user", "catalog.read"))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    void cachedValueIsServedDegradedOnceTheCircuitOpens() {
        String token = TestJwtSupport.token("test-user", "catalog.read");
        PRODUCT_SERVICE.stubFor(get(urlPathEqualTo("/internal/products/2"))
                .willReturn(okJson(
                        """
                        {"id":2,"sku":"SKU-2","name":"Desk","description":"d","active":true,\
                        "createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}""")));

        // Prime the Caffeine cache with a live response.
        client.get().uri("/api/products/2").header("Authorization", "Bearer " + token).exchange().expectStatus().isOk();

        // Flip downstream to failing and drive enough calls to open the breaker (test config:
        // sliding-window=4, minimum-calls=2, failure-rate-threshold=50%).
        PRODUCT_SERVICE.stubFor(
                get(urlPathEqualTo("/internal/products/2")).willReturn(aResponse().withStatus(500)));
        for (int i = 0; i < 3; i++) {
            client.get().uri("/api/products/2").header("Authorization", "Bearer " + token).exchange();
        }

        client.get()
                .uri("/api/products/2")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.id")
                .isEqualTo(2)
                .jsonPath("$.meta.degraded")
                .isEqualTo(true)
                .jsonPath("$.meta.source")
                .isEqualTo("cache")
                .jsonPath("$.meta.reason")
                .isEqualTo("PRODUCT_SERVICE_CIRCUIT_OPEN");
    }

    @Test
    void mutationReturns503WhenDownstreamIsUnavailable() {
        PRODUCT_SERVICE.stubFor(post(urlPathEqualTo("/internal/products")).willReturn(aResponse().withStatus(500)));

        client.post()
                .uri("/api/products")
                .header("Authorization", "Bearer " + TestJwtSupport.token("test-user", "catalog.write"))
                .bodyValue(Map.of("sku", "SKU-X", "name", "X"))
                .exchange()
                .expectStatus()
                .isEqualTo(503)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("PRODUCT_SERVICE_UNAVAILABLE");
    }
}
