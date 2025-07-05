package com.devorchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageReportDto {
    private String projectId;
    private int periodDays;
    private Double totalCost;
    private BigDecimal avgCpuHours;
    private BigDecimal avgMemoryGbHours;
    private BigDecimal avgNetworkGb;
    private BigDecimal peakCpuPercent;
    private Integer peakMemoryMb;
    private BigDecimal availabilityPercent;
    private LocalDateTime lastReportDate;
    private BigDecimal errorRate;
}