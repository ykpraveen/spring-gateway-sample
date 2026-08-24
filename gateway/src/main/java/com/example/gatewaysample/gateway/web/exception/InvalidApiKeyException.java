package com.example.gatewaysample.gateway.web.exception;

/** Thrown when the {@code X-API-Key} header value is unknown, revoked, or inactive. */
public class InvalidApiKeyException extends RuntimeException {

    public InvalidApiKeyException() {
        super("API key is invalid, revoked, or inactive");
    }
}
