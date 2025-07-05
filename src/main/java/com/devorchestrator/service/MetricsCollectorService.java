package com.devorchestrator.service;

import com.devorchestrator.entity.ProjectRegistration;
import com.devorchestrator.entity.ResourceMetric;
import com.devorchestrator.repository.ResourceMetricRepository;
import com.devorchestrator.websocket.MetricsWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@Transactional
public class MetricsCollectorService {
    
    private final ResourceMetricRepository metricRepository;
    private final ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private MetricsWebSocketHandler webSocketHandler;
    
    // Cache for active containers per project
    private final Map<String, Set<String>> projectContainers = new ConcurrentHashMap<>();
    
    // Patterns for parsing Docker stats
    private static final Pattern DOCKER_STATS_PATTERN = Pattern.compile(
        "([a-f0-9]+)\\s+([^\\s]+)\\s+([0-9.]+)%\\s+([^\\s]+)\\s*/\\s*([^\\s]+)\\s+([0-9.]+)%\\s+([^\\s]+)\\s*/\\s*([^\\s]+)\\s+([^\\s]+)\\s*/\\s*([^\\s]+)\\s+([0-9]+)"
    );
    
    public MetricsCollectorService(ResourceMetricRepository metricRepository,
                                 ObjectMapper objectMapper) {
        this.metricRepository = metricRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Collects metrics for a specific project
     */
    public CompletableFuture<List<ResourceMetric>> collectProjectMetrics(ProjectRegistration project) {
        return CompletableFuture.supplyAsync(() -> {
            List<ResourceMetric> metrics = new ArrayList<>();
            
            try {
                // Collect Docker container metrics
                metrics.addAll(collectDockerMetrics(project));
                
                // Collect system-level metrics if available
                metrics.addAll(collectSystemMetrics(project));
                
                // Collect application-specific metrics if available
                metrics.addAll(collectApplicationMetrics(project));
                
                // Save all metrics
                if (!metrics.isEmpty()) {
                    metricRepository.saveAll(metrics);
                    log.debug("Collected {} metrics for project {}", metrics.size(), project.getName());
                    
                    // Broadcast metrics via WebSocket
                    if (webSocketHandler != null) {
                        webSocketHandler.broadcastMetrics(project.getId(), metrics);
                    }
                }
                
            } catch (Exception e) {
                log.error("Error collecting metrics for project {}: {}", project.getName(), e.getMessage());
            }
            
            return metrics;
        });
    }
    
    /**
     * Scheduled task to collect metrics for all active projects
     */
    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    public void collectMetricsForActiveProjects() {
        // This would be integrated with ProjectRegistryService
        log.debug("Scheduled metrics collection triggered");
    }
    
    /**
     * Collects Docker container metrics
     */
    private List<ResourceMetric> collectDockerMetrics(ProjectRegistration project) throws IOException {
        List<ResourceMetric> metrics = new ArrayList<>();
        LocalDateTime timestamp = LocalDateTime.now();
        
        // Get containers for this project
        Set<String> containers = getProjectContainers(project);
        
        if (containers.isEmpty()) {
            return metrics;
        }
        
        // Run docker stats command
        ProcessBuilder pb = new ProcessBuilder(
            "docker", "stats", "--no-stream", "--format", 
            "table {{.Container}}\t{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}\t{{.BlockIO}}\t{{.PIDs}}"
        );
        
        Process process = pb.start();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            boolean headerSkipped = false;
            
            while ((line = reader.readLine()) != null) {
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }
                
                DockerStats stats = parseDockerStatsLine(line);
                if (stats != null && containers.contains(stats.containerId)) {
                    // CPU metric
                    metrics.add(ResourceMetric.builder()
                        .project(project)
                        .containerId(stats.containerId)
                        .metricType(ResourceMetric.MetricType.CPU)
                        .metricName("cpu_usage_percent")
                        .source(ResourceMetric.MetricSource.DOCKER)
                        .value(stats.cpuPercent)
                        .unit("percent")
                        .recordedAt(timestamp)
                        .build());
                    
                    // Memory usage metric
                    metrics.add(ResourceMetric.builder()
                        .project(project)
                        .containerId(stats.containerId)
                        .metricType(ResourceMetric.MetricType.MEMORY)
                        .metricName("memory_used_mb")
                        .source(ResourceMetric.MetricSource.DOCKER)
                        .value(stats.memoryUsedMb)
                        .unit("MB")
                        .recordedAt(timestamp)
                        .build());
                    
                    // Memory limit metric
                    metrics.add(ResourceMetric.builder()
                        .project(project)
                        .containerId(stats.containerId)
                        .metricType(ResourceMetric.MetricType.MEMORY)
                        .metricName("memory_limit_mb")
                        .source(ResourceMetric.MetricSource.DOCKER)
                        .value(stats.memoryLimitMb)
                        .unit("MB")
                        .recordedAt(timestamp)
                        .build());
                    
                    // Memory percentage metric
                    metrics.add(ResourceMetric.builder()
                        .project(project)
                        .containerId(stats.containerId)
                        .metricType(ResourceMetric.MetricType.MEMORY)
                        .metricName("memory_usage_percent")
                        .source(ResourceMetric.MetricSource.DOCKER)
                        .value(stats.memoryPercent)
                        .unit("percent")
                        .recordedAt(timestamp)
                        .build());
                    
                    // Network I/O metrics
                    if (stats.networkInMb != null && stats.networkOutMb != null) {
                        metrics.add(ResourceMetric.builder()
                            .project(project)
                            .containerId(stats.containerId)
                            .metricType(ResourceMetric.MetricType.NETWORK)
                            .metricName("network_in_mb")
                            .source(ResourceMetric.MetricSource.DOCKER)
                            .value(stats.networkInMb)
                            .unit("MB")
                            .recordedAt(timestamp)
                            .build());
                        
                        metrics.add(ResourceMetric.builder()
                            .project(project)
                            .containerId(stats.containerId)
                            .metricType(ResourceMetric.MetricType.NETWORK)
                            .metricName("network_out_mb")
                            .source(ResourceMetric.MetricSource.DOCKER)
                            .value(stats.networkOutMb)
                            .unit("MB")
                            .recordedAt(timestamp)
                            .build());
                    }
                    
                    // Block I/O metrics
                    if (stats.blockInMb != null && stats.blockOutMb != null) {
                        metrics.add(ResourceMetric.builder()
                            .project(project)
                            .containerId(stats.containerId)
                            .metricType(ResourceMetric.MetricType.DISK)
                            .metricName("disk_read_mb")
                            .source(ResourceMetric.MetricSource.DOCKER)
                            .value(stats.blockInMb)
                            .unit("MB")
                            .recordedAt(timestamp)
                            .build());
                        
                        metrics.add(ResourceMetric.builder()
                            .project(project)
                            .containerId(stats.containerId)
                            .metricType(ResourceMetric.MetricType.DISK)
                            .metricName("disk_write_mb")
                            .source(ResourceMetric.MetricSource.DOCKER)
                            .value(stats.blockOutMb)
                            .unit("MB")
                            .recordedAt(timestamp)
                            .build());
                    }
                }
            }
        }
        
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Docker stats collection interrupted");
        }
        
        return metrics;
    }
    
    /**
     * Collects system-level metrics
     */
    private List<ResourceMetric> collectSystemMetrics(ProjectRegistration project) {
        List<ResourceMetric> metrics = new ArrayList<>();
        LocalDateTime timestamp = LocalDateTime.now();
        
        try {
            // Collect overall system CPU usage
            BigDecimal cpuUsage = getSystemCpuUsage();
            if (cpuUsage != null) {
                metrics.add(ResourceMetric.builder()
                    .project(project)
                    .metricType(ResourceMetric.MetricType.CPU)
                    .metricName("system_cpu_usage_percent")
                    .source(ResourceMetric.MetricSource.SYSTEM)
                    .value(cpuUsage)
                    .unit("percent")
                    .recordedAt(timestamp)
                    .build());
            }
            
            // Collect system memory usage
            SystemMemoryInfo memInfo = getSystemMemoryInfo();
            if (memInfo != null) {
                metrics.add(ResourceMetric.builder()
                    .project(project)
                    .metricType(ResourceMetric.MetricType.MEMORY)
                    .metricName("system_memory_used_mb")
                    .source(ResourceMetric.MetricSource.SYSTEM)
                    .value(memInfo.usedMb)
                    .unit("MB")
                    .recordedAt(timestamp)
                    .build());
                
                metrics.add(ResourceMetric.builder()
                    .project(project)
                    .metricType(ResourceMetric.MetricType.MEMORY)
                    .metricName("system_memory_available_mb")
                    .source(ResourceMetric.MetricSource.SYSTEM)
                    .value(memInfo.availableMb)
                    .unit("MB")
                    .recordedAt(timestamp)
                    .build());
            }
            
        } catch (Exception e) {
            log.debug("Error collecting system metrics: {}", e.getMessage());
        }
        
        return metrics;
    }
    
    /**
     * Collects application-specific metrics (e.g., from Prometheus endpoints)
     */
    private List<ResourceMetric> collectApplicationMetrics(ProjectRegistration project) {
        List<ResourceMetric> metrics = new ArrayList<>();
        
        // This would integrate with application-specific metrics endpoints
        // For example, scraping Prometheus metrics from the application
        
        return metrics;
    }
    
    /**
     * Gets containers associated with a project
     */
    private Set<String> getProjectContainers(ProjectRegistration project) {
        // In a real implementation, this would:
        // 1. Query Docker for containers with specific labels
        // 2. Match containers by project name or environment ID
        // 3. Cache the results
        
        return projectContainers.computeIfAbsent(project.getId(), k -> {
            Set<String> containers = new HashSet<>();
            
            try {
                // Example: docker ps --filter "label=project=<project-name>"
                ProcessBuilder pb = new ProcessBuilder(
                    "docker", "ps", "--filter", "label=project=" + project.getName(),
                    "--format", "{{.ID}}"
                );
                
                Process process = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        containers.add(line.trim());
                    }
                }
                
                process.waitFor();
            } catch (Exception e) {
                log.error("Error getting containers for project {}: {}", project.getName(), e.getMessage());
            }
            
            return containers;
        });
    }
    
    /**
     * Parses a line from docker stats output
     */
    private DockerStats parseDockerStatsLine(String line) {
        Matcher matcher = DOCKER_STATS_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        
        try {
            DockerStats stats = new DockerStats();
            stats.containerId = matcher.group(1);
            stats.name = matcher.group(2);
            stats.cpuPercent = new BigDecimal(matcher.group(3).replace("%", ""));
            
            // Parse memory usage
            String memUsed = matcher.group(4);
            String memLimit = matcher.group(5);
            stats.memoryUsedMb = parseMemoryValue(memUsed);
            stats.memoryLimitMb = parseMemoryValue(memLimit);
            stats.memoryPercent = new BigDecimal(matcher.group(6).replace("%", ""));
            
            // Parse network I/O
            String netIn = matcher.group(7);
            String netOut = matcher.group(8);
            String[] netParts = parseIOValues(netIn, netOut);
            if (netParts != null) {
                stats.networkInMb = new BigDecimal(netParts[0]);
                stats.networkOutMb = new BigDecimal(netParts[1]);
            }
            
            // Parse block I/O
            String blockIn = matcher.group(9);
            String blockOut = matcher.group(10);
            String[] blockParts = parseIOValues(blockIn, blockOut);
            if (blockParts != null) {
                stats.blockInMb = new BigDecimal(blockParts[0]);
                stats.blockOutMb = new BigDecimal(blockParts[1]);
            }
            
            stats.pids = Integer.parseInt(matcher.group(11));
            
            return stats;
        } catch (Exception e) {
            log.debug("Error parsing docker stats line: {}", line, e);
            return null;
        }
    }
    
    /**
     * Parses memory value from Docker stats (e.g., "1.5GiB" -> MB)
     */
    private BigDecimal parseMemoryValue(String value) {
        value = value.trim().toUpperCase();
        
        if (value.endsWith("GIB") || value.endsWith("GB")) {
            return new BigDecimal(value.replaceAll("[^0-9.]", ""))
                .multiply(new BigDecimal("1024"));
        } else if (value.endsWith("MIB") || value.endsWith("MB")) {
            return new BigDecimal(value.replaceAll("[^0-9.]", ""));
        } else if (value.endsWith("KIB") || value.endsWith("KB")) {
            return new BigDecimal(value.replaceAll("[^0-9.]", ""))
                .divide(new BigDecimal("1024"), 2, RoundingMode.HALF_UP);
        }
        
        return BigDecimal.ZERO;
    }
    
    /**
     * Parses I/O values (network/block)
     */
    private String[] parseIOValues(String in, String out) {
        try {
            BigDecimal inValue = parseMemoryValue(in);
            BigDecimal outValue = parseMemoryValue(out);
            return new String[]{inValue.toString(), outValue.toString()};
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Gets system CPU usage percentage
     */
    private BigDecimal getSystemCpuUsage() {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", 
                "top -bn1 | grep 'Cpu(s)' | sed 's/.*, *\\([0-9.]*\\)%* id.*/\\1/' | awk '{print 100 - $1}'");
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    return new BigDecimal(line.trim());
                }
            }
            
            process.waitFor();
        } catch (Exception e) {
            log.debug("Error getting system CPU usage: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Gets system memory information
     */
    private SystemMemoryInfo getSystemMemoryInfo() {
        try {
            ProcessBuilder pb = new ProcessBuilder("free", "-m");
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Mem:")) {
                        String[] parts = line.split("\\s+");
                        SystemMemoryInfo info = new SystemMemoryInfo();
                        info.totalMb = new BigDecimal(parts[1]);
                        info.usedMb = new BigDecimal(parts[2]);
                        info.availableMb = new BigDecimal(parts[6]);
                        return info;
                    }
                }
            }
            
            process.waitFor();
        } catch (Exception e) {
            log.debug("Error getting system memory info: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Clears metrics cache for a project
     */
    public void clearProjectCache(String projectId) {
        projectContainers.remove(projectId);
    }
    
    // Helper classes
    private static class DockerStats {
        String containerId;
        String name;
        BigDecimal cpuPercent;
        BigDecimal memoryUsedMb;
        BigDecimal memoryLimitMb;
        BigDecimal memoryPercent;
        BigDecimal networkInMb;
        BigDecimal networkOutMb;
        BigDecimal blockInMb;
        BigDecimal blockOutMb;
        Integer pids;
    }
    
    private static class SystemMemoryInfo {
        BigDecimal totalMb;
        BigDecimal usedMb;
        BigDecimal availableMb;
    }
}