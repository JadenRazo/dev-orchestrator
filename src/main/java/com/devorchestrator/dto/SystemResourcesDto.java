package com.devorchestrator.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SystemResourcesDto {
    
    private double cpuUsagePercent;
    private long memoryUsageMb;
    private long totalMemoryMb;
    private long availableMemoryMb;
    private double memoryUsagePercent;
    
    private long allocatedCpuMb;
    private long allocatedMemoryMb;
    
    private int availableProcessors;
    private int totalContainers;
    private int runningContainers;
    
    private double diskUsagePercent;
    private long diskUsageGb;
    private long totalDiskGb;
    
    private SystemStatus status;
    
    public enum SystemStatus {
        HEALTHY,
        WARNING,
        CRITICAL
    }
    
    public SystemStatus calculateStatus() {
        if (cpuUsagePercent > 90 || memoryUsagePercent > 90 || diskUsagePercent > 90) {
            return SystemStatus.CRITICAL;
        } else if (cpuUsagePercent > 75 || memoryUsagePercent > 75 || diskUsagePercent > 75) {
            return SystemStatus.WARNING;
        } else {
            return SystemStatus.HEALTHY;
        }
    }
}