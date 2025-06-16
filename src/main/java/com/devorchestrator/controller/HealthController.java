package com.devorchestrator.controller;

import com.devorchestrator.service.ContainerOrchestrationService;
import com.devorchestrator.service.ResourceMonitoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Slf4j
public class HealthController {

    private final DataSource dataSource;
    private final ContainerOrchestrationService containerService;
    private final ResourceMonitoringService resourceService;

    public HealthController(DataSource dataSource,
                          ContainerOrchestrationService containerService,
                          ResourceMonitoringService resourceService) {
        this.dataSource = dataSource;
        this.containerService = containerService;
        this.resourceService = resourceService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        boolean allHealthy = true;

        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("service", "dev-environment-orchestrator");
        health.put("version", getClass().getPackage().getImplementationVersion());

        Map<String, Object> components = new LinkedHashMap<>();
        
        // Database health check
        Map<String, Object> database = checkDatabaseHealth();
        components.put("database", database);
        if (!"UP".equals(database.get("status"))) {
            allHealthy = false;
        }

        // Docker health check
        Map<String, Object> docker = checkDockerHealth();
        components.put("docker", docker);
        if (!"UP".equals(docker.get("status"))) {
            allHealthy = false;
        }

        // System resources check
        Map<String, Object> resources = checkSystemResources();
        components.put("resources", resources);
        if (!"UP".equals(resources.get("status"))) {
            allHealthy = false;
        }

        health.put("components", components);
        
        if (!allHealthy) {
            health.put("status", "DOWN");
            return ResponseEntity.status(503).body(health);
        }

        return ResponseEntity.ok(health);
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> readiness = new LinkedHashMap<>();
        boolean ready = true;

        // Check if application can accept traffic
        try {
            // Verify database connectivity
            try (Connection conn = dataSource.getConnection()) {
                conn.isValid(5);
            }

            // Verify Docker daemon is accessible
            if (!containerService.isDockerAvailable()) {
                ready = false;
            }

            readiness.put("status", ready ? "READY" : "NOT_READY");
            readiness.put("timestamp", LocalDateTime.now());
            readiness.put("message", ready ? "Application ready to accept traffic" : "Application not ready");

        } catch (Exception e) {
            log.error("Readiness check failed: {}", e.getMessage());
            readiness.put("status", "NOT_READY");
            readiness.put("timestamp", LocalDateTime.now());
            readiness.put("message", "Application not ready: " + e.getMessage());
            ready = false;
        }

        return ready ? ResponseEntity.ok(readiness) : ResponseEntity.status(503).body(readiness);
    }

    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> liveness() {
        Map<String, Object> liveness = new LinkedHashMap<>();
        
        // Simple liveness check - application is running
        liveness.put("status", "ALIVE");
        liveness.put("timestamp", LocalDateTime.now());
        liveness.put("uptime", getUptime());
        
        return ResponseEntity.ok(liveness);
    }

    private Map<String, Object> checkDatabaseHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        
        try (Connection conn = dataSource.getConnection()) {
            long startTime = System.currentTimeMillis();
            boolean isValid = conn.isValid(5);
            long responseTime = System.currentTimeMillis() - startTime;
            
            health.put("status", isValid ? "UP" : "DOWN");
            health.put("responseTime", responseTime + "ms");
            health.put("database", conn.getMetaData().getDatabaseProductName());
            health.put("databaseVersion", conn.getMetaData().getDatabaseProductVersion());
            
        } catch (SQLException e) {
            log.error("Database health check failed: {}", e.getMessage());
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
        }
        
        return health;
    }

    private Map<String, Object> checkDockerHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        
        try {
            boolean dockerAvailable = containerService.isDockerAvailable();
            health.put("status", dockerAvailable ? "UP" : "DOWN");
            
            if (dockerAvailable) {
                health.put("version", containerService.getDockerVersion());
                health.put("runningContainers", containerService.getRunningContainerCount());
            } else {
                health.put("error", "Docker daemon not accessible");
            }
            
        } catch (Exception e) {
            log.error("Docker health check failed: {}", e.getMessage());
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
        }
        
        return health;
    }

    private Map<String, Object> checkSystemResources() {
        Map<String, Object> health = new LinkedHashMap<>();
        
        try {
            double cpuUsage = resourceService.getCpuUsagePercentage();
            double memoryUsage = resourceService.getMemoryUsagePercentage();
            
            boolean cpuHealthy = cpuUsage < 90.0;
            boolean memoryHealthy = memoryUsage < 90.0;
            boolean systemHealthy = cpuHealthy && memoryHealthy;
            
            health.put("status", systemHealthy ? "UP" : "WARNING");
            health.put("cpu", Map.of(
                "usage", cpuUsage + "%",
                "healthy", cpuHealthy
            ));
            health.put("memory", Map.of(
                "usage", memoryUsage + "%",
                "healthy", memoryHealthy
            ));
            health.put("availableMemoryMB", resourceService.getAvailableMemoryMB());
            
        } catch (Exception e) {
            log.error("System resources health check failed: {}", e.getMessage());
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
        }
        
        return health;
    }

    private String getUptime() {
        long uptimeMillis = System.currentTimeMillis() - getStartTime();
        long seconds = uptimeMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes % 60, seconds % 60);
        } else {
            return seconds + "s";
        }
    }

    private long getStartTime() {
        // Spring Boot start time - in real implementation, you'd inject ApplicationContext
        // and use applicationContext.getStartupDate()
        return System.currentTimeMillis() - 60000; // Placeholder: assume started 1 minute ago
    }
}