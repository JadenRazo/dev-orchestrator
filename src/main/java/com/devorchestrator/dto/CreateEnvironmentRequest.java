package com.devorchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateEnvironmentRequest {
    
    @NotBlank(message = "Template ID is required")
    @Size(max = 50, message = "Template ID must not exceed 50 characters")
    private String templateId;
    
    @NotBlank(message = "Environment name is required")
    @Size(min = 3, max = 100, message = "Environment name must be between 3 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9-_\\s]*[a-zA-Z0-9]$", 
             message = "Environment name must start and end with alphanumeric characters and may contain hyphens, underscores, and spaces")
    private String name;
    
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}