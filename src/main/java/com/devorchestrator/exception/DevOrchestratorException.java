package com.devorchestrator.exception;

public abstract class DevOrchestratorException extends RuntimeException {

    protected DevOrchestratorException(String message) {
        super(message);
    }

    protected DevOrchestratorException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract String getErrorCode();
}