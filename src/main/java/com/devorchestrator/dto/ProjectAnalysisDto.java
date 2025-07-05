package com.devorchestrator.dto;

import com.devorchestrator.analyzer.model.*;
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
public class ProjectAnalysisDto {
    
    private String id;
    private String projectId;
    
    // Detected technologies
    private List<DetectedLanguage> languages;
    private List<DetectedFramework> frameworks;
    private List<DetectedDatabase> databases;
    private List<DetectedService> services;
    private List<DetectedDependency> dependencies;
    
    // Environment configuration
    private Map<String, String> environmentVariables;
    
    // Summary
    private String primaryLanguage;
    private String primaryFramework;
    private Integer totalTechnologies;
    
    // Resource requirements
    private Integer estimatedMemoryMb;
    private BigDecimal estimatedCpuCores;
    private List<Integer> requiredPorts;
    
    // Analysis quality
    private BigDecimal overallConfidence;
    private List<Warning> warnings;
    private List<String> recommendations;
    
    // Metadata
    private LocalDateTime analyzedAt;
    private Long analysisDurationMs;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Warning {
        private String category;
        private String message;
        private String severity; // INFO, WARNING, ERROR
    }
}