package com.example.gatewaysample.gateway.web.exception;

/** Thrown when a request has a valid JWT but no {@code X-API-Key} header. */
public class MissingApiKeyException extends RuntimeException {

    public MissingApiKeyException() {
        super("X-API-Key header is required");
    }
}
