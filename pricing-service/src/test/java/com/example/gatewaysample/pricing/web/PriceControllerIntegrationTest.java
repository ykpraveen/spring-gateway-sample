package com.example.gatewaysample.pricing.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.gatewaysample.pricing.AbstractIntegrationTest;
import com.example.gatewaysample.pricing.web.dto.PriceRequest;
import com.example.gatewaysample.pricing.web.dto.PriceResponse;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class PriceControllerIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void createsAndRetrievesAPrice() {
        PriceRequest request = new PriceRequest(5001L, new BigDecimal("19.99"), "EUR");

        PriceResponse created = client.post()
                .uri("/internal/prices")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PriceResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.productId()).isEqualTo(5001L);
        assertThat(created.active()).isTrue();

        client.get()
                .uri("/internal/prices/by-product/5001")
                .exchange()
                .expectStatus().isOk()
                .expectBody(PriceResponse.class)
                .value(body -> assertThat(body.id()).isEqualTo(created.id()));
    }

    @Test
    void supersedesThePreviousActivePriceForTheSameProduct() {
        Long productId = 5002L;
        PriceResponse first = client.post()
                .uri("/internal/prices")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PriceRequest(productId, new BigDecimal("10.00"), "EUR"))
                .exchange()
                .expectBody(PriceResponse.class)
                .returnResult()
                .getResponseBody();

        PriceResponse second = client.post()
                .uri("/internal/prices")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PriceRequest(productId, new BigDecimal("12.50"), "EUR"))
                .exchange()
                .expectBody(PriceResponse.class)
                .returnResult()
                .getResponseBody();

        client.get()
                .uri("/internal/prices/" + first.id())
                .exchange()
                .expectBody(PriceResponse.class)
                .value(body -> assertThat(body.active()).isFalse());

        client.get()
                .uri("/internal/prices/by-product/" + productId)
                .exchange()
                .expectBody(PriceResponse.class)
                .value(body -> {
                    assertThat(body.id()).isEqualTo(second.id());
                    assertThat(body.amount()).isEqualByComparingTo("12.50");
                });
    }

    @Test
    void returnsNotFoundWhenNoActivePriceExistsForProduct() {
        client.get()
                .uri("/internal/prices/by-product/999999999")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> assertThat(body).containsEntry("code", "PRICE_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidRequestBody() {
        PriceRequest invalid = new PriceRequest(5003L, new BigDecimal("-1.00"), "EUR");

        client.post()
                .uri("/internal/prices")
                .contentType(MediaType.APPLICATION_JSON)
                .body(invalid)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> assertThat(body).containsEntry("code", "VALIDATION_ERROR"));
    }

    @Test
    void listsPricesFilteredByProduct() {
        Long productId = 5004L;
        client.post()
                .uri("/internal/prices")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PriceRequest(productId, new BigDecimal("5.00"), "EUR"))
                .exchange();

        client.get()
                .uri("/internal/prices?productId=" + productId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> assertThat(body).containsKey("content"));
    }

    @Test
    void updatesAndDeletesAPriceRecord() {
        Long productId = 5005L;
        PriceResponse created = client.post()
                .uri("/internal/prices")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PriceRequest(productId, new BigDecimal("7.00"), "EUR"))
                .exchange()
                .expectBody(PriceResponse.class)
                .returnResult()
                .getResponseBody();

        client.put()
                .uri("/internal/prices/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PriceRequest(productId, new BigDecimal("8.25"), "USD"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(PriceResponse.class)
                .value(body -> {
                    assertThat(body.amount()).isEqualByComparingTo("8.25");
                    assertThat(body.currency()).isEqualTo("USD");
                });

        client.delete()
                .uri("/internal/prices/" + created.id())
                .exchange()
                .expectStatus().isNoContent();

        client.get()
                .uri("/internal/prices/by-product/" + productId)
                .exchange()
                .expectStatus().isNotFound();
    }
}
