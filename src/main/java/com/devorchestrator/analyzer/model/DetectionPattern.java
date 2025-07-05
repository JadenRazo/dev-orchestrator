package com.devorchestrator.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetectionPattern {
    
    private String technology;
    
    private String category; // language, framework, database, service
    
    @Builder.Default
    private List<String> filePatterns = new ArrayList<>();
    
    @Builder.Default
    private List<String> fileExtensions = new ArrayList<>();
    
    @Builder.Default
    private List<String> configFiles = new ArrayList<>();
    
    @Builder.Default
    private List<String> dependencies = new ArrayList<>();
    
    @Builder.Default
    private List<String> codePatterns = new ArrayList<>();
    
    @Builder.Default
    private List<String> directoryPatterns = new ArrayList<>();
    
    @Builder.Default
    private List<String> connectionPatterns = new ArrayList<>();
    
    @Builder.Default
    private List<String> environmentVariables = new ArrayList<>();
    
    private String dockerImage;
    
    private Integer defaultPort;
    
    private Double baseConfidence = 0.5;
    
    @Builder.Default
    private List<Pattern> compiledPatterns = new ArrayList<>();
    
    public void compilePatterns() {
        codePatterns.forEach(pattern -> 
            compiledPatterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE))
        );
        connectionPatterns.forEach(pattern -> 
            compiledPatterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE))
        );
    }
    
    public boolean matchesFile(String fileName) {
        for (String pattern : filePatterns) {
            if (fileName.matches(pattern)) {
                return true;
            }
        }
        for (String ext : fileExtensions) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return configFiles.contains(fileName);
    }
    
    public boolean matchesContent(String content) {
        for (Pattern pattern : compiledPatterns) {
            if (pattern.matcher(content).find()) {
                return true;
            }
        }
        return false;
    }
}