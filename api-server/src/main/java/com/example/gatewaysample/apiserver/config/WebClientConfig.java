package com.example.gatewaysample.apiserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring Boot 4.1 no longer auto-configures a {@code WebClient.Builder} bean (WebClient's
 * autoconfiguration support was dropped in favor of {@code RestClient} for blocking use cases), so
 * each downstream client is built directly from {@link WebClient#builder()}.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient productServiceWebClient(DownstreamProperties properties) {
        return WebClient.builder().baseUrl(properties.productService().baseUrl()).build();
    }

    @Bean
    public WebClient pricingServiceWebClient(DownstreamProperties properties) {
        return WebClient.builder().baseUrl(properties.pricingService().baseUrl()).build();
    }
}
