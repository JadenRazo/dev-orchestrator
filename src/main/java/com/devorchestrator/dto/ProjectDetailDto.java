package com.devorchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectDetailDto {
    
    private String id;
    private String name;
    private String path;
    private String description;
    private String projectType;
    private String status;
    private String healthStatus;
    private String environmentId;
    
    // Configuration
    private Boolean autoStart;
    private Boolean autoScaleEnabled;
    private Boolean monitoringEnabled;
    
    // Analysis summary
    private String primaryLanguage;
    private String primaryFramework;
    private Integer databaseCount;
    private Integer serviceCount;
    private Integer dependencyCount;
    private BigDecimal analysisConfidence;
    
    // Resource requirements
    private Integer estimatedMemoryMb;
    private BigDecimal estimatedCpuCores;
    private List<Integer> requiredPorts;
    
    // Latest metrics
    private List<MetricDto> latestMetrics;
    
    // Timestamps
    private LocalDateTime lastAnalyzedAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Additional metadata
    private Map<String, String> metadata;
}