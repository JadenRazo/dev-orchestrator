package com.devorchestrator.dto;

import com.devorchestrator.entity.EnvironmentStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class EnvironmentDto {
    
    private String id;
    private String name;
    private String templateId;
    private String templateName;
    private EnvironmentStatus status;
    private Long userId;
    private String username;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastAccessedAt;
    
    private List<ContainerDto> containers;
    private ResourceUsageDto resourceUsage;
    
    @Data
    @Builder
    public static class ContainerDto {
        private String id;
        private String serviceName;
        private String imageName;
        private String status;
        private Integer exposedPort;
        private String containerId;
        
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createdAt;
    }
    
    @Data
    @Builder
    public static class ResourceUsageDto {
        private Integer cpuLimit;
        private Long memoryLimit;
        private Double cpuUsage;
        private Long memoryUsage;
        private Integer containerCount;
    }
}