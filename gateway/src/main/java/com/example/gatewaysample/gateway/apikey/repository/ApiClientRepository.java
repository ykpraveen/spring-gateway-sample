package com.example.gatewaysample.gateway.apikey.repository;

import com.example.gatewaysample.gateway.apikey.domain.ApiClient;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface ApiClientRepository extends ReactiveCrudRepository<ApiClient, Long> {

    Mono<ApiClient> findByKeyHashAndActiveTrue(String keyHash);

    Mono<ApiClient> findByName(String name);
}
