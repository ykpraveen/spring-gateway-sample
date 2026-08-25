package com.example.gatewaysample.product.exception;

public class SimulatedFailureException extends RuntimeException {

    public SimulatedFailureException() {
        super("Simulated failure requested via mode=fail");
    }
}
