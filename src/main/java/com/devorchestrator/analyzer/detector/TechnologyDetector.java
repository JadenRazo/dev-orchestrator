package com.devorchestrator.analyzer.detector;

import com.devorchestrator.analyzer.model.ProjectAnalysis;

import java.nio.file.Path;

public interface TechnologyDetector {
    
    /**
     * Analyzes the project directory and detects relevant technologies
     * @param projectPath The root path of the project to analyze
     * @param analysis The analysis object to update with findings
     */
    void detect(Path projectPath, ProjectAnalysis analysis);
    
    /**
     * Returns the priority order of this detector (higher = runs first)
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * Returns the name of this detector for logging
     */
    String getName();
}