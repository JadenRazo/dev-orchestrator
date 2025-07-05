package com.devorchestrator.service;

import com.devorchestrator.entity.*;
import com.devorchestrator.repository.*;
import com.devorchestrator.exception.DockerOperationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class ProjectStartupService {
    
    private final ProjectRegistrationRepository projectRepository;
    private final ProjectAnalysisRepository analysisRepository;
    private final EnvironmentRepository environmentRepository;
    private final EnvironmentTemplateRepository templateRepository;
    private final ContainerOrchestrationService containerService;
    private final TemplateGeneratorService templateGenerator;
    private final MetricsCollectorService metricsCollector;
    private final WebSocketNotificationService notificationService;
    private final ObjectMapper objectMapper;
    
    // Base directory for project workspaces
    private static final String WORKSPACE_BASE = "/tmp/dev-orchestrator/workspaces";
    
    public ProjectStartupService(ProjectRegistrationRepository projectRepository,
                               ProjectAnalysisRepository analysisRepository,
                               EnvironmentRepository environmentRepository,
                               EnvironmentTemplateRepository templateRepository,
                               ContainerOrchestrationService containerService,
                               TemplateGeneratorService templateGenerator,
                               MetricsCollectorService metricsCollector,
                               WebSocketNotificationService notificationService,
                               ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.analysisRepository = analysisRepository;
        this.environmentRepository = environmentRepository;
        this.templateRepository = templateRepository;
        this.containerService = containerService;
        this.templateGenerator = templateGenerator;
        this.metricsCollector = metricsCollector;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Start a project environment
     */
    @Async
    public CompletableFuture<Environment> startProject(String projectId, Long userId) {
        log.info("Starting project: {} for user: {}", projectId, userId);
        
        try {
            // Get project and its analysis
            ProjectRegistration project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
            
            // Check if environment already exists and is running
            Optional<Environment> existingEnv = environmentRepository
                .findByProjectIdAndStatus(projectId, EnvironmentStatus.RUNNING);
            if (existingEnv.isPresent()) {
                log.info("Project {} already has a running environment", projectId);
                return CompletableFuture.completedFuture(existingEnv.get());
            }
            
            // Get latest analysis
            ProjectAnalysisEntity analysis = analysisRepository
                .findTopByProjectIdOrderByAnalyzedAtDesc(projectId)
                .orElseThrow(() -> new IllegalStateException(
                    "No analysis found for project. Please analyze the project first."));
            
            // Generate or get template
            EnvironmentTemplate template = getOrGenerateTemplate(project, analysis);
            
            // Create environment
            Environment environment = createEnvironment(project, template, userId);
            
            // Setup workspace
            Path workspacePath = setupWorkspace(project, environment);
            
            // Write docker-compose file
            writeDockerComposeFile(workspacePath, template);
            
            // Start containers
            startContainers(workspacePath, environment);
            
            // Update project status
            project.setStatus(ProjectRegistration.ProjectStatus.ACTIVE);
            projectRepository.save(project);
            
            // Start monitoring
            startMonitoring(project, environment);
            
            // Send notification
            notificationService.sendNotification(userId, 
                "Project " + project.getName() + " started successfully", 
                "success");
            
            return CompletableFuture.completedFuture(environment);
            
        } catch (Exception e) {
            log.error("Error starting project: {}", projectId, e);
            notificationService.sendNotification(userId, 
                "Failed to start project: " + e.getMessage(), 
                "error");
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Stop a project environment
     */
    @Async
    public CompletableFuture<Void> stopProject(String projectId, Long userId) {
        log.info("Stopping project: {} for user: {}", projectId, userId);
        
        try {
            // Get project
            ProjectRegistration project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
            
            // Get running environment
            Environment environment = environmentRepository
                .findByProjectIdAndStatus(projectId, EnvironmentStatus.RUNNING)
                .orElseThrow(() -> new IllegalStateException("No running environment found"));
            
            // Get workspace path
            Path workspacePath = Paths.get(WORKSPACE_BASE, environment.getId());
            
            // Stop containers
            stopContainers(workspacePath, environment);
            
            // Update environment status
            environment.setStatus(EnvironmentStatus.STOPPED);
            environment.setStoppedAt(LocalDateTime.now());
            environmentRepository.save(environment);
            
            // Update project status
            project.setStatus(ProjectRegistration.ProjectStatus.INACTIVE);
            projectRepository.save(project);
            
            // Stop monitoring
            metricsCollector.clearProjectCache(projectId);
            
            // Send notification
            notificationService.sendNotification(userId, 
                "Project " + project.getName() + " stopped successfully", 
                "info");
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Error stopping project: {}", projectId, e);
            notificationService.sendNotification(userId, 
                "Failed to stop project: " + e.getMessage(), 
                "error");
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Get project runtime status
     */
    public Map<String, Object> getProjectStatus(String projectId) {
        Map<String, Object> status = new HashMap<>();
        
        // Get project
        ProjectRegistration project = projectRepository.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        
        status.put("projectId", projectId);
        status.put("projectName", project.getName());
        status.put("projectStatus", project.getStatus().name());
        
        // Get environment
        Optional<Environment> environment = environmentRepository
            .findByProjectIdAndStatusIn(projectId, 
                Arrays.asList(EnvironmentStatus.RUNNING, EnvironmentStatus.STARTING));
        
        if (environment.isPresent()) {
            Environment env = environment.get();
            status.put("environmentId", env.getId());
            status.put("environmentStatus", env.getStatus().name());
            status.put("startedAt", env.getCreatedAt());
            
            // Get container statuses
            List<Map<String, Object>> containerStatuses = getContainerStatuses(env);
            status.put("containers", containerStatuses);
            
            // Get resource usage summary
            Map<String, Object> resourceUsage = getResourceUsageSummary(projectId);
            status.put("resourceUsage", resourceUsage);
            
            // Get exposed URLs
            List<String> urls = getExposedUrls(env);
            status.put("urls", urls);
        } else {
            status.put("environmentStatus", "NONE");
            status.put("containers", new ArrayList<>());
        }
        
        return status;
    }
    
    /**
     * Restart a project environment
     */
    @Async
    public CompletableFuture<Environment> restartProject(String projectId, Long userId) {
        log.info("Restarting project: {} for user: {}", projectId, userId);
        
        // Stop the project first
        return stopProject(projectId, userId)
            .thenCompose(v -> {
                // Wait a bit for cleanup
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // Start the project again
                return startProject(projectId, userId);
            });
    }
    
    /**
     * Get or generate template for project
     */
    private EnvironmentTemplate getOrGenerateTemplate(ProjectRegistration project, 
                                                    ProjectAnalysisEntity analysis) {
        // Check if template already exists for this project
        String templateName = project.getName() + " - Docker Compose";
        Optional<EnvironmentTemplate> existing = templateRepository.findByName(templateName);
        
        if (existing.isPresent()) {
            log.info("Using existing template for project: {}", project.getName());
            return existing.get();
        }
        
        // Generate new template
        log.info("Generating new template for project: {}", project.getName());
        return templateGenerator.generateDockerComposeTemplate(project.getId(), analysis.getId());
    }
    
    /**
     * Create environment entity
     */
    private Environment createEnvironment(ProjectRegistration project, 
                                        EnvironmentTemplate template, 
                                        Long userId) {
        Environment environment = Environment.builder()
            .id(UUID.randomUUID().toString())
            .name(project.getName() + " Environment")
            .template(template)
            .status(EnvironmentStatus.PROVISIONING)
            .createdBy(userId)
            .build();
        
        // Set project reference
        environment.setProjectId(project.getId());
        
        return environmentRepository.save(environment);
    }
    
    /**
     * Setup workspace directory
     */
    private Path setupWorkspace(ProjectRegistration project, Environment environment) 
            throws IOException {
        Path workspacePath = Paths.get(WORKSPACE_BASE, environment.getId());
        Files.createDirectories(workspacePath);
        
        // Create symlink to actual project directory if local
        Path projectPath = Paths.get(project.getPath());
        if (Files.exists(projectPath) && Files.isDirectory(projectPath)) {
            // Copy project files (in production, would use volumes)
            log.info("Setting up workspace from: {} to: {}", projectPath, workspacePath);
            
            // For now, just create a reference file
            Path referenceFile = workspacePath.resolve(".project-ref");
            Files.writeString(referenceFile, projectPath.toString());
        }
        
        return workspacePath;
    }
    
    /**
     * Write docker-compose file to workspace
     */
    private void writeDockerComposeFile(Path workspacePath, EnvironmentTemplate template) 
            throws IOException {
        Path composeFile = workspacePath.resolve("docker-compose.yml");
        Files.writeString(composeFile, template.getDockerComposeContent(), 
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        
        // Write .env file if environment variables are defined
        if (template.getEnvironmentVariables() != null) {
            Path envFile = workspacePath.resolve(".env");
            String envContent = convertEnvJsonToFile(template.getEnvironmentVariables());
            Files.writeString(envFile, envContent,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }
    
    /**
     * Start containers using docker-compose
     */
    private void startContainers(Path workspacePath, Environment environment) 
            throws IOException, InterruptedException {
        log.info("Starting containers in: {}", workspacePath);
        
        // Update environment status
        environment.setStatus(EnvironmentStatus.STARTING);
        environmentRepository.save(environment);
        
        // Run docker-compose up
        ProcessBuilder pb = new ProcessBuilder(
            "docker-compose", "up", "-d"
        );
        pb.directory(workspacePath.toFile());
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // Capture output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.debug("docker-compose: {}", line);
            }
        }
        
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new DockerOperationException("Docker compose up timed out");
        }
        
        if (process.exitValue() != 0) {
            throw new DockerOperationException(
                "Docker compose up failed: " + output.toString());
        }
        
        // Update environment status
        environment.setStatus(EnvironmentStatus.RUNNING);
        environmentRepository.save(environment);
        
        log.info("Containers started successfully for environment: {}", environment.getId());
    }
    
    /**
     * Stop containers using docker-compose
     */
    private void stopContainers(Path workspacePath, Environment environment) 
            throws IOException, InterruptedException {
        log.info("Stopping containers in: {}", workspacePath);
        
        if (!Files.exists(workspacePath)) {
            log.warn("Workspace directory not found: {}", workspacePath);
            return;
        }
        
        // Update environment status
        environment.setStatus(EnvironmentStatus.STOPPING);
        environmentRepository.save(environment);
        
        // Run docker-compose down
        ProcessBuilder pb = new ProcessBuilder(
            "docker-compose", "down", "-v"
        );
        pb.directory(workspacePath.toFile());
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // Capture output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.debug("docker-compose: {}", line);
            }
        }
        
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.warn("Docker compose down timed out, forcing shutdown");
        }
        
        log.info("Containers stopped for environment: {}", environment.getId());
    }
    
    /**
     * Start monitoring for project
     */
    private void startMonitoring(ProjectRegistration project, Environment environment) {
        // Label containers for monitoring
        Path workspacePath = Paths.get(WORKSPACE_BASE, environment.getId());
        
        try {
            // Get container IDs
            ProcessBuilder pb = new ProcessBuilder(
                "docker-compose", "ps", "-q"
            );
            pb.directory(workspacePath.toFile());
            
            Process process = pb.start();
            List<String> containerIds = new ArrayList<>();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        containerIds.add(line.trim());
                    }
                }
            }
            
            process.waitFor();
            
            // Label containers for monitoring
            for (String containerId : containerIds) {
                ProcessBuilder labelPb = new ProcessBuilder(
                    "docker", "label", containerId, "project=" + project.getName()
                );
                labelPb.start().waitFor();
            }
            
            log.info("Monitoring started for {} containers", containerIds.size());
            
        } catch (Exception e) {
            log.error("Error setting up monitoring", e);
        }
    }
    
    /**
     * Get container statuses
     */
    private List<Map<String, Object>> getContainerStatuses(Environment environment) {
        List<Map<String, Object>> statuses = new ArrayList<>();
        Path workspacePath = Paths.get(WORKSPACE_BASE, environment.getId());
        
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "docker-compose", "ps", "--format", "json"
            );
            pb.directory(workspacePath.toFile());
            
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        // Parse JSON line
                        Map<String, Object> containerInfo = objectMapper.readValue(
                            line, Map.class);
                        statuses.add(containerInfo);
                    }
                }
            }
            
            process.waitFor();
            
        } catch (Exception e) {
            log.error("Error getting container statuses", e);
        }
        
        return statuses;
    }
    
    /**
     * Get resource usage summary
     */
    private Map<String, Object> getResourceUsageSummary(String projectId) {
        Map<String, Object> usage = new HashMap<>();
        
        // Get latest metrics
        Double cpuUsage = metricRepository.getAverageMetricValue(
            projectId, "cpu_usage_percent", 
            LocalDateTime.now().minusMinutes(5), LocalDateTime.now());
        
        Double memoryUsage = metricRepository.getAverageMetricValue(
            projectId, "memory_used_mb",
            LocalDateTime.now().minusMinutes(5), LocalDateTime.now());
        
        usage.put("cpuPercent", cpuUsage != null ? cpuUsage : 0.0);
        usage.put("memoryMb", memoryUsage != null ? memoryUsage : 0.0);
        
        return usage;
    }
    
    /**
     * Get exposed URLs for environment
     */
    private List<String> getExposedUrls(Environment environment) {
        List<String> urls = new ArrayList<>();
        
        // Get exposed ports from template
        if (environment.getTemplate() != null) {
            Set<Integer> ports = environment.getTemplate().getExposedPorts();
            for (Integer port : ports) {
                urls.add("http://localhost:" + port);
            }
        }
        
        return urls;
    }
    
    /**
     * Convert environment JSON to .env file format
     */
    private String convertEnvJsonToFile(String envJson) {
        try {
            Map<String, String> envVars = objectMapper.readValue(envJson, Map.class);
            return envVars.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.error("Error converting env JSON", e);
            return "";
        }
    }
}