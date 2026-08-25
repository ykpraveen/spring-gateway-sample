package com.example.gatewaysample.apiserver.downstream;

import java.time.Instant;

/** Outcome of a cached/circuit-broken GET: either a live downstream value or a degraded cached one. */
public sealed interface GetResult<T> permits GetResult.Live, GetResult.Degraded {

    record Live<T>(T value) implements GetResult<T> {}

    record Degraded<T>(T value, String reason, Instant cachedAt) implements GetResult<T> {}
}
