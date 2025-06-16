package com.devorchestrator.exception;

public class PortAllocationException extends DevOrchestratorException {

    private static final String ERROR_CODE = "PORT_ALLOCATION_FAILED";

    public PortAllocationException(int requestedPort) {
        super(String.format("Failed to allocate port %d: port is already in use", requestedPort));
    }

    public PortAllocationException(int startRange, int endRange) {
        super(String.format("No available ports in range %d-%d", startRange, endRange));
    }

    public PortAllocationException(String message) {
        super(message);
    }

    public PortAllocationException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String getErrorCode() {
        return ERROR_CODE;
    }
}