package com.devorchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CloudCostEstimateDto {
    private String projectId;
    private String provider;
    private String strategy;
    private BigDecimal estimatedMonthlyCost;
    private String costComparisonJson;
    private String instanceRecommendationsJson;
    private String migrationStepsJson;
}