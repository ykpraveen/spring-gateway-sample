package com.example.gatewaysample.apiserver.downstream;

/** Raised when a mutation's downstream dependency is unavailable and there is no cached fallback for it. */
public class DownstreamUnavailableException extends RuntimeException {

    private final String service;

    public DownstreamUnavailableException(String service, Throwable cause) {
        super(service + " is unavailable", cause);
        this.service = service;
    }

    public String service() {
        return service;
    }
}
