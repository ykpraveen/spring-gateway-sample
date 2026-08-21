package com.example.gatewaysample.gateway.apikey.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Generates raw API keys and computes their HMAC-SHA256 digest with a server-side pepper. The
 * digest is deterministic so it can be looked up directly by a unique index, without ever
 * persisting or comparing the raw key.
 */
@Component
public class ApiKeyHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String RAW_KEY_PREFIX = "ak_";
    private static final int RAW_KEY_RANDOM_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKeySpec pepperKey;

    public ApiKeyHasher(@Value("${app.security.api-key.pepper:dev-only-api-key-pepper-change-me}") String pepper) {
        this.pepperKey = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    public String digest(String rawKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(pepperKey);
            return HexFormat.of().formatHex(mac.doFinal(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute API key digest", e);
        }
    }

    public String generateRawKey() {
        byte[] randomBytes = new byte[RAW_KEY_RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return RAW_KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
