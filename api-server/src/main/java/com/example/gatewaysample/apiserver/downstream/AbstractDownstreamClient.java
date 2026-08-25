package com.example.gatewaysample.apiserver.downstream;

import com.example.gatewaysample.apiserver.config.CacheProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Shared WebClient + Caffeine + circuit-breaker mechanics for a single downstream dependency.
 *
 * <p>GETs go through {@link #getWithFallback}: a successful call is cached and returned as {@link
 * GetResult.Live}; if the circuit is open or the call fails, the last cached value (if any) is
 * returned as {@link GetResult.Degraded}, otherwise the failure surfaces as {@link
 * DownstreamUnavailableException}. Mutations go through {@link #mutate}: never cached, never
 * degraded — an unavailable dependency always surfaces as {@link DownstreamUnavailableException}.
 *
 * <p>In both cases, a {@link DownstreamClientErrorException} (a downstream 4xx) bypasses this
 * logic entirely and is rethrown as-is: a business rejection is not an infrastructure failure and
 * must not open the circuit, get cached, or get papered over with stale data.
 */
public abstract class AbstractDownstreamClient {

    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory circuitBreakerFactory;
    private final String circuitBreakerName;
    private final String serviceName;
    private final Cache<String, CachedEntry> cache;

    protected AbstractDownstreamClient(
            WebClient webClient,
            ReactiveCircuitBreakerFactory circuitBreakerFactory,
            String circuitBreakerName,
            String serviceName,
            CacheProperties cacheProperties) {
        this.webClient = webClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.circuitBreakerName = circuitBreakerName;
        this.serviceName = serviceName;
        this.cache = Caffeine.newBuilder()
                .maximumSize(cacheProperties.maxSize())
                .expireAfterWrite(cacheProperties.ttl())
                .build();
    }

    /** Short, upper-snake-case identifier used to build {@code *_CIRCUIT_OPEN} / {@code *_CALL_FAILED} reason codes. */
    protected abstract String serviceCode();

    protected <T> Mono<T> exchangeGet(String uri, Class<T> type) {
        return webClient.get().uri(uri).retrieve().onStatus(HttpStatusCode::is4xxClientError, this::toClientError)
                .bodyToMono(type);
    }

    protected <T> Mono<T> exchangeGet(String uri, ParameterizedTypeReference<T> type) {
        return webClient.get().uri(uri).retrieve().onStatus(HttpStatusCode::is4xxClientError, this::toClientError)
                .bodyToMono(type);
    }

    protected <T> Mono<T> exchangePost(String uri, Object body, Class<T> type) {
        return webClient
                .post()
                .uri(uri)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::toClientError)
                .bodyToMono(type);
    }

    protected <T> Mono<T> exchangePut(String uri, Object body, Class<T> type) {
        return webClient
                .put()
                .uri(uri)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::toClientError)
                .bodyToMono(type);
    }

    protected Mono<Void> exchangeDelete(String uri) {
        return webClient
                .delete()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::toClientError)
                .toBodilessEntity()
                .then();
    }

    private Mono<Throwable> toClientError(ClientResponse response) {
        return response
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .defaultIfEmpty(Map.of())
                .map(body -> new DownstreamClientErrorException(response.statusCode(), body));
    }

    protected <T> Mono<GetResult<T>> getWithFallback(String cacheKey, Mono<T> call) {
        Mono<T> retried = call.retryWhen(Retry.max(1).filter(WebClientRequestException.class::isInstance));
        Mono<GetResult<T>> primary = retried
                .doOnNext(value -> cache.put(cacheKey, new CachedEntry(value, Instant.now())))
                .map(value -> (GetResult<T>) new GetResult.Live<>(value));
        ReactiveCircuitBreaker circuitBreaker = circuitBreakerFactory.create(circuitBreakerName);
        return circuitBreaker.run(primary, ex -> fallback(cacheKey, ex));
    }

    @SuppressWarnings("unchecked")
    private <T> Mono<GetResult<T>> fallback(String cacheKey, Throwable ex) {
        if (ex instanceof DownstreamClientErrorException clientError) {
            return Mono.error(clientError);
        }
        CachedEntry cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            String reason = ex instanceof CallNotPermittedException
                    ? serviceCode() + "_CIRCUIT_OPEN"
                    : serviceCode() + "_CALL_FAILED";
            return Mono.just(new GetResult.Degraded<>((T) cached.value(), reason, cached.cachedAt()));
        }
        return Mono.error(new DownstreamUnavailableException(serviceName, ex));
    }

    protected <T> Mono<T> mutate(Mono<T> call) {
        ReactiveCircuitBreaker circuitBreaker = circuitBreakerFactory.create(circuitBreakerName);
        Function<Throwable, Mono<T>> fallback = this::mutationFallback;
        return circuitBreaker.run(call, fallback);
    }

    private <T> Mono<T> mutationFallback(Throwable ex) {
        if (ex instanceof DownstreamClientErrorException clientError) {
            return Mono.error(clientError);
        }
        return Mono.error(new DownstreamUnavailableException(serviceName, ex));
    }
}
