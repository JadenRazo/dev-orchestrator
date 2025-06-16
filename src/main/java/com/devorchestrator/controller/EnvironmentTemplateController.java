package com.devorchestrator.controller;

import com.devorchestrator.entity.EnvironmentTemplate;
import com.devorchestrator.service.EnvironmentTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/templates")
@Tag(name = "Template Management", description = "APIs for managing environment templates")
@Slf4j
public class EnvironmentTemplateController {

    private final EnvironmentTemplateService templateService;

    public EnvironmentTemplateController(EnvironmentTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    @Operation(summary = "List all templates", description = "Retrieves all available environment templates")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Page<EnvironmentTemplate>> getTemplates(
            @PageableDefault(size = 20) Pageable pageable) {
        
        Page<EnvironmentTemplate> templates = templateService.getTemplates(pageable);
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/active")
    @Operation(summary = "List active templates", description = "Retrieves only active/enabled environment templates")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<EnvironmentTemplate>> getActiveTemplates() {
        
        List<EnvironmentTemplate> templates = templateService.getActiveTemplates();
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get template details", description = "Retrieves detailed information about a specific template")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<EnvironmentTemplate> getTemplate(
            @Parameter(description = "Template ID") @PathVariable String id) {
        
        EnvironmentTemplate template = templateService.getTemplate(id);
        return ResponseEntity.ok(template);
    }

    @GetMapping("/{id}/validate")
    @Operation(summary = "Validate template", description = "Validates if a template configuration is valid")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ValidationResult> validateTemplate(
            @Parameter(description = "Template ID") @PathVariable String id) {
        
        boolean isValid = templateService.isTemplateValid(id);
        
        ValidationResult result = new ValidationResult();
        result.setValid(isValid);
        result.setTemplateId(id);
        result.setMessage(isValid ? "Template is valid" : "Template validation failed");
        
        return ResponseEntity.ok(result);
    }

    public static class ValidationResult {
        private boolean valid;
        private String templateId;
        private String message;

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public String getTemplateId() {
            return templateId;
        }

        public void setTemplateId(String templateId) {
            this.templateId = templateId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}