package com.devorchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CloudResourceDto {
    
    private String resourceId;
    
    private String resourceType;
    
    private String provider;
    
    private String region;
    
    private String status;
    
    private Map<String, String> properties;
    
    private Map<String, String> tags;
    
    private LocalDateTime createdAt;
    
    private Double estimatedCostPerHour;
}