package com.example.gatewaysample.gateway.security;

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
        client.get().uri("/some-route").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void authenticatesRequestsWithAValidToken() {
        String token = TestJwtSupport.token("test-user", "catalog.read");
        client.get()
                .uri("/some-route")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isNotFound();
    }
}
