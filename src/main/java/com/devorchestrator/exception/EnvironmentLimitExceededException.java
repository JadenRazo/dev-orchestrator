package com.devorchestrator.exception;

public class EnvironmentLimitExceededException extends DevOrchestratorException {

    private static final String ERROR_CODE = "ENVIRONMENT_LIMIT_EXCEEDED";

    public EnvironmentLimitExceededException(int currentCount, int maxAllowed) {
        super(String.format("Environment limit exceeded: %d/%d environments. Cannot create more environments.", 
              currentCount, maxAllowed));
    }

    public EnvironmentLimitExceededException(String username, int currentCount, int maxAllowed) {
        super(String.format("Environment limit exceeded for user %s: %d/%d environments. Cannot create more environments.", 
              username, currentCount, maxAllowed));
    }

    public EnvironmentLimitExceededException(String message) {
        super(message);
    }

    @Override
    public String getErrorCode() {
        return ERROR_CODE;
    }
}