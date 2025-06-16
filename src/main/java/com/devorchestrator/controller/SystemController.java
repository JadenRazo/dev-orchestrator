package com.devorchestrator.controller;

import com.devorchestrator.service.ResourceMonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System Management", description = "APIs for system monitoring and management")
@Slf4j
public class SystemController {

    private final ResourceMonitoringService resourceService;

    public SystemController(ResourceMonitoringService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping("/resources")
    @Operation(summary = "Get system resources", description = "Retrieves current system resource usage and availability")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResourceMonitoringService.ResourceUsageStats> getSystemResources() {
        
        ResourceMonitoringService.ResourceUsageStats stats = resourceService.getCurrentResourceStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/health")
    @Operation(summary = "Get system health", description = "Provides overall system health status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        
        ResourceMonitoringService.ResourceUsageStats stats = resourceService.getCurrentResourceStats();
        
        boolean healthy = stats.getCpuUsagePercent() < 90 && 
                         (stats.getMemoryUsageMb() * 100.0 / stats.getTotalMemoryMb()) < 90;
        
        return ResponseEntity.ok(Map.of(
            "status", healthy ? "healthy" : "degraded",
            "cpuUsage", stats.getCpuUsagePercent(),
            "memoryUsage", (stats.getMemoryUsageMb() * 100.0 / stats.getTotalMemoryMb()),
            "availableMemoryMb", stats.getAvailableMemoryMb(),
            "allocatedCpuMb", stats.getAllocatedCpuMb(),
            "allocatedMemoryMb", stats.getAllocatedMemoryMb()
        ));
    }
}