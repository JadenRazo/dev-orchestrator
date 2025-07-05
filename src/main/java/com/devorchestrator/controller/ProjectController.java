package com.devorchestrator.controller;

import com.devorchestrator.dto.*;
import com.devorchestrator.entity.*;
import com.devorchestrator.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Project Management", description = "Endpoints for managing and monitoring projects")
@Slf4j
@PreAuthorize("isAuthenticated()")
public class ProjectController {
    
    private final ProjectRegistryService projectRegistryService;
    private final ProjectAnalyzerService analyzerService;
    private final MetricsCollectorService metricsCollectorService;
    private final ResourceMetricRepository metricRepository;
    private final ProjectStartupService projectStartupService;
    private final TemplateGeneratorService templateGeneratorService;
    private final UsageAnalyticsService usageAnalyticsService;
    private final UsageReportRepository usageReportRepository;
    private final CloudCostEstimatorService cloudCostEstimatorService;
    
    public ProjectController(ProjectRegistryService projectRegistryService,
                           ProjectAnalyzerService analyzerService,
                           MetricsCollectorService metricsCollectorService,
                           ResourceMetricRepository metricRepository,
                           ProjectStartupService projectStartupService,
                           TemplateGeneratorService templateGeneratorService,
                           UsageAnalyticsService usageAnalyticsService,
                           UsageReportRepository usageReportRepository,
                           CloudCostEstimatorService cloudCostEstimatorService) {
        this.projectRegistryService = projectRegistryService;
        this.analyzerService = analyzerService;
        this.metricsCollectorService = metricsCollectorService;
        this.metricRepository = metricRepository;
        this.projectStartupService = projectStartupService;
        this.templateGeneratorService = templateGeneratorService;
        this.usageAnalyticsService = usageAnalyticsService;
        this.usageReportRepository = usageReportRepository;
        this.cloudCostEstimatorService = cloudCostEstimatorService;
    }
    
    @PostMapping("/register")
    @Operation(summary = "Register a new project", description = "Registers a project for management and triggers initial analysis")
    public ResponseEntity<ProjectRegistrationDto> registerProject(
            @Valid @RequestBody RegisterProjectRequest request,
            @AuthenticationPrincipal User user) {
        
        log.info("Registering project: {} at path: {}", request.getName(), request.getPath());
        
        ProjectRegistration project = projectRegistryService.registerProject(
            request.getPath(),
            request.getName(),
            user
        );
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ProjectMapper.toDto(project));
    }
    
    @GetMapping
    @Operation(summary = "List user projects", description = "Returns paginated list of user's registered projects")
    public ResponseEntity<Page<ProjectRegistrationDto>> listProjects(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal User user) {
        
        Page<ProjectRegistration> projects = projectRegistryService.getUserProjects(user, pageable);
        
        return ResponseEntity.ok(projects.map(ProjectMapper::toDto));
    }
    
    @GetMapping("/{projectId}")
    @Operation(summary = "Get project details", description = "Returns detailed information about a specific project")
    public ResponseEntity<ProjectDetailDto> getProject(
            @PathVariable String projectId,
            @AuthenticationPrincipal User user) {
        
        ProjectRegistration project = projectRegistryService.getProjectById(projectId, user);
        
        // Get latest metrics if project is active
        List<ResourceMetric> latestMetrics = null;
        if (project.getStatus() == ProjectRegistration.ProjectStatus.ACTIVE) {
            latestMetrics = metricRepository.getLatestMetricsForProject(projectId);
        }
        
        return ResponseEntity.ok(ProjectMapper.toDetailDto(project, latestMetrics));
    }
    
    @PostMapping("/{projectId}/analyze")
    @Operation(summary = "Analyze project", description = "Triggers a new analysis of the project")
    public ResponseEntity<AnalysisResultDto> analyzeProject(
            @PathVariable String projectId,
            @AuthenticationPrincipal User user) {
        
        ProjectRegistration project = projectRegistryService.getProjectById(projectId, user);
        
        CompletableFuture<ProjectAnalysisEntity> futureAnalysis = 
            projectRegistryService.analyzeProjectAsync(project);
        
        return ResponseEntity.accepted()
            .body(AnalysisResultDto.builder()
                .projectId(projectId)
                .status("ANALYZING")
                .message("Analysis started. Check back for results.")
                .build());
    }
    
    @PostMapping("/{projectId}/start")
    @Operation(summary = "Start project", description = "Creates environment and starts project containers")
    public ResponseEntity<ProjectRegistrationDto> startProject(
            @PathVariable String projectId,
            @AuthenticationPrincipal User user) {
        
        log.info("Starting project: {}", projectId);
        
        ProjectRegistration project = projectRegistryService.startProject(projectId, user);
        
        return ResponseEntity.ok(ProjectMapper.toDto(project));
    }
    
    @PostMapping("/{projectId}/stop")
    @Operation(summary = "Stop project", description = "Stops project containers and releases resources")
    public ResponseEntity<ProjectRegistrationDto> stopProject(
            @PathVariable String projectId,
            @AuthenticationPrincipal User user) {
        
        log.info("Stopping project: {}", projectId);
        
        ProjectRegistration project = projectRegistryService.stopProject(projectId, user);
        
        return ResponseEntity.ok(ProjectMapper.toDto(project));
    }
    
    @PatchMapping("/{projectId}/config")
    @Operation(summary = "Update project configuration", description = "Updates project settings like auto-start and monitoring")
    public ResponseEntity<ProjectRegistrationDto> updateProjectConfig(
            @PathVariable String projectId,
            @Valid @RequestBody UpdateProjectConfigRequest request,
            @AuthenticationPrincipal User user) {
        
        ProjectRegistration project = projectRegistryService.updateProjectConfig(
            projectId, user,
            request.getAutoStart(),
            request.getAutoScaleEnabled(),
            request.getMonitoringEnabled()
        );
        
        return ResponseEntity.ok(ProjectMapper.toDto(project));
    }
    
    @GetMapping("/{projectId}/metrics")
    @Operation(summary = "Get project metrics", description = "Returns resource metrics for a project")
    public ResponseEntity<ProjectMetricsDto> getProjectMetrics(
            @PathVariable String projectId,
            @RequestParam(required = false) @Parameter(description = "Start time for metrics") LocalDateTime start,
            @RequestParam(required = false) @Parameter(description = "End time for metrics") LocalDateTime end,
            @RequestParam(required = false) @Parameter(description = "Metric type filter") ResourceMetric.MetricType type,
            @PageableDefault(size = 100) Pageable pageable,
            @AuthenticationPrincipal User user) {
        
        ProjectRegistration project = projectRegistryService.getProjectById(projectId, user);
        
        if (start == null) start = LocalDateTime.now().minusHours(1);
        if (end == null) end = LocalDateTime.now();
        
        Page<ResourceMetric> metrics;
        if (type != null) {
            metrics = Page.empty(); // Would implement type-specific query
        } else {
            metrics = metricRepository.findByProjectIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                projectId, start, end, pageable
            );
        }
        
        // Calculate aggregates
        Double avgCpu = metricRepository.getAverageMetricValue(projectId, "cpu_usage_percent", start, end);
        Double maxCpu = metricRepository.getMaxMetricValue(projectId, "cpu_usage_percent", start, end);
        Double avgMemory = metricRepository.getAverageMetricValue(projectId, "memory_used_mb", start, end);
        Double maxMemory = metricRepository.getMaxMetricValue(projectId, "memory_used_mb", start, end);
        
        return ResponseEntity.ok(ProjectMetricsDto.builder()
            .projectId(projectId)
            .startTime(start)
            .endTime(end)
            .metrics(metrics.map(MetricMapper::toDto))
            .aggregates(MetricAggregates.builder()
                .avgCpuPercent(avgCpu)
                .maxCpuPercent(maxCpu)
                .avgMemoryMb(avgMemory)
                .maxMemoryMb(maxMemory)
                .build())
            .build());
    }
    
    @GetMapping("/{projectId}/analysis")
    @Operation(summary = "Get project analysis", description = "Returns the latest analysis results for a project")
    public ResponseEntity<ProjectAnalysisDto> getProjectAnalysis(
            @PathVariable String projectId,
            @AuthenticationPrincipal User user) {
        
        ProjectRegistration project = projectRegistryService.getProjectById(projectId, user);
        
        if (project.getAnalysisId() == null) {
            return ResponseEntity.noContent().build();
        }
        
        // Would fetch and convert analysis entity
        return ResponseEntity.ok(new ProjectAnalysisDto()); // Placeholder
    }
    
    @DeleteMapping("/{projectId}")
    @Operation(summary = "Delete project", description = "Removes project registration and all associated data")
    public ResponseEntity<Void> deleteProject(
            @PathVariable String projectId,
            @AuthenticationPrincipal User user) {
        
        log.info("Deleting project: {}", projectId);
        
        projectRegistryService.deleteProject(projectId, user);
        
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/active")
    @Operation(summary = "Get active projects", description = "Returns list of currently running projects")
    public ResponseEntity<List<ProjectRegistrationDto>> getActiveProjects(
            @AuthenticationPrincipal User user) {
        
        List<ProjectRegistration> activeProjects = projectRegistryService.getActiveProjects(user);
        
        return ResponseEntity.ok(
            activeProjects.stream()
                .map(ProjectMapper::toDto)
                .collect(Collectors.toList())
        );
    }
    
    @PostMapping("/{projectId}/metrics/collect")
    @Operation(summary = "Trigger metrics collection", description = "Manually triggers metrics collection for a project")
    public ResponseEntity<Void> collectMetrics(
            @PathVariable String projectId,
            @AuthenticationPrincipal User user) {
        
        ProjectRegistration project = projectRegistryService.getProjectById(projectId, user);
        
        if (project.getStatus() != ProjectRegistration.ProjectStatus.ACTIVE) {
            return ResponseEntity.badRequest().build();
        }
        
        metricsCollectorService.collectProjectMetrics(project);
        
        return ResponseEntity.accepted().build();
    }
    
    @PostMapping("/{projectId}/start")
    @Operation(summary = "Start project environment", description = "Launches Docker containers for the project")
    public ResponseEntity<ProjectStatusDto> startProject(
            @PathVariable String projectId,
            @AuthenticationPrincipal User user) {
        
        log.info("Starting project {} for user {}", projectId, user.getId());
        
        // Verify project ownership
        ProjectRegistration project = projectRegistryService.getProjectById(projectId, user);
        
        // Start project asynchronously
        projectStartupService.startProject(projectId, user.getId());
        
        // Return immediate response with starting status
        return ResponseEntity.ok(ProjectStatusDto.builder()
            .projectId(projectId)
            .status("STARTING")
            .message("Project startup initiated")
            .build());
    }
    
    @PostMapping("/{projectId}/stop")
    @Operation(summary = "Stop project environment", description = "Stops running Docker containers for the project")
    public ResponseEntity<ProjectStatusDto> stopProject(
            @PathVariable String projectId,
            @AuthenticationPrincipal User user) {
        
        log.info("Stopping project {} for user {}", projectId, user.getId());
        
        // Verify project ownership
        ProjectRegistration project = projectRegistryService.getProjectById(projectId, user);
        
        // Stop project asynchronously
        projectStartupService.stopProject(projectId, user.getId());
        
        // Return immediate response with stopping status
        return ResponseEntity.ok(ProjectStatusDto.builder()
            .projectId(projectId)
            .status("STOPPING")
            .message("Project shutdown initiated")
            .build());
    }
    
    @PostMapping("/{projectId}/restart")
    @Operation(summary = "Restart project environment", description = "Restarts Docker containers for the project")
    public ResponseEntity<ProjectStatusDto> restartProject(
            @PathVariable String projectId,
            @AuthenticationPrincipal User user) {
        
        log.info("Restarting project {} for user {}", projectId, user.getId());
        
        // Verify project ownership
        ProjectRegistration project = projectRegistryService.getProjectById(projectId, user);
        
        // Restart project asynchronously
        projectStartupService.restartProject(projectId, user.getId());
        
        // Return immediate response with restarting status
        return ResponseEntity.ok(ProjectStatusDto.builder()
            .projectId(projectId)
            .status("RESTARTING")
            .message("Project restart initiated")
            .build());
    }
    
    @GetMapping("/{projectId}/status")
    @Operation(summary = "Get project runtime status", description = "Returns current status of project environment and containers")
    public ResponseEntity<ProjectRuntimeStatusDto> getProjectStatus(
            @PathVariable String projectId,
            @AuthenticationPrincipal User user) {
        
        // Verify project ownership
        ProjectRegistration project = projectRegistryService.getProjectById(projectId, user);
        
        // Get runtime status
        Map<String, Object> status = projectStartupService.getProjectStatus(projectId);
        
        // Convert to DTO
        ProjectRuntimeStatusDto statusDto = ProjectRuntimeStatusDto.builder()
            .projectId(projectId)
            .projectName((String) status.get("projectName"))
            .projectStatus((String) status.get("projectStatus"))
            .environmentId((String) status.get("environmentId"))
            .environmentStatus((String) status.get("environmentStatus"))
            .startedAt((LocalDateTime) status.get("startedAt"))
            .containers((List<Map<String, Object>>) status.get("containers"))
            .resourceUsage((Map<String, Object>) status.get("resourceUsage"))
            .urls((List<String>) status.get("urls"))
            .build();
        
        return ResponseEntity.ok(statusDto);
    }
    
    @PostMapping("/{projectId}/generate-template")
    @Operation(summary = "Generate deployment template", description = "Generates Docker Compose template from project analysis")
    public ResponseEntity<TemplateGenerationResultDto> generateTemplate(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "DOCKER") @Parameter(description = "Template type to generate") String templateType,
            @AuthenticationPrincipal User user) {
        
        log.info("Generating {} template for project {}", templateType, projectId);
        
        // Verify project ownership
        ProjectRegistration project = projectRegistryService.getProjectById(projectId, user);
        
        // Get latest analysis
        if (project.getAnalysisId() == null) {
            return ResponseEntity.badRequest().build();
        }
        
        // Generate template based on type
        String templateContent;
        String templateName;
        
        switch (templateType.toUpperCase()) {
            case "KUBERNETES":
                templateContent = templateGeneratorService.generateKubernetesManifests(projectId, project.getAnalysisId());
                templateName = "kubernetes-manifests.yaml";
                break;
            case "TERRAFORM_AWS":
                templateContent = templateGeneratorService.generateTerraformConfiguration(
                    projectId, project.getAnalysisId(), InfrastructureProvider.AWS);
                templateName = "main.tf";
                break;
            case "DOCKER":
            default:
                EnvironmentTemplate dockerTemplate = templateGeneratorService.generateDockerComposeTemplate(
                    projectId, project.getAnalysisId());
                templateContent = dockerTemplate.getDockerComposeContent();
                templateName = "docker-compose.yml";
                break;
        }
        
        return ResponseEntity.ok(TemplateGenerationResultDto.builder()
            .projectId(projectId)
            .templateType(templateType)
            .templateName(templateName)
            .templateContent(templateContent)
            .generatedAt(LocalDateTime.now())
            .build());
    }
    
    @GetMapping("/{projectId}/usage-report")
    @Operation(summary = "Get usage report", description = "Returns resource usage analytics and cost estimates")
    public ResponseEntity<UsageReportDto> getUsageReport(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "7") @Parameter(description = "Number of days to include") int days,
            @AuthenticationPrincipal User user) {
        
        // Verify project ownership
        ProjectRegistration project = projectRegistryService.getProjectById(projectId, user);
        
        // Get usage summary
        Map<String, Object> summary = usageAnalyticsService.getUsageSummary(projectId, days);
        
        // Get latest usage report
        Optional<UsageReport> latestReport = usageReportRepository.findTopByProjectIdOrderByPeriodEndDesc(projectId);
        
        UsageReportDto reportDto = UsageReportDto.builder()
            .projectId(projectId)
            .periodDays(days)
            .totalCost((Double) summary.get("totalCost"))
            .avgCpuHours((BigDecimal) summary.get("avgCpuHours"))
            .avgMemoryGbHours((BigDecimal) summary.get("avgMemoryGbHours"))
            .avgNetworkGb((BigDecimal) summary.get("avgNetworkGb"))
            .peakCpuPercent((BigDecimal) summary.get("peakCpuPercent"))
            .peakMemoryMb((Integer) summary.get("peakMemoryMb"))
            .availabilityPercent((BigDecimal) summary.get("availabilityPercent"))
            .build();
        
        if (latestReport.isPresent()) {
            UsageReport report = latestReport.get();
            reportDto.setLastReportDate(report.getPeriodEnd());
            reportDto.setErrorRate(report.getErrorRate());
        }
        
        return ResponseEntity.ok(reportDto);
    }
    
    @PostMapping("/{projectId}/estimate-cloud-cost")
    @Operation(summary = "Estimate cloud migration cost", description = "Calculates estimated costs for migrating to cloud providers")
    public ResponseEntity<CloudCostEstimateDto> estimateCloudCost(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "AWS") @Parameter(description = "Target cloud provider") InfrastructureProvider provider,
            @RequestParam(defaultValue = "LIFT_AND_SHIFT") @Parameter(description = "Migration strategy") CloudMigrationPlan.MigrationStrategy strategy,
            @AuthenticationPrincipal User user) {
        
        // Verify project ownership
        ProjectRegistration project = projectRegistryService.getProjectById(projectId, user);
        
        // Generate cost estimate
        CloudMigrationPlan estimate = cloudCostEstimatorService.estimateMigrationCosts(
            projectId, provider, strategy);
        
        return ResponseEntity.ok(CloudCostEstimateDto.builder()
            .projectId(projectId)
            .provider(provider.name())
            .strategy(strategy.name())
            .estimatedMonthlyCost(estimate.getEstimatedMonthlyCost())
            .costComparisonJson(estimate.getCostComparisonJson())
            .instanceRecommendationsJson(estimate.getInstanceTypeRecommendationsJson())
            .migrationStepsJson(estimate.getMigrationStepsJson())
            .build());
    }
}

// Mapper utility class
class ProjectMapper {
    static ProjectRegistrationDto toDto(ProjectRegistration project) {
        return ProjectRegistrationDto.builder()
            .id(project.getId())
            .name(project.getName())
            .path(project.getPath())
            .projectType(project.getProjectType())
            .status(project.getStatus().name())
            .healthStatus(project.getHealthStatus() != null ? project.getHealthStatus().name() : null)
            .autoStart(project.getAutoStart())
            .autoScaleEnabled(project.getAutoScaleEnabled())
            .monitoringEnabled(project.getMonitoringEnabled())
            .lastAnalyzedAt(project.getLastAnalyzedAt())
            .createdAt(project.getCreatedAt())
            .updatedAt(project.getUpdatedAt())
            .build();
    }
    
    static ProjectDetailDto toDetailDto(ProjectRegistration project, List<ResourceMetric> latestMetrics) {
        ProjectDetailDto dto = new ProjectDetailDto();
        // Copy basic fields from toDto
        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setPath(project.getPath());
        dto.setStatus(project.getStatus().name());
        
        // Add additional details
        if (project.getEnvironment() != null) {
            dto.setEnvironmentId(project.getEnvironment().getId());
        }
        
        // Add latest metrics if available
        if (latestMetrics != null) {
            dto.setLatestMetrics(
                latestMetrics.stream()
                    .map(MetricMapper::toDto)
                    .collect(Collectors.toList())
            );
        }
        
        return dto;
    }
}

// Metric mapper
class MetricMapper {
    static MetricDto toDto(ResourceMetric metric) {
        return MetricDto.builder()
            .metricType(metric.getMetricType().name())
            .metricName(metric.getMetricName())
            .value(metric.getValue())
            .unit(metric.getUnit())
            .recordedAt(metric.getRecordedAt())
            .build();
    }
}