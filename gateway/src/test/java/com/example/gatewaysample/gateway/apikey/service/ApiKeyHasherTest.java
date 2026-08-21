package com.example.gatewaysample.gateway.apikey.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiKeyHasherTest {

    private final ApiKeyHasher hasher = new ApiKeyHasher("test-only-pepper-do-not-reuse");

    @Test
    void digestIsDeterministicForTheSameRawKey() {
        String rawKey = hasher.generateRawKey();

        assertThat(hasher.digest(rawKey)).isEqualTo(hasher.digest(rawKey));
    }

    @Test
    void digestIsSixtyFourHexCharacters() {
        String digest = hasher.digest(hasher.generateRawKey());

        assertThat(digest).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void digestDependsOnThePepper() {
        String rawKey = hasher.generateRawKey();
        ApiKeyHasher otherPepperHasher = new ApiKeyHasher("a-different-pepper");

        assertThat(hasher.digest(rawKey)).isNotEqualTo(otherPepperHasher.digest(rawKey));
    }

    @Test
    void generatedRawKeysAreUniqueAndPrefixed() {
        String first = hasher.generateRawKey();
        String second = hasher.generateRawKey();

        assertThat(first).isNotEqualTo(second);
        assertThat(first).startsWith("ak_");
        assertThat(second).startsWith("ak_");
    }
}
