package com.devorchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateGenerationResultDto {
    private String projectId;
    private String templateType;
    private String templateName;
    private String templateContent;
    private LocalDateTime generatedAt;
}