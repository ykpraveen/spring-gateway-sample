package com.example.gatewaysample.product.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** Signs HS256 test tokens accepted only by {@link TestSecurityConfig}'s decoder, never by Keycloak. */
public final class TestJwtSupport {

    static final String SECRET = "test-only-hmac-secret-for-product-service-do-not-reuse";

    private TestJwtSupport() {}

    public static String token(String subject, String... roles) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                    .claim("realm_access", Map.of("roles", List.of(roles)))
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), claims);
            jwt.sign(new MACSigner(SECRET));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign test JWT", e);
        }
    }
}
