package com.devorchestrator.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cloud_cost_tracking")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CloudCostTracking {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectRegistration project;
    
    // Cost details
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private InfrastructureProvider provider;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 100)
    private ServiceType serviceType;
    
    @Column(name = "resource_id", length = 255)
    private String resourceId;
    
    // Cost data
    @Column(name = "cost_amount", nullable = false, precision = 10, scale = 4)
    private BigDecimal costAmount;
    
    @Column(name = "currency", length = 3)
    private String currency = "USD";
    
    @Column(name = "usage_quantity", precision = 20, scale = 4)
    private BigDecimal usageQuantity;
    
    @Column(name = "usage_unit", length = 50)
    private String usageUnit;
    
    // Time period
    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;
    
    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;
    
    // Metadata
    @Column(name = "tags_json", columnDefinition = "TEXT")
    private String tagsJson;
    
    @CreatedDate
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;
    
    public enum ServiceType {
        COMPUTE("Compute", "Virtual machines and containers"),
        STORAGE("Storage", "Object and block storage"),
        NETWORK("Network", "Data transfer and load balancing"),
        DATABASE("Database", "Managed database services"),
        CONTAINER("Container", "Container orchestration services"),
        SERVERLESS("Serverless", "Function as a service"),
        ML("Machine Learning", "AI/ML services"),
        MONITORING("Monitoring", "Logging and monitoring"),
        SECURITY("Security", "Security and compliance services"),
        OTHER("Other", "Other services");
        
        private final String displayName;
        private final String description;
        
        ServiceType(String displayName, String description) {
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
    
    // Helper methods
    public BigDecimal getCostPerUnit() {
        if (usageQuantity == null || usageQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return costAmount.divide(usageQuantity, 4, BigDecimal.ROUND_HALF_UP);
    }
    
    public BigDecimal getHourlyRate() {
        long hours = java.time.Duration.between(periodStart, periodEnd).toHours();
        if (hours == 0) {
            return BigDecimal.ZERO;
        }
        return costAmount.divide(new BigDecimal(hours), 4, BigDecimal.ROUND_HALF_UP);
    }
    
    public BigDecimal getDailyRate() {
        return getHourlyRate().multiply(new BigDecimal("24"));
    }
    
    public BigDecimal getMonthlyProjection() {
        return getDailyRate().multiply(new BigDecimal("30"));
    }
    
    public boolean isHighCost() {
        return getMonthlyProjection().compareTo(new BigDecimal("1000")) > 0;
    }
    
    public String getProviderServiceKey() {
        return provider.name() + "_" + serviceType.name();
    }
}