package com.example.gatewaysample.gateway.apikey.service;

import com.example.gatewaysample.gateway.apikey.dto.ApiClientPrincipal;
import com.example.gatewaysample.gateway.apikey.repository.ApiClientRepository;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Validates a raw API key against the {@code api_client} table, caching the validated identity in
 * Redis by digest for {@code app.security.api-key.cache-ttl} so that repeated requests from the
 * same client skip the database. The cache TTL is also the maximum staleness window: a rotated or
 * revoked key may still resolve from cache until the entry expires.
 */
@Service
public class ApiKeyValidationService {

    private static final String CACHE_KEY_PREFIX = "apikey:";

    private final ApiClientRepository apiClientRepository;
    private final ApiKeyHasher hasher;
    private final ReactiveRedisTemplate<String, ApiClientPrincipal> redisTemplate;
    private final Duration cacheTtl;

    public ApiKeyValidationService(
            ApiClientRepository apiClientRepository,
            ApiKeyHasher hasher,
            ReactiveRedisTemplate<String, ApiClientPrincipal> redisTemplate,
            @Value("${app.security.api-key.cache-ttl:60s}") Duration cacheTtl) {
        this.apiClientRepository = apiClientRepository;
        this.hasher = hasher;
        this.redisTemplate = redisTemplate;
        this.cacheTtl = cacheTtl;
    }

    /** Returns the validated client identity, or an empty {@link Mono} if the key is unknown, revoked, or inactive. */
    public Mono<ApiClientPrincipal> validate(String rawKey) {
        String digest = hasher.digest(rawKey);
        String cacheKey = CACHE_KEY_PREFIX + digest;
        ReactiveValueOperations<String, ApiClientPrincipal> ops = redisTemplate.opsForValue();
        return ops.get(cacheKey).switchIfEmpty(Mono.defer(() -> loadAndCache(digest, cacheKey, ops)));
    }

    private Mono<ApiClientPrincipal> loadAndCache(
            String digest, String cacheKey, ReactiveValueOperations<String, ApiClientPrincipal> ops) {
        return apiClientRepository
                .findByKeyHashAndActiveTrue(digest)
                .map(client -> new ApiClientPrincipal(client.id(), client.name(), client.tier()))
                .flatMap(principal -> ops.set(cacheKey, principal, cacheTtl).thenReturn(principal));
    }
}
