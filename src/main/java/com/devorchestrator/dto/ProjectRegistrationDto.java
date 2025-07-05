package com.devorchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectRegistrationDto {
    
    private String id;
    private String name;
    private String path;
    private String projectType;
    private String status;
    private String healthStatus;
    private Boolean autoStart;
    private Boolean autoScaleEnabled;
    private Boolean monitoringEnabled;
    private LocalDateTime lastAnalyzedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}