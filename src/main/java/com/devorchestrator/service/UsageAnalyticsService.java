package com.devorchestrator.service;

import com.devorchestrator.entity.*;
import com.devorchestrator.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class UsageAnalyticsService {
    
    private final UsageReportRepository usageReportRepository;
    private final ResourceMetricRepository metricRepository;
    private final ProjectRegistrationRepository projectRepository;
    private final ObjectMapper objectMapper;
    
    // Cost factors per unit (example values, should be configurable)
    private static final BigDecimal CPU_HOUR_COST = new BigDecimal("0.05");
    private static final BigDecimal MEMORY_GB_HOUR_COST = new BigDecimal("0.01");
    private static final BigDecimal STORAGE_GB_COST = new BigDecimal("0.10");
    private static final BigDecimal NETWORK_GB_COST = new BigDecimal("0.12");
    
    public UsageAnalyticsService(UsageReportRepository usageReportRepository,
                               ResourceMetricRepository metricRepository,
                               ProjectRegistrationRepository projectRepository,
                               ObjectMapper objectMapper) {
        this.usageReportRepository = usageReportRepository;
        this.metricRepository = metricRepository;
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Generate usage report for a project
     */
    public UsageReport generateUsageReport(String projectId, LocalDateTime startTime, 
                                         LocalDateTime endTime, UsageReport.ReportType reportType) {
        log.info("Generating {} report for project {} from {} to {}", 
                reportType, projectId, startTime, endTime);
        
        ProjectRegistration project = projectRepository.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        
        // Check if report already exists
        if (usageReportRepository.existsByProjectIdAndPeriodStartAndPeriodEndAndReportType(
                projectId, startTime, endTime, reportType)) {
            log.warn("Report already exists for this period");
            return usageReportRepository.findByProjectIdAndPeriodStartBetweenOrderByPeriodStartDesc(
                    projectId, startTime, endTime, null).getContent().get(0);
        }
        
        UsageReport report = UsageReport.builder()
            .id(UUID.randomUUID().toString())
            .project(project)
            .periodStart(startTime)
            .periodEnd(endTime)
            .reportType(reportType)
            .build();
        
        // Calculate resource usage
        calculateResourceUsage(report, projectId, startTime, endTime);
        
        // Calculate service metrics
        calculateServiceMetrics(report, projectId, startTime, endTime);
        
        // Calculate peak usage
        calculatePeakUsage(report, projectId, startTime, endTime);
        
        // Calculate availability
        calculateAvailability(report, projectId, startTime, endTime);
        
        // Estimate costs
        estimateCosts(report);
        
        return usageReportRepository.save(report);
    }
    
    /**
     * Calculate resource usage metrics
     */
    private void calculateResourceUsage(UsageReport report, String projectId, 
                                      LocalDateTime startTime, LocalDateTime endTime) {
        // CPU usage
        List<ResourceMetric> cpuMetrics = metricRepository
            .findByProjectIdAndMetricTypeAndRecordedAtBetweenOrderByRecordedAtDesc(
                projectId, ResourceMetric.MetricType.CPU, startTime, endTime);
        
        if (!cpuMetrics.isEmpty()) {
            BigDecimal totalCpuHours = calculateResourceHours(cpuMetrics, "cpu_usage_percent", 100);
            report.setTotalCpuHours(totalCpuHours);
        }
        
        // Memory usage
        List<ResourceMetric> memoryMetrics = metricRepository
            .findByProjectIdAndMetricTypeAndRecordedAtBetweenOrderByRecordedAtDesc(
                projectId, ResourceMetric.MetricType.MEMORY, startTime, endTime);
        
        if (!memoryMetrics.isEmpty()) {
            BigDecimal totalMemoryGbHours = calculateResourceHours(memoryMetrics, "memory_used_mb", 1024);
            report.setTotalMemoryGbHours(totalMemoryGbHours);
        }
        
        // Network usage
        List<ResourceMetric> networkMetrics = metricRepository
            .findByProjectIdAndMetricTypeAndRecordedAtBetweenOrderByRecordedAtDesc(
                projectId, ResourceMetric.MetricType.NETWORK, startTime, endTime);
        
        if (!networkMetrics.isEmpty()) {
            BigDecimal totalNetworkGb = calculateTotalUsage(networkMetrics, 
                Arrays.asList("network_in_mb", "network_out_mb"), 1024);
            report.setTotalNetworkGb(totalNetworkGb);
        }
        
        // Storage usage
        List<ResourceMetric> diskMetrics = metricRepository
            .findByProjectIdAndMetricTypeAndRecordedAtBetweenOrderByRecordedAtDesc(
                projectId, ResourceMetric.MetricType.DISK, startTime, endTime);
        
        if (!diskMetrics.isEmpty()) {
            BigDecimal totalStorageGb = calculateTotalUsage(diskMetrics, 
                Arrays.asList("disk_read_mb", "disk_write_mb"), 1024);
            report.setTotalStorageGb(totalStorageGb);
        }
    }
    
    /**
     * Calculate service-level metrics
     */
    private void calculateServiceMetrics(UsageReport report, String projectId,
                                       LocalDateTime startTime, LocalDateTime endTime) {
        // Calculate uptime hours
        long totalMinutes = ChronoUnit.MINUTES.between(startTime, endTime);
        Long metricCount = metricRepository.countByProjectIdAndRecordedAtBetween(
            projectId, startTime, endTime);
        
        // Assuming metrics are collected every 30 seconds
        BigDecimal uptimeHours = new BigDecimal(metricCount * 0.5 / 60)
            .setScale(4, RoundingMode.HALF_UP);
        report.setTotalUptimeHours(uptimeHours);
        
        // Calculate availability percentage
        BigDecimal totalHours = new BigDecimal(totalMinutes).divide(new BigDecimal(60), 4, RoundingMode.HALF_UP);
        BigDecimal availabilityPercent = uptimeHours.divide(totalHours, 2, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        report.setAvailabilityPercent(availabilityPercent);
        
        // Calculate downtime
        BigDecimal downtimeHours = totalHours.subtract(uptimeHours);
        report.setDowntimeMinutes(downtimeHours.multiply(new BigDecimal(60)).intValue());
        
        // Set placeholder values for now (would come from application metrics)
        report.setTotalRequests(0L);
        report.setErrorCount(0L);
        report.setAverageResponseTimeMs(BigDecimal.ZERO);
    }
    
    /**
     * Calculate peak usage values
     */
    private void calculatePeakUsage(UsageReport report, String projectId,
                                  LocalDateTime startTime, LocalDateTime endTime) {
        // Peak CPU
        Double peakCpu = metricRepository.getMaxMetricValue(
            projectId, "cpu_usage_percent", startTime, endTime);
        if (peakCpu != null) {
            report.setPeakCpuPercent(BigDecimal.valueOf(peakCpu).setScale(2, RoundingMode.HALF_UP));
        }
        
        // Peak Memory
        Double peakMemory = metricRepository.getMaxMetricValue(
            projectId, "memory_used_mb", startTime, endTime);
        if (peakMemory != null) {
            report.setPeakMemoryMb(peakMemory.intValue());
        }
        
        // Peak concurrent users (placeholder)
        report.setPeakConcurrentUsers(0);
    }
    
    /**
     * Calculate availability metrics
     */
    private void calculateAvailability(UsageReport report, String projectId,
                                     LocalDateTime startTime, LocalDateTime endTime) {
        // Already calculated in service metrics
        // Additional availability logic could go here
    }
    
    /**
     * Estimate costs based on resource usage
     */
    private void estimateCosts(UsageReport report) {
        BigDecimal totalCost = BigDecimal.ZERO;
        Map<String, BigDecimal> costBreakdown = new HashMap<>();
        
        // CPU costs
        if (report.getTotalCpuHours() != null) {
            BigDecimal cpuCost = report.getTotalCpuHours().multiply(CPU_HOUR_COST);
            totalCost = totalCost.add(cpuCost);
            costBreakdown.put("cpu", cpuCost);
        }
        
        // Memory costs
        if (report.getTotalMemoryGbHours() != null) {
            BigDecimal memoryCost = report.getTotalMemoryGbHours().multiply(MEMORY_GB_HOUR_COST);
            totalCost = totalCost.add(memoryCost);
            costBreakdown.put("memory", memoryCost);
        }
        
        // Storage costs
        if (report.getTotalStorageGb() != null) {
            BigDecimal storageCost = report.getTotalStorageGb().multiply(STORAGE_GB_COST);
            totalCost = totalCost.add(storageCost);
            costBreakdown.put("storage", storageCost);
        }
        
        // Network costs
        if (report.getTotalNetworkGb() != null) {
            BigDecimal networkCost = report.getTotalNetworkGb().multiply(NETWORK_GB_COST);
            totalCost = totalCost.add(networkCost);
            costBreakdown.put("network", networkCost);
        }
        
        report.setEstimatedCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        
        try {
            report.setCostBreakdownJson(objectMapper.writeValueAsString(costBreakdown));
        } catch (JsonProcessingException e) {
            log.error("Error serializing cost breakdown", e);
        }
    }
    
    /**
     * Calculate resource hours from metrics
     */
    private BigDecimal calculateResourceHours(List<ResourceMetric> metrics, 
                                            String metricName, int divisor) {
        BigDecimal totalHours = BigDecimal.ZERO;
        
        Map<String, List<ResourceMetric>> metricsByName = metrics.stream()
            .filter(m -> m.getMetricName().equals(metricName))
            .collect(Collectors.groupingBy(ResourceMetric::getContainerId));
        
        for (List<ResourceMetric> containerMetrics : metricsByName.values()) {
            for (int i = 0; i < containerMetrics.size() - 1; i++) {
                ResourceMetric current = containerMetrics.get(i);
                ResourceMetric next = containerMetrics.get(i + 1);
                
                long minutesBetween = ChronoUnit.MINUTES.between(
                    next.getRecordedAt(), current.getRecordedAt());
                
                BigDecimal avgValue = current.getValue().add(next.getValue())
                    .divide(new BigDecimal(2), 4, RoundingMode.HALF_UP);
                
                BigDecimal hours = new BigDecimal(minutesBetween)
                    .divide(new BigDecimal(60), 4, RoundingMode.HALF_UP);
                
                BigDecimal resourceHours = avgValue.multiply(hours)
                    .divide(new BigDecimal(divisor), 4, RoundingMode.HALF_UP);
                
                totalHours = totalHours.add(resourceHours);
            }
        }
        
        return totalHours;
    }
    
    /**
     * Calculate total usage from metrics
     */
    private BigDecimal calculateTotalUsage(List<ResourceMetric> metrics, 
                                         List<String> metricNames, int divisor) {
        BigDecimal total = BigDecimal.ZERO;
        
        for (String metricName : metricNames) {
            BigDecimal metricTotal = metrics.stream()
                .filter(m -> m.getMetricName().equals(metricName))
                .map(ResourceMetric::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            total = total.add(metricTotal);
        }
        
        return total.divide(new BigDecimal(divisor), 4, RoundingMode.HALF_UP);
    }
    
    /**
     * Generate daily reports for all active projects
     */
    @Scheduled(cron = "0 0 1 * * *") // Run at 1 AM daily
    public void generateDailyReports() {
        log.info("Starting daily report generation");
        
        LocalDateTime endTime = LocalDate.now().atStartOfDay();
        LocalDateTime startTime = endTime.minusDays(1);
        
        List<ProjectRegistration> activeProjects = projectRepository
            .findByStatusAndMonitoringEnabled(
                ProjectRegistration.ProjectStatus.ACTIVE, true);
        
        for (ProjectRegistration project : activeProjects) {
            try {
                generateUsageReport(project.getId(), startTime, endTime, 
                    UsageReport.ReportType.DAILY);
                log.info("Generated daily report for project: {}", project.getName());
            } catch (Exception e) {
                log.error("Error generating daily report for project: {}", 
                    project.getName(), e);
            }
        }
    }
    
    /**
     * Generate weekly reports for all active projects
     */
    @Scheduled(cron = "0 0 2 * * MON") // Run at 2 AM on Mondays
    public void generateWeeklyReports() {
        log.info("Starting weekly report generation");
        
        LocalDateTime endTime = LocalDate.now().atStartOfDay();
        LocalDateTime startTime = endTime.minusWeeks(1);
        
        List<ProjectRegistration> activeProjects = projectRepository
            .findByStatusAndMonitoringEnabled(
                ProjectRegistration.ProjectStatus.ACTIVE, true);
        
        for (ProjectRegistration project : activeProjects) {
            try {
                generateUsageReport(project.getId(), startTime, endTime, 
                    UsageReport.ReportType.WEEKLY);
                log.info("Generated weekly report for project: {}", project.getName());
            } catch (Exception e) {
                log.error("Error generating weekly report for project: {}", 
                    project.getName(), e);
            }
        }
    }
    
    /**
     * Generate monthly reports for all active projects
     */
    @Scheduled(cron = "0 0 3 1 * *") // Run at 3 AM on the 1st of each month
    public void generateMonthlyReports() {
        log.info("Starting monthly report generation");
        
        LocalDateTime endTime = LocalDate.now().atStartOfDay();
        LocalDateTime startTime = endTime.minusMonths(1);
        
        List<ProjectRegistration> activeProjects = projectRepository
            .findByStatusAndMonitoringEnabled(
                ProjectRegistration.ProjectStatus.ACTIVE, true);
        
        for (ProjectRegistration project : activeProjects) {
            try {
                generateUsageReport(project.getId(), startTime, endTime, 
                    UsageReport.ReportType.MONTHLY);
                log.info("Generated monthly report for project: {}", project.getName());
            } catch (Exception e) {
                log.error("Error generating monthly report for project: {}", 
                    project.getName(), e);
            }
        }
    }
    
    /**
     * Get usage summary for a project
     */
    public Map<String, Object> getUsageSummary(String projectId, int days) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(days);
        
        Map<String, Object> summary = new HashMap<>();
        
        // Get total costs
        Double totalCost = usageReportRepository.getTotalCostForPeriod(
            projectId, startTime, endTime);
        summary.put("totalCost", totalCost != null ? totalCost : 0.0);
        
        // Get latest report
        Optional<UsageReport> latestReport = usageReportRepository
            .findTopByProjectIdOrderByPeriodEndDesc(projectId);
        
        if (latestReport.isPresent()) {
            UsageReport report = latestReport.get();
            summary.put("lastReportDate", report.getPeriodEnd());
            summary.put("peakCpuPercent", report.getPeakCpuPercent());
            summary.put("peakMemoryMb", report.getPeakMemoryMb());
            summary.put("availabilityPercent", report.getAvailabilityPercent());
        }
        
        // Get resource averages
        Object[] averages = usageReportRepository.getAverageResourceUsage(
            projectId, UsageReport.ReportType.DAILY);
        if (averages != null && averages.length > 0) {
            summary.put("avgCpuHours", averages[0]);
            summary.put("avgMemoryGbHours", averages[1]);
            summary.put("avgNetworkGb", averages[2]);
        }
        
        return summary;
    }
    
    /**
     * Clean up old reports
     */
    @Scheduled(cron = "0 0 4 * * SUN") // Run at 4 AM on Sundays
    public void cleanupOldReports() {
        log.info("Starting cleanup of old usage reports");
        
        // Keep reports for 90 days
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        usageReportRepository.deleteByGeneratedAtBefore(cutoff);
        
        log.info("Completed cleanup of old usage reports");
    }
}