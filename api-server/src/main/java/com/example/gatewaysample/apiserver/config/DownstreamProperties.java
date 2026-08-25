package com.example.gatewaysample.apiserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.downstream")
public record DownstreamProperties(Service productService, Service pricingService) {

    public record Service(String baseUrl) {}
}
