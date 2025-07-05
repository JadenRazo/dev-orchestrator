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
public class AnalysisResultDto {
    
    private String projectId;
    private String analysisId;
    private String status; // ANALYZING, COMPLETED, FAILED
    private String message;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long durationMs;
}