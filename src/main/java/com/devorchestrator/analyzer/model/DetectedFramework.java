package com.devorchestrator.analyzer.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class DetectedFramework extends DetectedTechnology {
    
    private String category; // web, mobile, desktop, game, ml, etc.
    
    private String language;
    
    private List<String> configFiles = new ArrayList<>();
    
    private List<String> dependencies = new ArrayList<>();
    
    private Boolean requiresBuildStep;
    
    private String buildCommand;
    
    private String startCommand;
    
    private Integer defaultPort;
    
    public static DetectedFramework of(String name, String version, Double confidence) {
        return DetectedFramework.builder()
            .name(name)
            .version(version)
            .confidence(confidence)
            .build();
    }
}