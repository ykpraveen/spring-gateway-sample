package com.example.gatewaysample.pricing.web;

import com.example.gatewaysample.pricing.AbstractIntegrationTest;
import com.example.gatewaysample.pricing.security.TestJwtSupport;
import com.example.gatewaysample.pricing.security.TestSecurityConfig;
import com.example.gatewaysample.pricing.web.dto.PriceRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestSecurityConfig.class)
class PriceSecurityIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void rejectsRequestsWithoutAToken() {
        client.get().uri("/internal/prices").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void rejectsRequestsWithATokenMissingRequiredRoles() {
        String token = TestJwtSupport.token("no-roles");
        client.get()
                .uri("/internal/prices")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void allowsReadsForAPricingReadToken() {
        String token = TestJwtSupport.token("reader", "pricing.read");
        client.get()
                .uri("/internal/prices")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void rejectsWritesForAReadOnlyToken() {
        String token = TestJwtSupport.token("reader", "pricing.read");
        PriceRequest request = new PriceRequest(9001L, new BigDecimal("1.00"), "EUR");
        client.post()
                .uri("/internal/prices")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void allowsWritesForAPricingWriteToken() {
        String token = TestJwtSupport.token("writer", "pricing.read", "pricing.write");
        PriceRequest request = new PriceRequest(9002L, new BigDecimal("2.00"), "EUR");
        client.post()
                .uri("/internal/prices")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated();
    }
}
