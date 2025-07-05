package com.devorchestrator.analyzer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetectedDependency {
    
    private String name;
    
    private String version;
    
    private String scope; // compile, runtime, test, dev
    
    private String packageManager; // npm, maven, pip, etc.
    
    private Boolean isDirect;
    
    private String purpose; // database driver, web framework, testing, etc.
    
    public static DetectedDependency of(String name, String version) {
        return DetectedDependency.builder()
            .name(name)
            .version(version)
            .isDirect(true)
            .build();
    }
}