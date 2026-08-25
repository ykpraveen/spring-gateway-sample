package com.example.gatewaysample.apiserver.downstream;

import java.time.Instant;

record CachedEntry(Object value, Instant cachedAt) {}
