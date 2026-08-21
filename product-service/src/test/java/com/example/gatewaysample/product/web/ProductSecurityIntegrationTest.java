package com.example.gatewaysample.product.web;

import com.example.gatewaysample.product.AbstractIntegrationTest;
import com.example.gatewaysample.product.security.TestJwtSupport;
import com.example.gatewaysample.product.security.TestSecurityConfig;
import com.example.gatewaysample.product.web.dto.ProductRequest;
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
class ProductSecurityIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void rejectsRequestsWithoutAToken() {
        client.get().uri("/internal/products").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void rejectsRequestsWithATokenMissingRequiredRoles() {
        String token = TestJwtSupport.token("no-roles");
        client.get()
                .uri("/internal/products")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void allowsReadsForACatalogReadToken() {
        String token = TestJwtSupport.token("reader", "catalog.read");
        client.get()
                .uri("/internal/products")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void rejectsWritesForAReadOnlyToken() {
        String token = TestJwtSupport.token("reader", "catalog.read");
        ProductRequest request = new ProductRequest("SKU-SEC-1", "Blocked", null, true);
        client.post()
                .uri("/internal/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void allowsWritesForACatalogWriteToken() {
        String token = TestJwtSupport.token("writer", "catalog.read", "catalog.write");
        ProductRequest request = new ProductRequest("SKU-SEC-2", "Allowed", null, true);
        client.post()
                .uri("/internal/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated();
    }
}
