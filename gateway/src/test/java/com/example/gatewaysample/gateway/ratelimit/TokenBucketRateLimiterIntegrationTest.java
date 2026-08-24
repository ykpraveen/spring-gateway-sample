package com.example.gatewaysample.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.gatewaysample.gateway.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TokenBucketRateLimiterIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TokenBucketRateLimiter rateLimiter;

    private static String uniqueKey() {
        return "test:" + UUID.randomUUID();
    }

    @Test
    void allowsUpToBurstCapacityThenRejects() {
        RateLimitProperties.Bucket bucket = new RateLimitProperties.Bucket(1, 3);
        String key = uniqueKey();

        assertThat(rateLimiter.tryConsume(key, bucket).block()).isTrue();
        assertThat(rateLimiter.tryConsume(key, bucket).block()).isTrue();
        assertThat(rateLimiter.tryConsume(key, bucket).block()).isTrue();
        assertThat(rateLimiter.tryConsume(key, bucket).block()).isFalse();
    }

    @Test
    void distinctKeysAreIndependentBuckets() {
        RateLimitProperties.Bucket bucket = new RateLimitProperties.Bucket(1, 1);
        String keyA = uniqueKey();
        String keyB = uniqueKey();

        assertThat(rateLimiter.tryConsume(keyA, bucket).block()).isTrue();
        assertThat(rateLimiter.tryConsume(keyA, bucket).block()).isFalse();

        // Exhausting keyA must not affect an independently-keyed bucket.
        assertThat(rateLimiter.tryConsume(keyB, bucket).block()).isTrue();
    }

    @Test
    void refundReturnsExactlyOneTokenCappedAtCapacity() {
        RateLimitProperties.Bucket bucket = new RateLimitProperties.Bucket(1, 1);
        String key = uniqueKey();

        assertThat(rateLimiter.tryConsume(key, bucket).block()).isTrue();
        assertThat(rateLimiter.tryConsume(key, bucket).block()).isFalse();

        rateLimiter.refund(key, bucket).block();
        assertThat(rateLimiter.tryConsume(key, bucket).block()).isTrue();

        // A second refund past capacity must not let two tokens be consumed afterwards.
        rateLimiter.refund(key, bucket).block();
        rateLimiter.refund(key, bucket).block();
        assertThat(rateLimiter.tryConsume(key, bucket).block()).isTrue();
        assertThat(rateLimiter.tryConsume(key, bucket).block()).isFalse();
    }
}
