package com.devorchestrator.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "scaling_policies")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScalingPolicy {
    
    @Id
    @Column(length = 36)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectRegistration project;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id")
    private Environment environment;
    
    // Policy details
    @Column(name = "policy_name", nullable = false, length = 100)
    private String policyName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 50)
    private PolicyType policyType;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    // Scaling configuration
    @Column(name = "min_instances", nullable = false)
    private Integer minInstances = 1;
    
    @Column(name = "max_instances", nullable = false)
    private Integer maxInstances = 10;
    
    @Column(name = "current_instances")
    private Integer currentInstances = 1;
    
    // Scaling triggers
    @Column(name = "scale_up_threshold", nullable = false, precision = 10, scale = 2)
    private BigDecimal scaleUpThreshold;
    
    @Column(name = "scale_up_duration_seconds")
    private Integer scaleUpDurationSeconds = 300;
    
    @Column(name = "scale_down_threshold", nullable = false, precision = 10, scale = 2)
    private BigDecimal scaleDownThreshold;
    
    @Column(name = "scale_down_duration_seconds")
    private Integer scaleDownDurationSeconds = 300;
    
    // Scaling behavior
    @Column(name = "scale_up_increment")
    private Integer scaleUpIncrement = 1;
    
    @Column(name = "scale_down_increment")
    private Integer scaleDownIncrement = 1;
    
    @Column(name = "cooldown_seconds")
    private Integer cooldownSeconds = 300;
    
    // Metrics configuration
    @Column(name = "metric_name", nullable = false, length = 100)
    private String metricName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "metric_aggregation", length = 20)
    private MetricAggregation metricAggregation = MetricAggregation.AVG;
    
    // Last scaling events
    @Enumerated(EnumType.STRING)
    @Column(name = "last_scale_action", length = 20)
    private ScaleAction lastScaleAction;
    
    @Column(name = "last_scale_at")
    private LocalDateTime lastScaleAt;
    
    @Column(name = "scale_history_json", columnDefinition = "TEXT")
    private String scaleHistoryJson;
    
    // Timestamps
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    public enum PolicyType {
        CPU("CPU Usage", "cpu_usage_percent"),
        MEMORY("Memory Usage", "memory_usage_percent"),
        REQUEST_RATE("Request Rate", "requests_per_second"),
        RESPONSE_TIME("Response Time", "average_response_time_ms"),
        CUSTOM("Custom Metric", null);
        
        private final String displayName;
        private final String defaultMetricName;
        
        PolicyType(String displayName, String defaultMetricName) {
            this.displayName = displayName;
            this.defaultMetricName = defaultMetricName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDefaultMetricName() {
            return defaultMetricName;
        }
    }
    
    public enum MetricAggregation {
        AVG("Average"),
        MAX("Maximum"),
        MIN("Minimum"),
        SUM("Sum");
        
        private final String displayName;
        
        MetricAggregation(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public enum ScaleAction {
        UP("Scale Up"),
        DOWN("Scale Down");
        
        private final String displayName;
        
        ScaleAction(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // Helper methods
    public boolean canScaleUp() {
        return isActive && currentInstances < maxInstances && !isInCooldown();
    }
    
    public boolean canScaleDown() {
        return isActive && currentInstances > minInstances && !isInCooldown();
    }
    
    public boolean isInCooldown() {
        if (lastScaleAt == null || cooldownSeconds == null) {
            return false;
        }
        return lastScaleAt.plusSeconds(cooldownSeconds).isAfter(LocalDateTime.now());
    }
    
    public int getSecondsUntilCooldownEnds() {
        if (!isInCooldown()) {
            return 0;
        }
        LocalDateTime cooldownEnd = lastScaleAt.plusSeconds(cooldownSeconds);
        return (int) java.time.Duration.between(LocalDateTime.now(), cooldownEnd).getSeconds();
    }
    
    public void recordScaleAction(ScaleAction action, int newInstanceCount) {
        this.lastScaleAction = action;
        this.lastScaleAt = LocalDateTime.now();
        this.currentInstances = newInstanceCount;
    }
    
    public int calculateNewInstanceCount(boolean scaleUp) {
        if (scaleUp) {
            return Math.min(currentInstances + scaleUpIncrement, maxInstances);
        } else {
            return Math.max(currentInstances - scaleDownIncrement, minInstances);
        }
    }
    
    public boolean isAtMaxCapacity() {
        return currentInstances >= maxInstances;
    }
    
    public boolean isAtMinCapacity() {
        return currentInstances <= minInstances;
    }
}