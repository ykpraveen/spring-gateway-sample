package com.example.gatewaysample.gateway.ratelimit;

import java.util.List;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * A single Redis-backed token bucket, consumed and refunded atomically via Lua scripts so
 * concurrent requests against the same key can't race each other. The gateway's three-bucket
 * rate-limit policy composes three independently-keyed instances of this bucket rather than
 * checking all three atomically in one script — a deliberate simplicity-over-perfect-accuracy
 * trade-off for a demo system, accepting a small bounded over-consumption window.
 */
@Component
public class TokenBucketRateLimiter {

    /**
     * Refills the bucket proportionally to elapsed time since its last write, then consumes one
     * token if available. KEYS[1] = bucket key. ARGV[1] = replenish rate (tokens/sec). ARGV[2] =
     * burst capacity. ARGV[3] = now (epoch millis). Returns 1 if a token was consumed, else 0.
     */
    private static final String CONSUME_SCRIPT =
            """
            local key = KEYS[1]
            local rate = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])

            local bucket = redis.call('HMGET', key, 'tokens', 'timestamp')
            local tokens = tonumber(bucket[1])
            local timestamp = tonumber(bucket[2])
            if tokens == nil then tokens = capacity end
            if timestamp == nil then timestamp = now end

            local elapsedMillis = math.max(0, now - timestamp)
            local filled = math.min(capacity, tokens + (elapsedMillis * rate / 1000.0))

            local allowed = 0
            if filled >= 1 then
              allowed = 1
              filled = filled - 1
            end

            redis.call('HSET', key, 'tokens', filled, 'timestamp', now)
            redis.call('EXPIRE', key, math.max(1, math.ceil(capacity / rate) + 1))

            return allowed
            """;

    /**
     * Refunds one token to a bucket without touching its refill timestamp, capped at the bucket's
     * capacity. KEYS[1] = bucket key. ARGV[1] = burst capacity.
     */
    private static final String REFUND_SCRIPT =
            """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])

            local tokens = tonumber(redis.call('HGET', key, 'tokens'))
            if tokens == nil then
              tokens = capacity
            else
              tokens = math.min(capacity, tokens + 1)
            end

            redis.call('HSET', key, 'tokens', tokens)
            return tokens
            """;

    private static final RedisScript<Long> CONSUME = RedisScript.of(CONSUME_SCRIPT, Long.class);
    private static final RedisScript<Long> REFUND = RedisScript.of(REFUND_SCRIPT, Long.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    public TokenBucketRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Attempts to consume one token from the bucket at {@code key}, refilling it first. */
    public Mono<Boolean> tryConsume(String key, RateLimitProperties.Bucket bucket) {
        List<String> args =
                List.of(String.valueOf(bucket.replenishRate()), String.valueOf(bucket.burstCapacity()), String.valueOf(System.currentTimeMillis()));
        return redisTemplate.execute(CONSUME, List.of(key), args).next().map(result -> result == 1L);
    }

    /** Returns one previously-consumed token to the bucket at {@code key}, capped at its capacity. */
    public Mono<Void> refund(String key, RateLimitProperties.Bucket bucket) {
        return redisTemplate
                .execute(REFUND, List.of(key), List.of(String.valueOf(bucket.burstCapacity())))
                .next()
                .then();
    }
}
