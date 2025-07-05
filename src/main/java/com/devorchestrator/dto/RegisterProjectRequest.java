package com.devorchestrator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterProjectRequest {
    
    @NotBlank(message = "Project path is required")
    @Pattern(regexp = "^(/[^/]+)+/?$", message = "Invalid project path")
    private String path;
    
    private String name; // Optional, will use directory name if not provided
    
    private String vcsUrl; // Optional Git/SVN URL
    
    private String vcsBranch; // Optional branch name
    
    private Boolean autoAnalyze = true; // Whether to analyze immediately
}