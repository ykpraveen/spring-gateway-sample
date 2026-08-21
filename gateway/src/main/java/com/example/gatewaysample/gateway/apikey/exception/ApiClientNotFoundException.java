package com.example.gatewaysample.gateway.apikey.exception;

public class ApiClientNotFoundException extends RuntimeException {

    public ApiClientNotFoundException(Long apiClientId) {
        super("API client not found: " + apiClientId);
    }
}
