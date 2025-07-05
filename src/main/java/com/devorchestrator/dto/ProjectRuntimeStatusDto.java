package com.devorchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectRuntimeStatusDto {
    private String projectId;
    private String projectName;
    private String projectStatus;
    private String environmentId;
    private String environmentStatus;
    private LocalDateTime startedAt;
    private List<Map<String, Object>> containers;
    private Map<String, Object> resourceUsage;
    private List<String> urls;
}