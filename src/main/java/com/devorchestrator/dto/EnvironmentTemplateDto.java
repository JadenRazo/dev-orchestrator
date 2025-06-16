package com.devorchestrator.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class EnvironmentTemplateDto {
    
    private String id;
    private String name;
    private String description;
    private String category;
    private String version;
    private boolean isActive;
    
    private Integer cpuLimit;
    private Long memoryLimit;
    private Integer diskLimit;
    
    private Map<String, Object> configuration;
    private Map<String, ServiceConfig> services;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    @Data
    @Builder
    public static class ServiceConfig {
        private String image;
        private String[] ports;
        private Map<String, String> environment;
        private String[] volumes;
        private String[] dependsOn;
        private HealthCheck healthCheck;
        
        @Data
        @Builder
        public static class HealthCheck {
            private String test;
            private String interval;
            private String timeout;
            private Integer retries;
            private String startPeriod;
        }
    }
}