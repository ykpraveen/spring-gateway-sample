package com.example.gatewaysample.product.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.gatewaysample.product.AbstractIntegrationTest;
import com.example.gatewaysample.product.security.TestJwtSupport;
import com.example.gatewaysample.product.security.TestSecurityConfig;
import com.example.gatewaysample.product.web.dto.ProductRequest;
import com.example.gatewaysample.product.web.dto.ProductResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestSecurityConfig.class)
class ProductControllerIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        String token = TestJwtSupport.token("test-user", "catalog.read", "catalog.write");
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }

    @Test
    void createsAndRetrievesAProduct() {
        ProductRequest request = new ProductRequest("SKU-CREATE-1", "Test Product", "A product", true);

        ProductResponse created = client.post()
                .uri("/internal/products")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists("Location")
                .expectBody(ProductResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.sku()).isEqualTo("SKU-CREATE-1");
        assertThat(created.id()).isNotNull();

        client.get()
                .uri("/internal/products/" + created.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductResponse.class)
                .value(body -> assertThat(body.name()).isEqualTo("Test Product"));
    }

    @Test
    void rejectsDuplicateSku() {
        ProductRequest request = new ProductRequest("SKU-DUP-1", "First", null, true);
        client.post().uri("/internal/products").contentType(MediaType.APPLICATION_JSON).body(request).exchange();

        client.post()
                .uri("/internal/products")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> assertThat(body).containsEntry("code", "DUPLICATE_SKU"));
    }

    @Test
    void returnsNotFoundForMissingProduct() {
        client.get()
                .uri("/internal/products/999999999")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> assertThat(body).containsEntry("code", "PRODUCT_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidRequestBody() {
        ProductRequest invalid = new ProductRequest("SKU-INVALID", "", null, true);

        client.post()
                .uri("/internal/products")
                .contentType(MediaType.APPLICATION_JSON)
                .body(invalid)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> assertThat(body).containsEntry("code", "VALIDATION_ERROR"));
    }

    @Test
    void listsProductsWithPagination() {
        client.post()
                .uri("/internal/products")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ProductRequest("SKU-LIST-1", "Listed Product", null, true))
                .exchange();

        client.get()
                .uri("/internal/products?page=0&size=5")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {})
                .value(body -> {
                    assertThat(body).containsKey("content");
                    assertThat(body).containsKey("page");
                });
    }

    @Test
    void updatesAndDeletesAProduct() {
        ProductResponse created = client.post()
                .uri("/internal/products")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ProductRequest("SKU-UPDATE-1", "Original", null, true))
                .exchange()
                .expectBody(ProductResponse.class)
                .returnResult()
                .getResponseBody();

        ProductRequest updateRequest = new ProductRequest("SKU-UPDATE-1", "Renamed", "Updated description", false);
        client.put()
                .uri("/internal/products/" + created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(updateRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductResponse.class)
                .value(body -> {
                    assertThat(body.name()).isEqualTo("Renamed");
                    assertThat(body.active()).isFalse();
                });

        client.delete()
                .uri("/internal/products/" + created.id())
                .exchange()
                .expectStatus().isNoContent();

        client.get()
                .uri("/internal/products/" + created.id())
                .exchange()
                .expectStatus().isNotFound();
    }
}
