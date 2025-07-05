package com.devorchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TerraformOperationStatus {
    
    private String environmentId;
    
    private String operationType;
    
    private String status;
    
    private String phase;
    
    private List<String> messages;
    
    private Map<String, String> outputs;
    
    private Integer progress;
    
    private LocalDateTime startedAt;
    
    private LocalDateTime completedAt;
    
    private String errorMessage;
    
    private String terraformVersion;
}