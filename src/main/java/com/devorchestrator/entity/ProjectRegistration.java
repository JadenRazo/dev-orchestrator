package com.devorchestrator.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_registrations")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectRegistration {
    
    @Id
    @Column(name = "id", length = 36)
    private String id;
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "path", nullable = false, length = 1000)
    private String path;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "project_type", length = 50)
    private String projectType;
    
    @Column(name = "vcs_url", length = 500)
    private String vcsUrl;
    
    @Column(name = "vcs_branch", length = 100)
    private String vcsBranch;
    
    // Analysis results
    @Column(name = "analysis_id", length = 36)
    private String analysisId;
    
    @Column(name = "last_analyzed_at")
    private LocalDateTime lastAnalyzedAt;
    
    @Column(name = "analysis_confidence", precision = 3, scale = 2)
    private BigDecimal analysisConfidence;
    
    // Project status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProjectStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "health_status")
    private HealthStatus healthStatus;
    
    // Configuration
    @Column(name = "auto_start")
    private Boolean autoStart = false;
    
    @Column(name = "auto_scale_enabled")
    private Boolean autoScaleEnabled = false;
    
    @Column(name = "monitoring_enabled")
    private Boolean monitoringEnabled = true;
    
    // Timestamps
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;
    
    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id")
    private Environment environment;
    
    @OneToOne(mappedBy = "project", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private ProjectAnalysisEntity latestAnalysis;
    
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = ProjectStatus.INACTIVE;
        }
    }
    
    public enum ProjectStatus {
        ACTIVE,
        INACTIVE,
        ANALYZING,
        ERROR
    }
    
    public enum HealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY
    }
}