package com.devorchestrator.exception;

import com.devorchestrator.entity.EnvironmentStatus;

public class InvalidEnvironmentStateException extends DevOrchestratorException {

    private static final String ERROR_CODE = "INVALID_ENVIRONMENT_STATE";

    public InvalidEnvironmentStateException(String operation, EnvironmentStatus currentStatus) {
        super(String.format("Cannot perform operation '%s' on environment with status '%s'", 
              operation, currentStatus));
    }

    public InvalidEnvironmentStateException(String operation, EnvironmentStatus currentStatus, 
                                          EnvironmentStatus[] requiredStatuses) {
        super(String.format("Cannot perform operation '%s' on environment with status '%s'. Required status: %s", 
              operation, currentStatus, java.util.Arrays.toString(requiredStatuses)));
    }

    public InvalidEnvironmentStateException(String message) {
        super(message);
    }

    @Override
    public String getErrorCode() {
        return ERROR_CODE;
    }
}