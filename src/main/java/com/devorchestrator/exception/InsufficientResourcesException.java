package com.devorchestrator.exception;

public class InsufficientResourcesException extends DevOrchestratorException {

    private static final String ERROR_CODE = "INSUFFICIENT_RESOURCES";

    public InsufficientResourcesException(String resource, int required, int available) {
        super(String.format("Insufficient %s: required %d, available %d", resource, required, available));
    }

    public InsufficientResourcesException(String resource, double required, double available) {
        super(String.format("Insufficient %s: required %.2f, available %.2f", resource, required, available));
    }

    public InsufficientResourcesException(String message) {
        super(message);
    }

    @Override
    public String getErrorCode() {
        return ERROR_CODE;
    }
}