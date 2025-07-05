package com.devorchestrator.analyzer.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class DetectedService extends DetectedTechnology {
    
    private ServiceType type;
    
    private String dockerImage;
    
    private Integer defaultPort;
    
    private Map<String, String> environmentVariables = new HashMap<>();
    
    private String purpose;
    
    private Boolean isRequired;
    
    private String healthCheckUrl;
    
    public enum ServiceType {
        CACHE,
        MESSAGE_QUEUE,
        SEARCH_ENGINE,
        API_GATEWAY,
        REVERSE_PROXY,
        MONITORING,
        LOGGING,
        AUTHENTICATION,
        STORAGE,
        EMAIL,
        SCHEDULER,
        OTHER
    }
    
    public static DetectedService of(String name, ServiceType type, Double confidence) {
        return DetectedService.builder()
            .name(name)
            .type(type)
            .confidence(confidence)
            .build();
    }
}