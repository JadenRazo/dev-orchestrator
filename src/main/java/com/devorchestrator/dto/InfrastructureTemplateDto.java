package com.devorchestrator.dto;

import com.devorchestrator.entity.InfrastructureProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InfrastructureTemplateDto {
    
    @NotBlank(message = "Template ID is required")
    private String id;
    
    @NotBlank(message = "Template name is required")
    private String name;
    
    private String description;
    
    @NotNull(message = "Infrastructure type is required")
    private InfrastructureProvider infrastructureType;
    
    private String dockerComposeContent;
    
    private String terraformTemplate;
    
    private Map<String, Object> terraformVariables;
    
    private String cloudRegion;
    
    private Set<Integer> exposedPorts;
    
    private Integer memoryLimitMb;
    
    private Double cpuLimit;
    
    private Boolean isPublic;
    
    private Double estimatedCostPerHour;
    
    private Map<String, String> requiredPermissions;
}