package com.example.gatewaysample.apiserver.security;

import com.example.gatewaysample.apiserver.testsupport.TestJwtSupport;
import com.example.gatewaysample.apiserver.testsupport.TestSecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestSecurityConfig.class)
class SecurityConfigIntegrationTest {

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void actuatorHealthIsPubliclyReachable() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void rejectsRequestsWithoutAToken() {
        client.get().uri("/api/products").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void authenticatesRequestsWithAValidToken() {
        // No product-service is running in this security-only test context, so the authenticated
        // request reaches ProductController, WebClient fails to connect, and — with no cached
        // value to fall back to — api-server reports 503 rather than rejecting the credential.
        String token = TestJwtSupport.token("test-user", "catalog.read");
        client.get()
                .uri("/api/products")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isEqualTo(503);
    }
}
