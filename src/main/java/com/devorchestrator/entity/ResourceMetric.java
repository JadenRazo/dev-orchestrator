package com.devorchestrator.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "resource_metrics", indexes = {
    @Index(name = "idx_resource_metrics_project_time", columnList = "project_id, recorded_at DESC"),
    @Index(name = "idx_resource_metrics_type_time", columnList = "metric_type, recorded_at DESC"),
    @Index(name = "idx_resource_metrics_container", columnList = "container_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceMetric {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectRegistration project;
    
    @Column(name = "environment_id", length = 36)
    private String environmentId;
    
    @Column(name = "container_id", length = 64)
    private String containerId;
    
    // Metric type and source
    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 50)
    private MetricType metricType;
    
    @Column(name = "metric_name", nullable = false, length = 100)
    private String metricName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 50)
    private MetricSource source;
    
    // Metric values
    @Column(name = "value", nullable = false, precision = 20, scale = 4)
    private BigDecimal value;
    
    @Column(name = "unit", length = 20)
    private String unit;
    
    // Additional context
    @Column(name = "tags_json", columnDefinition = "TEXT")
    private String tagsJson;
    
    // Timestamp
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
    
    public enum MetricType {
        CPU,
        MEMORY,
        DISK,
        NETWORK,
        CUSTOM
    }
    
    public enum MetricSource {
        DOCKER,
        PROMETHEUS,
        APPLICATION,
        SYSTEM
    }
}