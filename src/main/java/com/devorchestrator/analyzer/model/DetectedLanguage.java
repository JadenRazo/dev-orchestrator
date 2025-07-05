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
public class DetectedLanguage extends DetectedTechnology {
    
    private String runtime;
    
    private List<String> fileExtensions = new ArrayList<>();
    
    private Integer filesCount;
    
    private Long totalLinesOfCode;
    
    private Boolean isPrimary;
    
    private String packageManager;
    
    public static DetectedLanguage of(String name, String version, Double confidence) {
        return DetectedLanguage.builder()
            .name(name)
            .version(version)
            .confidence(confidence)
            .build();
    }
}