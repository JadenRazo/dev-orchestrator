package com.devorchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectMetricsDto {
    
    private String projectId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Page<MetricDto> metrics;
    private MetricAggregates aggregates;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MetricAggregates {
    private Double avgCpuPercent;
    private Double maxCpuPercent;
    private Double avgMemoryMb;
    private Double maxMemoryMb;
    private Double totalNetworkInMb;
    private Double totalNetworkOutMb;
    private Double totalDiskReadMb;
    private Double totalDiskWriteMb;
}