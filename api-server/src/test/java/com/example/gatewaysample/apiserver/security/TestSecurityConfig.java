package com.example.gatewaysample.apiserver.security;

import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/** Replaces the JWKS-backed {@link ReactiveJwtDecoder} with one that trusts {@link TestJwtSupport}-signed tokens. */
@TestConfiguration
class TestSecurityConfig {

    @Bean
    @Primary
    ReactiveJwtDecoder testJwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(TestJwtSupport.SECRET.getBytes(), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
