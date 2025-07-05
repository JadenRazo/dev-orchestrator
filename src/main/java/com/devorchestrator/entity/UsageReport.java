package com.devorchestrator.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "usage_reports")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageReport {
    
    @Id
    @Column(length = 36)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectRegistration project;
    
    // Report period
    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;
    
    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 50)
    private ReportType reportType;
    
    // Resource usage summary
    @Column(name = "total_cpu_hours", precision = 20, scale = 4)
    private BigDecimal totalCpuHours;
    
    @Column(name = "total_memory_gb_hours", precision = 20, scale = 4)
    private BigDecimal totalMemoryGbHours;
    
    @Column(name = "total_storage_gb", precision = 20, scale = 4)
    private BigDecimal totalStorageGb;
    
    @Column(name = "total_network_gb", precision = 20, scale = 4)
    private BigDecimal totalNetworkGb;
    
    // Service usage
    @Column(name = "total_uptime_hours", precision = 20, scale = 4)
    private BigDecimal totalUptimeHours;
    
    @Column(name = "total_requests")
    private Long totalRequests;
    
    @Column(name = "error_count")
    private Long errorCount;
    
    @Column(name = "average_response_time_ms", precision = 10, scale = 2)
    private BigDecimal averageResponseTimeMs;
    
    // Cost estimates
    @Column(name = "estimated_cost", precision = 10, scale = 2)
    private BigDecimal estimatedCost;
    
    @Column(name = "cost_breakdown_json", columnDefinition = "TEXT")
    private String costBreakdownJson;
    
    // Peak usage
    @Column(name = "peak_cpu_percent", precision = 5, scale = 2)
    private BigDecimal peakCpuPercent;
    
    @Column(name = "peak_memory_mb")
    private Integer peakMemoryMb;
    
    @Column(name = "peak_concurrent_users")
    private Integer peakConcurrentUsers;
    
    // Availability
    @Column(name = "availability_percent", precision = 5, scale = 2)
    private BigDecimal availabilityPercent;
    
    @Column(name = "downtime_minutes")
    private Integer downtimeMinutes;
    
    // Metadata
    @CreatedDate
    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;
    
    public enum ReportType {
        DAILY("Daily"),
        WEEKLY("Weekly"),
        MONTHLY("Monthly"),
        CUSTOM("Custom");
        
        private final String displayName;
        
        ReportType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // Helper methods
    public boolean hasHighCpuUsage() {
        return peakCpuPercent != null && peakCpuPercent.compareTo(new BigDecimal("80")) > 0;
    }
    
    public boolean hasHighMemoryUsage() {
        return peakMemoryMb != null && totalMemoryGbHours != null && 
               totalMemoryGbHours.compareTo(new BigDecimal("100")) > 0;
    }
    
    public BigDecimal getErrorRate() {
        if (totalRequests == null || totalRequests == 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(errorCount != null ? errorCount : 0)
            .divide(new BigDecimal(totalRequests), 4, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("100"));
    }
    
    public boolean hasHighErrorRate() {
        return getErrorRate().compareTo(new BigDecimal("5")) > 0;
    }
    
    public BigDecimal getUptimePercentage() {
        return availabilityPercent != null ? availabilityPercent : new BigDecimal("100");
    }
}