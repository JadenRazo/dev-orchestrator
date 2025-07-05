package com.devorchestrator.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectAnalysis {
    
    private String projectPath;
    
    private String projectName;
    
    private LocalDateTime analyzedAt;
    
    @Builder.Default
    private List<DetectedLanguage> languages = new ArrayList<>();
    
    @Builder.Default
    private List<DetectedFramework> frameworks = new ArrayList<>();
    
    @Builder.Default
    private List<DetectedDatabase> databases = new ArrayList<>();
    
    @Builder.Default
    private List<DetectedService> services = new ArrayList<>();
    
    @Builder.Default
    private Map<String, List<DetectedDependency>> dependencies = new HashMap<>();
    
    @Builder.Default
    private List<String> buildTools = new ArrayList<>();
    
    @Builder.Default
    private Map<String, String> environmentVariables = new HashMap<>();
    
    @Builder.Default
    private List<Integer> exposedPorts = new ArrayList<>();
    
    @Builder.Default
    private ResourceRequirements resourceRequirements = new ResourceRequirements();
    
    @Builder.Default
    private List<AnalysisWarning> warnings = new ArrayList<>();
    
    @Builder.Default
    private List<String> recommendations = new ArrayList<>();
    
    private Double overallConfidence;
    
    public void addLanguage(DetectedLanguage language) {
        this.languages.add(language);
    }
    
    public void addFramework(DetectedFramework framework) {
        this.frameworks.add(framework);
    }
    
    public void addDatabase(DetectedDatabase database) {
        this.databases.add(database);
    }
    
    public void addService(DetectedService service) {
        this.services.add(service);
    }
    
    public void addWarning(String category, String message) {
        this.warnings.add(new AnalysisWarning(category, message));
    }
    
    public void addRecommendation(String recommendation) {
        this.recommendations.add(recommendation);
    }
    
    public void calculateOverallConfidence() {
        List<Double> confidences = new ArrayList<>();
        
        languages.forEach(l -> confidences.add(l.getConfidence()));
        frameworks.forEach(f -> confidences.add(f.getConfidence()));
        databases.forEach(d -> confidences.add(d.getConfidence()));
        services.forEach(s -> confidences.add(s.getConfidence()));
        
        if (!confidences.isEmpty()) {
            this.overallConfidence = confidences.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        }
    }
    
    @Data
    @AllArgsConstructor
    public static class AnalysisWarning {
        private String category;
        private String message;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceRequirements {
        @Builder.Default
        private Integer estimatedMemoryMb = 512;
        
        @Builder.Default
        private Double estimatedCpuCores = 0.5;
        
        @Builder.Default
        private Integer estimatedDiskGb = 1;
    }
}