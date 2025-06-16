package com.devorchestrator.exception;

public class DockerOperationException extends DevOrchestratorException {

    private static final String ERROR_CODE = "DOCKER_OPERATION_FAILED";

    public DockerOperationException(String operation, String details) {
        super(String.format("Docker operation failed: %s. Details: %s", operation, details));
    }

    public DockerOperationException(String operation, Throwable cause) {
        super(String.format("Docker operation failed: %s", operation), cause);
    }

    public DockerOperationException(String operation, String details, Throwable cause) {
        super(String.format("Docker operation failed: %s. Details: %s", operation, details), cause);
    }

    @Override
    public String getErrorCode() {
        return ERROR_CODE;
    }
}