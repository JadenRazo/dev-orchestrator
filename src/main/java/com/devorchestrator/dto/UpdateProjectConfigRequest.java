package com.devorchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProjectConfigRequest {
    
    private Boolean autoStart;
    
    private Boolean autoScaleEnabled;
    
    private Boolean monitoringEnabled;
    
    private String description;
}