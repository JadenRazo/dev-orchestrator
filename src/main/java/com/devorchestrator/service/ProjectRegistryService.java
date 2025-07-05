package com.devorchestrator.service;

import com.devorchestrator.analyzer.ProjectAnalyzerService;
import com.devorchestrator.analyzer.model.ProjectAnalysis;
import com.devorchestrator.entity.*;
import com.devorchestrator.exception.DevOrchestratorException;
import com.devorchestrator.repository.ProjectAnalysisRepository;
import com.devorchestrator.repository.ProjectRegistrationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class ProjectRegistryService {
    
    private final ProjectRegistrationRepository projectRepository;
    private final ProjectAnalysisRepository analysisRepository;
    private final ProjectAnalyzerService analyzerService;
    private final EnvironmentService environmentService;
    private final MetricsCollectorService metricsCollectorService;
    private final ObjectMapper objectMapper;
    
    public ProjectRegistryService(ProjectRegistrationRepository projectRepository,
                                ProjectAnalysisRepository analysisRepository,
                                ProjectAnalyzerService analyzerService,
                                EnvironmentService environmentService,
                                MetricsCollectorService metricsCollectorService,
                                ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.analysisRepository = analysisRepository;
        this.analyzerService = analyzerService;
        this.environmentService = environmentService;
        this.metricsCollectorService = metricsCollectorService;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Registers a new project for management
     */
    public ProjectRegistration registerProject(String projectPath, String name, User user) {
        Path path = Paths.get(projectPath);
        
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new DevOrchestratorException("Invalid project path: " + projectPath);
        }
        
        // Check if project already registered
        Optional<ProjectRegistration> existing = projectRepository.findByPathAndUserId(projectPath, user.getId());
        if (existing.isPresent()) {
            log.info("Project already registered: {}", projectPath);
            return existing.get();
        }
        
        // Create new registration
        ProjectRegistration project = ProjectRegistration.builder()
            .id(UUID.randomUUID().toString())
            .name(name != null ? name : path.getFileName().toString())
            .path(projectPath)
            .user(user)
            .status(ProjectRegistration.ProjectStatus.INACTIVE)
            .monitoringEnabled(true)
            .build();
        
        project = projectRepository.save(project);
        
        log.info("Registered new project: {} at {}", project.getName(), projectPath);
        
        // Trigger initial analysis asynchronously
        analyzeProjectAsync(project);
        
        return project;
    }
    
    /**
     * Analyzes a registered project
     */
    @Async
    public CompletableFuture<ProjectAnalysisEntity> analyzeProjectAsync(ProjectRegistration project) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return analyzeProject(project);
            } catch (Exception e) {
                log.error("Error analyzing project {}: {}", project.getName(), e.getMessage());
                project.setStatus(ProjectRegistration.ProjectStatus.ERROR);
                projectRepository.save(project);
                throw new DevOrchestratorException("Project analysis failed", e);
            }
        });
    }
    
    /**
     * Performs project analysis and stores results
     */
    public ProjectAnalysisEntity analyzeProject(ProjectRegistration project) {
        log.info("Starting analysis for project: {}", project.getName());
        
        // Update project status
        project.setStatus(ProjectRegistration.ProjectStatus.ANALYZING);
        projectRepository.save(project);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Run the analyzer
            ProjectAnalysis analysis = analyzerService.analyzeProject(project.getPath());
            
            // Convert to entity
            ProjectAnalysisEntity analysisEntity = convertAnalysisToEntity(analysis, project);
            analysisEntity.setAnalysisDurationMs(System.currentTimeMillis() - startTime);
            
            // Save analysis
            analysisEntity = analysisRepository.save(analysisEntity);
            
            // Update project with analysis results
            project.setAnalysisId(analysisEntity.getId());
            project.setLastAnalyzedAt(LocalDateTime.now());
            project.setAnalysisConfidence(analysisEntity.getOverallConfidence());
            project.setStatus(ProjectRegistration.ProjectStatus.INACTIVE);
            
            // Determine project type based on analysis
            project.setProjectType(determineProjectType(analysis));
            
            projectRepository.save(project);
            
            log.info("Completed analysis for project: {} with confidence: {}", 
                project.getName(), analysisEntity.getOverallConfidence());
            
            return analysisEntity;
            
        } catch (Exception e) {
            log.error("Error during project analysis", e);
            project.setStatus(ProjectRegistration.ProjectStatus.ERROR);
            projectRepository.save(project);
            throw new DevOrchestratorException("Failed to analyze project", e);
        }
    }
    
    /**
     * Converts ProjectAnalysis to entity for persistence
     */
    private ProjectAnalysisEntity convertAnalysisToEntity(ProjectAnalysis analysis, ProjectRegistration project) {
        try {
            ProjectAnalysisEntity entity = ProjectAnalysisEntity.builder()
                .id(UUID.randomUUID().toString())
                .project(project)
                .analyzedAt(analysis.getAnalyzedAt())
                .build();
            
            // Serialize complex objects to JSON
            entity.setLanguagesJson(objectMapper.writeValueAsString(analysis.getLanguages()));
            entity.setFrameworksJson(objectMapper.writeValueAsString(analysis.getFrameworks()));
            entity.setDatabasesJson(objectMapper.writeValueAsString(analysis.getDatabases()));
            entity.setServicesJson(objectMapper.writeValueAsString(analysis.getServices()));
            entity.setDependenciesJson(objectMapper.writeValueAsString(analysis.getDependencies()));
            entity.setEnvironmentVariablesJson(objectMapper.writeValueAsString(analysis.getEnvironmentVariables()));
            entity.setWarningsJson(objectMapper.writeValueAsString(analysis.getWarnings()));
            entity.setRecommendationsJson(objectMapper.writeValueAsString(analysis.getRecommendations()));
            entity.setRequiredPortsJson(objectMapper.writeValueAsString(
                analysis.getResourceRequirements().getRequiredPorts()));
            
            // Set summary data
            if (!analysis.getLanguages().isEmpty()) {
                entity.setPrimaryLanguage(analysis.getLanguages().get(0).getName());
            }
            if (!analysis.getFrameworks().isEmpty()) {
                entity.setPrimaryFramework(analysis.getFrameworks().get(0).getName());
            }
            
            entity.setDatabaseCount(analysis.getDatabases().size());
            entity.setServiceCount(analysis.getServices().size());
            entity.setDependencyCount(analysis.getDependencies().size());
            
            // Resource requirements
            entity.setEstimatedMemoryMb(analysis.getResourceRequirements().getEstimatedMemoryMb());
            entity.setEstimatedCpuCores(BigDecimal.valueOf(
                analysis.getResourceRequirements().getEstimatedCpuCores()));
            
            entity.setOverallConfidence(BigDecimal.valueOf(analysis.getOverallConfidence()));
            
            return entity;
            
        } catch (JsonProcessingException e) {
            throw new DevOrchestratorException("Failed to serialize analysis results", e);
        }
    }
    
    /**
     * Determines project type from analysis
     */
    private String determineProjectType(ProjectAnalysis analysis) {
        // Check for bot frameworks
        boolean hasDiscordPy = analysis.getFrameworks().stream()
            .anyMatch(f -> "Discord.py".equalsIgnoreCase(f.getName()));
        boolean hasDiscordJs = analysis.getFrameworks().stream()
            .anyMatch(f -> "Discord.js".equalsIgnoreCase(f.getName()));
        
        if (hasDiscordPy || hasDiscordJs) {
            return "bot";
        }
        
        // Check for web frameworks
        boolean hasWebFramework = analysis.getFrameworks().stream()
            .anyMatch(f -> "web".equalsIgnoreCase(f.getCategory()));
        
        if (hasWebFramework) {
            return "web";
        }
        
        // Check for mobile frameworks
        boolean hasMobileFramework = analysis.getFrameworks().stream()
            .anyMatch(f -> "mobile".equalsIgnoreCase(f.getCategory()));
        
        if (hasMobileFramework) {
            return "mobile";
        }
        
        // Check for microservices
        if (analysis.getServices().size() > 3) {
            return "microservice";
        }
        
        // Default
        return "application";
    }
    
    /**
     * Starts a project (creates environment and starts containers)
     */
    public ProjectRegistration startProject(String projectId, User user) {
        ProjectRegistration project = getProjectById(projectId, user);
        
        if (project.getStatus() == ProjectRegistration.ProjectStatus.ACTIVE) {
            log.info("Project {} is already active", project.getName());
            return project;
        }
        
        // Ensure project has been analyzed
        if (project.getAnalysisId() == null) {
            throw new DevOrchestratorException("Project must be analyzed before starting");
        }
        
        try {
            // Create environment if needed
            if (project.getEnvironment() == null) {
                Environment environment = createProjectEnvironment(project);
                project.setEnvironment(environment);
            }
            
            // Start the environment
            environmentService.startEnvironment(project.getEnvironment().getId(), user);
            
            // Update project status
            project.setStatus(ProjectRegistration.ProjectStatus.ACTIVE);
            project.setLastActiveAt(LocalDateTime.now());
            project.setHealthStatus(ProjectRegistration.HealthStatus.HEALTHY);
            
            projectRepository.save(project);
            
            // Start metrics collection
            metricsCollectorService.collectProjectMetrics(project);
            
            log.info("Started project: {}", project.getName());
            
            return project;
            
        } catch (Exception e) {
            log.error("Failed to start project {}", project.getName(), e);
            project.setStatus(ProjectRegistration.ProjectStatus.ERROR);
            project.setHealthStatus(ProjectRegistration.HealthStatus.UNHEALTHY);
            projectRepository.save(project);
            throw new DevOrchestratorException("Failed to start project", e);
        }
    }
    
    /**
     * Stops a project
     */
    public ProjectRegistration stopProject(String projectId, User user) {
        ProjectRegistration project = getProjectById(projectId, user);
        
        if (project.getStatus() != ProjectRegistration.ProjectStatus.ACTIVE) {
            log.info("Project {} is not active", project.getName());
            return project;
        }
        
        try {
            // Stop the environment
            if (project.getEnvironment() != null) {
                environmentService.stopEnvironment(project.getEnvironment().getId(), user);
            }
            
            // Update project status
            project.setStatus(ProjectRegistration.ProjectStatus.INACTIVE);
            project.setHealthStatus(null);
            
            projectRepository.save(project);
            
            // Clear metrics cache
            metricsCollectorService.clearProjectCache(projectId);
            
            log.info("Stopped project: {}", project.getName());
            
            return project;
            
        } catch (Exception e) {
            log.error("Failed to stop project {}", project.getName(), e);
            throw new DevOrchestratorException("Failed to stop project", e);
        }
    }
    
    /**
     * Creates an environment for a project based on its analysis
     */
    private Environment createProjectEnvironment(ProjectRegistration project) {
        // Load the analysis
        ProjectAnalysisEntity analysis = analysisRepository.findById(project.getAnalysisId())
            .orElseThrow(() -> new DevOrchestratorException("Analysis not found"));
        
        // Create environment template based on analysis
        EnvironmentTemplate template = createTemplateFromAnalysis(analysis);
        
        // Create the environment
        return environmentService.createEnvironmentFromTemplate(
            template.getId(),
            project.getUser().getId(),
            project.getName() + "-env"
        );
    }
    
    /**
     * Creates a template from project analysis
     */
    private EnvironmentTemplate createTemplateFromAnalysis(ProjectAnalysisEntity analysis) {
        // This would be implemented to generate appropriate Docker Compose
        // or other configuration based on the analysis results
        
        // For now, return a placeholder
        throw new UnsupportedOperationException("Template generation not yet implemented");
    }
    
    /**
     * Gets all projects for a user
     */
    public Page<ProjectRegistration> getUserProjects(User user, Pageable pageable) {
        return projectRepository.findByUserIdOrderByUpdatedAtDesc(user.getId(), pageable);
    }
    
    /**
     * Gets active projects for a user
     */
    public List<ProjectRegistration> getActiveProjects(User user) {
        return projectRepository.findByUserIdAndStatus(
            user.getId(), 
            ProjectRegistration.ProjectStatus.ACTIVE
        );
    }
    
    /**
     * Gets a project by ID with authorization check
     */
    public ProjectRegistration getProjectById(String projectId, User user) {
        ProjectRegistration project = projectRepository.findById(projectId)
            .orElseThrow(() -> new DevOrchestratorException("Project not found"));
        
        if (!project.getUser().getId().equals(user.getId())) {
            throw new DevOrchestratorException("Unauthorized access to project");
        }
        
        return project;
    }
    
    /**
     * Updates project configuration
     */
    public ProjectRegistration updateProjectConfig(String projectId, User user,
                                                 Boolean autoStart, Boolean autoScale, 
                                                 Boolean monitoring) {
        ProjectRegistration project = getProjectById(projectId, user);
        
        if (autoStart != null) project.setAutoStart(autoStart);
        if (autoScale != null) project.setAutoScaleEnabled(autoScale);
        if (monitoring != null) project.setMonitoringEnabled(monitoring);
        
        return projectRepository.save(project);
    }
    
    /**
     * Deletes a project registration
     */
    public void deleteProject(String projectId, User user) {
        ProjectRegistration project = getProjectById(projectId, user);
        
        // Stop project if active
        if (project.getStatus() == ProjectRegistration.ProjectStatus.ACTIVE) {
            stopProject(projectId, user);
        }
        
        // Delete associated environment if exists
        if (project.getEnvironment() != null) {
            environmentService.deleteEnvironment(project.getEnvironment().getId(), user);
        }
        
        // Delete the project (cascades to analysis and metrics)
        projectRepository.delete(project);
        
        log.info("Deleted project: {}", project.getName());
    }
    
    /**
     * Checks health status of active projects
     */
    @Async
    public void checkProjectHealth() {
        List<ProjectRegistration> activeProjects = projectRepository.findByStatus(
            ProjectRegistration.ProjectStatus.ACTIVE
        );
        
        for (ProjectRegistration project : activeProjects) {
            try {
                // Check if environment is healthy
                if (project.getEnvironment() != null) {
                    Environment env = project.getEnvironment();
                    if (env.getStatus() != EnvironmentStatus.RUNNING) {
                        project.setHealthStatus(ProjectRegistration.HealthStatus.UNHEALTHY);
                    } else {
                        // Could add more health checks here
                        project.setHealthStatus(ProjectRegistration.HealthStatus.HEALTHY);
                    }
                    
                    projectRepository.save(project);
                }
            } catch (Exception e) {
                log.error("Error checking health for project {}", project.getName(), e);
            }
        }
    }
}