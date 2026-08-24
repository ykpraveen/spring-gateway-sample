package com.example.gatewaysample.gateway.web.exception;

/** Thrown when valid JWT + API-key credentials lack the realm role a route requires. */
public class InsufficientRoleException extends RuntimeException {

    private final String requiredAuthority;

    public InsufficientRoleException(String requiredAuthority) {
        super("Missing required role: " + requiredAuthority);
        this.requiredAuthority = requiredAuthority;
    }

    public String requiredAuthority() {
        return requiredAuthority;
    }
}
