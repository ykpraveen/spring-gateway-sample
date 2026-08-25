package com.example.gatewaysample.apiserver.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(int maxSize, Duration ttl) {}
