package com.example.gatewaysample.pricing.exception;

public class SimulatedFailureException extends RuntimeException {

    public SimulatedFailureException() {
        super("Simulated failure requested via mode=fail");
    }
}
