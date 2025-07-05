package com.devorchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricDto {
    
    private String metricType;
    private String metricName;
    private BigDecimal value;
    private String unit;
    private String containerId;
    private LocalDateTime recordedAt;
    private Map<String, String> tags;
}