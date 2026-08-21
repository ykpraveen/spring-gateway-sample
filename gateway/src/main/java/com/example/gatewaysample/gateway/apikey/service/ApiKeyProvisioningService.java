package com.example.gatewaysample.gateway.apikey.service;

import com.example.gatewaysample.gateway.apikey.domain.ApiClient;
import com.example.gatewaysample.gateway.apikey.dto.IssuedApiKey;
import com.example.gatewaysample.gateway.apikey.exception.ApiClientNotFoundException;
import com.example.gatewaysample.gateway.apikey.repository.ApiClientRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ApiKeyProvisioningService {

    private final ApiClientRepository apiClientRepository;
    private final ApiKeyHasher hasher;

    public ApiKeyProvisioningService(ApiClientRepository apiClientRepository, ApiKeyHasher hasher) {
        this.apiClientRepository = apiClientRepository;
        this.hasher = hasher;
    }

    /** Registers a new API client and issues its first key. */
    public Mono<IssuedApiKey> provision(String name, String tier) {
        String rawKey = hasher.generateRawKey();
        Instant now = Instant.now();
        ApiClient client = new ApiClient(null, name, hasher.digest(rawKey), tier, true, now, now);
        return apiClientRepository.save(client).map(saved -> new IssuedApiKey(saved.id(), rawKey));
    }

    /**
     * Replaces a client's key with a newly generated one. The old key stops matching this row
     * immediately, but any Redis cache entry already validated against it remains valid until its
     * TTL expires — the same bounded revocation latency documented for {@link #revoke}.
     */
    public Mono<IssuedApiKey> rotate(Long apiClientId) {
        String rawKey = hasher.generateRawKey();
        return apiClientRepository
                .findById(apiClientId)
                .switchIfEmpty(Mono.error(new ApiClientNotFoundException(apiClientId)))
                .map(existing -> new ApiClient(
                        existing.id(),
                        existing.name(),
                        hasher.digest(rawKey),
                        existing.tier(),
                        existing.active(),
                        existing.createdAt(),
                        Instant.now()))
                .flatMap(apiClientRepository::save)
                .map(saved -> new IssuedApiKey(saved.id(), rawKey));
    }

    /**
     * Deactivates a client's key. Already-cached validations remain valid until the Redis cache
     * TTL expires, which is the maximum revocation latency.
     */
    public Mono<Void> revoke(Long apiClientId) {
        return apiClientRepository
                .findById(apiClientId)
                .switchIfEmpty(Mono.error(new ApiClientNotFoundException(apiClientId)))
                .map(existing -> new ApiClient(
                        existing.id(),
                        existing.name(),
                        existing.keyHash(),
                        existing.tier(),
                        false,
                        existing.createdAt(),
                        Instant.now()))
                .flatMap(apiClientRepository::save)
                .then();
    }
}
