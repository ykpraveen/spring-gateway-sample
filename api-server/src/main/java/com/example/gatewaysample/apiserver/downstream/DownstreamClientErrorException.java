package com.example.gatewaysample.apiserver.downstream;

import java.util.Map;
import org.springframework.http.HttpStatusCode;

/**
 * Wraps a downstream 4xx response so it can be proxied back to the caller with its original
 * status and Problem Details body, bypassing the Caffeine/circuit-breaker fallback path — a
 * business rejection (404, 409, validation) is not a service failure.
 *
 * <p>The body is kept as a raw {@code Map} rather than deserialized into {@link
 * org.springframework.http.ProblemDetail}: Spring's {@code ProblemDetail} has no {@code
 * @JsonAnySetter} counterpart to its {@code @JsonAnyGetter}-based serialization, so round-tripping
 * through it silently drops extension members such as {@code code} — exactly the part the caller
 * needs.
 */
public class DownstreamClientErrorException extends RuntimeException {

    private final HttpStatusCode status;
    private final Map<String, Object> body;

    public DownstreamClientErrorException(HttpStatusCode status, Map<String, Object> body) {
        super(String.valueOf(body.get("detail")));
        this.status = status;
        this.body = body;
    }

    public HttpStatusCode status() {
        return status;
    }

    public Map<String, Object> body() {
        return body;
    }
}
