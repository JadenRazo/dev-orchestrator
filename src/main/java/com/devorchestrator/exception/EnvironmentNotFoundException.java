package com.devorchestrator.exception;

public class EnvironmentNotFoundException extends DevOrchestratorException {

    private static final String ERROR_CODE = "ENVIRONMENT_NOT_FOUND";

    public EnvironmentNotFoundException(String environmentId) {
        super(String.format("Environment not found: %s", environmentId));
    }

    public EnvironmentNotFoundException(String environmentId, String additionalContext) {
        super(String.format("Environment not found: %s. %s", environmentId, additionalContext));
    }

    @Override
    public String getErrorCode() {
        return ERROR_CODE;
    }
}