package com.devorchestrator.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class DetectedTechnology {
    
    private String name;
    
    private String version;
    
    private Double confidence;
    
    private DetectionSource source;
    
    private String detectionDetails;
    
    public enum DetectionSource {
        FILE_EXTENSION,
        CONFIG_FILE,
        DEPENDENCY_FILE,
        CODE_PATTERN,
        DIRECTORY_STRUCTURE,
        CONNECTION_STRING,
        ENVIRONMENT_VARIABLE,
        DOCKER_COMPOSE,
        USER_SPECIFIED
    }
    
    public boolean isHighConfidence() {
        return confidence != null && confidence >= 0.8;
    }
    
    public boolean isMediumConfidence() {
        return confidence != null && confidence >= 0.6 && confidence < 0.8;
    }
    
    public boolean isLowConfidence() {
        return confidence != null && confidence < 0.6;
    }
}