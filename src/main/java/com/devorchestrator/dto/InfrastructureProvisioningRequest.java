package com.devorchestrator.dto;

import com.devorchestrator.entity.InfrastructureProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InfrastructureProvisioningRequest {
    
    @NotBlank(message = "Environment name is required")
    private String environmentName;
    
    @NotBlank(message = "Template ID is required")
    private String templateId;
    
    @NotNull(message = "Infrastructure provider is required")
    private InfrastructureProvider infrastructureProvider;
    
    private String cloudRegion;
    
    private Map<String, Object> terraformVariables;
    
    private Map<String, String> environmentVariables;
    
    private Integer autoStopAfterHours = 8;
    
    private String dockerComposeOverride;
}