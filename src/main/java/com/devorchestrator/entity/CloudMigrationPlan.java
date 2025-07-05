package com.devorchestrator.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cloud_migration_plans")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CloudMigrationPlan {
    
    @Id
    @Column(length = 36)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectRegistration project;
    
    // Migration details
    @Enumerated(EnumType.STRING)
    @Column(name = "target_provider", nullable = false, length = 50)
    private InfrastructureProvider targetProvider;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "migration_strategy", nullable = false, length = 50)
    private MigrationStrategy migrationStrategy;
    
    // Resource mapping
    @Column(name = "instance_type_recommendations_json", columnDefinition = "TEXT")
    private String instanceTypeRecommendationsJson;
    
    @Column(name = "estimated_monthly_cost", precision = 10, scale = 2)
    private BigDecimal estimatedMonthlyCost;
    
    @Column(name = "cost_comparison_json", columnDefinition = "TEXT")
    private String costComparisonJson;
    
    // Migration configuration
    @Column(name = "terraform_config_json", columnDefinition = "TEXT")
    private String terraformConfigJson;
    
    @Column(name = "kubernetes_manifests_json", columnDefinition = "TEXT")
    private String kubernetesManifestsJson;
    
    @Column(name = "migration_steps_json", columnDefinition = "TEXT")
    private String migrationStepsJson;
    
    // Validation and testing
    @Column(name = "pre_migration_checks_json", columnDefinition = "TEXT")
    private String preMigrationChecksJson;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", length = 50)
    private ValidationStatus validationStatus;
    
    @Column(name = "validation_errors_json", columnDefinition = "TEXT")
    private String validationErrorsJson;
    
    // Migration state
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private MigrationStatus status;
    
    @Column(name = "current_step")
    private Integer currentStep = 0;
    
    @Column(name = "total_steps")
    private Integer totalSteps;
    
    // Rollback plan
    @Column(name = "rollback_plan_json", columnDefinition = "TEXT")
    private String rollbackPlanJson;
    
    @Column(name = "rollback_tested")
    private Boolean rollbackTested = false;
    
    // Timestamps
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "migration_started_at")
    private LocalDateTime migrationStartedAt;
    
    @Column(name = "migration_completed_at")
    private LocalDateTime migrationCompletedAt;
    
    public enum MigrationStrategy {
        LIFT_AND_SHIFT("Lift and Shift", "Direct migration with minimal changes"),
        REPLATFORM("Replatform", "Optimize for cloud with some modifications"),
        REFACTOR("Refactor", "Redesign for cloud-native architecture");
        
        private final String displayName;
        private final String description;
        
        MigrationStrategy(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    public enum MigrationStatus {
        DRAFT("Draft"),
        READY("Ready"),
        IN_PROGRESS("In Progress"),
        COMPLETED("Completed"),
        FAILED("Failed"),
        ROLLED_BACK("Rolled Back");
        
        private final String displayName;
        
        MigrationStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public enum ValidationStatus {
        NOT_STARTED("Not Started"),
        IN_PROGRESS("In Progress"),
        PASSED("Passed"),
        FAILED("Failed"),
        PARTIAL("Partial Success");
        
        private final String displayName;
        
        ValidationStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // Helper methods
    public boolean isReady() {
        return status == MigrationStatus.READY && 
               validationStatus == ValidationStatus.PASSED;
    }
    
    public boolean isInProgress() {
        return status == MigrationStatus.IN_PROGRESS;
    }
    
    public boolean isCompleted() {
        return status == MigrationStatus.COMPLETED;
    }
    
    public boolean canStart() {
        return status == MigrationStatus.READY && 
               validationStatus == ValidationStatus.PASSED &&
               rollbackTested != null && rollbackTested;
    }
    
    public BigDecimal getProgress() {
        if (totalSteps == null || totalSteps == 0) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(currentStep != null ? currentStep : 0)
            .divide(new BigDecimal(totalSteps), 2, BigDecimal.ROUND_HALF_UP)
            .multiply(new BigDecimal("100"));
    }
    
    public void incrementStep() {
        if (currentStep == null) {
            currentStep = 0;
        }
        currentStep++;
    }
}