package com.devorchestrator.exception;

public class TemplateNotFoundException extends DevOrchestratorException {

    private static final String ERROR_CODE = "TEMPLATE_NOT_FOUND";

    public TemplateNotFoundException(String templateId) {
        super(String.format("Environment template not found: %s", templateId));
    }

    public TemplateNotFoundException(String templateId, String additionalContext) {
        super(String.format("Environment template not found: %s. %s", templateId, additionalContext));
    }

    @Override
    public String getErrorCode() {
        return ERROR_CODE;
    }
}