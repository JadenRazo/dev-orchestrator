package com.devorchestrator.exception;

public class UserNotFoundException extends DevOrchestratorException {

    private static final String ERROR_CODE = "USER_NOT_FOUND";

    public UserNotFoundException(Long userId) {
        super(String.format("User not found: %d", userId));
    }

    public UserNotFoundException(String username) {
        super(String.format("User not found: %s", username));
    }

    public UserNotFoundException(String identifier, String additionalContext) {
        super(String.format("User not found: %s. %s", identifier, additionalContext));
    }

    @Override
    public String getErrorCode() {
        return ERROR_CODE;
    }
}