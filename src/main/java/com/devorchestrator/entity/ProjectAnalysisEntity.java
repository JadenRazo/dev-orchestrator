package com.devorchestrator.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.devorchestrator.analyzer.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "project_analyses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectAnalysisEntity {
    
    @Id
    @Column(name = "id", length = 36)
    private String id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectRegistration project;
    
    // Analysis data stored as JSON
    @Column(name = "languages_json", columnDefinition = "TEXT")
    private String languagesJson;
    
    @Column(name = "frameworks_json", columnDefinition = "TEXT")
    private String frameworksJson;
    
    @Column(name = "databases_json", columnDefinition = "TEXT")
    private String databasesJson;
    
    @Column(name = "services_json", columnDefinition = "TEXT")
    private String servicesJson;
    
    @Column(name = "dependencies_json", columnDefinition = "TEXT")
    private String dependenciesJson;
    
    @Column(name = "environment_variables_json", columnDefinition = "TEXT")
    private String environmentVariablesJson;
    
    // Summary data
    @Column(name = "primary_language", length = 50)
    private String primaryLanguage;
    
    @Column(name = "primary_framework", length = 100)
    private String primaryFramework;
    
    @Column(name = "database_count")
    private Integer databaseCount = 0;
    
    @Column(name = "service_count")
    private Integer serviceCount = 0;
    
    @Column(name = "dependency_count")
    private Integer dependencyCount = 0;
    
    // Resource requirements
    @Column(name = "estimated_memory_mb")
    private Integer estimatedMemoryMb;
    
    @Column(name = "estimated_cpu_cores", precision = 3, scale = 2)
    private BigDecimal estimatedCpuCores;
    
    @Column(name = "required_ports_json", columnDefinition = "TEXT")
    private String requiredPortsJson;
    
    // Analysis metadata
    @Column(name = "overall_confidence", precision = 3, scale = 2)
    private BigDecimal overallConfidence;
    
    @Column(name = "warnings_json", columnDefinition = "TEXT")
    private String warningsJson;
    
    @Column(name = "recommendations_json", columnDefinition = "TEXT")
    private String recommendationsJson;
    
    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;
    
    @Column(name = "analysis_duration_ms")
    private Long analysisDurationMs;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private AnalysisStatus status = AnalysisStatus.PENDING;
    
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
    
    public enum AnalysisStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
    
    // Transient helper methods to work with JSON data
    @Transient
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Transient
    public List<DetectedLanguage> getDetectedLanguages() {
        if (languagesJson == null) return new ArrayList<>();
        try {
            return objectMapper.readValue(languagesJson, new TypeReference<List<DetectedLanguage>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    @Transient
    public List<DetectedFramework> getDetectedFrameworks() {
        if (frameworksJson == null) return new ArrayList<>();
        try {
            return objectMapper.readValue(frameworksJson, new TypeReference<List<DetectedFramework>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    @Transient
    public List<DetectedDatabase> getDetectedDatabases() {
        if (databasesJson == null) return new ArrayList<>();
        try {
            return objectMapper.readValue(databasesJson, new TypeReference<List<DetectedDatabase>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    @Transient
    public List<DetectedService> getDetectedServices() {
        if (servicesJson == null) return new ArrayList<>();
        try {
            return objectMapper.readValue(servicesJson, new TypeReference<List<DetectedService>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}