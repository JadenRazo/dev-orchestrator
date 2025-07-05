package com.devorchestrator.analyzer;

import com.devorchestrator.analyzer.detector.TechnologyDetector;
import com.devorchestrator.analyzer.model.ProjectAnalysis;
import com.devorchestrator.exception.DevOrchestratorException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProjectAnalyzerService {
    
    private final List<TechnologyDetector> detectors;
    private final ExecutorService executorService;
    
    public ProjectAnalyzerService(List<TechnologyDetector> detectors) {
        this.detectors = detectors.stream()
            .sorted(Comparator.comparing(TechnologyDetector::getPriority).reversed())
            .collect(Collectors.toList());
        this.executorService = Executors.newFixedThreadPool(4);
        
        log.info("Initialized ProjectAnalyzerService with {} detectors", detectors.size());
    }
    
    public ProjectAnalysis analyzeProject(String projectPath) {
        Path path = Paths.get(projectPath);
        
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new DevOrchestratorException("Invalid project path: " + projectPath);
        }
        
        log.info("Starting analysis of project: {}", projectPath);
        
        ProjectAnalysis analysis = ProjectAnalysis.builder()
            .projectPath(projectPath)
            .projectName(extractProjectName(path))
            .analyzedAt(LocalDateTime.now())
            .build();
        
        // Run detectors in priority order
        for (TechnologyDetector detector : detectors) {
            try {
                log.debug("Running detector: {}", detector.getName());
                detector.detect(path, analysis);
            } catch (Exception e) {
                log.error("Error in detector {}: {}", detector.getName(), e.getMessage());
                analysis.addWarning("Detector Error", 
                    String.format("Failed to run %s detector: %s", detector.getName(), e.getMessage()));
            }
        }
        
        // Post-process analysis
        postProcessAnalysis(analysis);
        
        log.info("Completed analysis of project: {} with confidence: {}", 
            projectPath, analysis.getOverallConfidence());
        
        return analysis;
    }
    
    public CompletableFuture<ProjectAnalysis> analyzeProjectAsync(String projectPath) {
        return CompletableFuture.supplyAsync(() -> analyzeProject(projectPath), executorService);
    }
    
    public ProjectAnalysis analyzeGitRepository(String gitUrl, String branch) {
        // Clone repository to temp directory
        Path tempDir = cloneRepository(gitUrl, branch);
        
        try {
            // Analyze the cloned repository
            ProjectAnalysis analysis = analyzeProject(tempDir.toString());
            analysis.setProjectName(extractProjectNameFromGitUrl(gitUrl));
            return analysis;
        } finally {
            // Clean up temp directory
            cleanupTempDirectory(tempDir);
        }
    }
    
    private void postProcessAnalysis(ProjectAnalysis analysis) {
        // Calculate overall confidence
        analysis.calculateOverallConfidence();
        
        // Add recommendations based on detected technologies
        addRecommendations(analysis);
        
        // Estimate resource requirements
        estimateResourceRequirements(analysis);
        
        // Validate detected technologies
        validateDetections(analysis);
    }
    
    private void addRecommendations(ProjectAnalysis analysis) {
        // Database recommendations
        if (analysis.getDatabases().isEmpty() && !analysis.getFrameworks().isEmpty()) {
            analysis.addRecommendation("No database detected. Consider adding a database if your application needs persistence.");
        }
        
        // Caching recommendations
        boolean hasCache = analysis.getServices().stream()
            .anyMatch(s -> s.getType() == com.devorchestrator.analyzer.model.DetectedService.ServiceType.CACHE);
        if (!hasCache && analysis.getFrameworks().stream().anyMatch(f -> f.getCategory() != null && f.getCategory().equals("web"))) {
            analysis.addRecommendation("Consider adding Redis or Memcached for caching to improve performance.");
        }
        
        // Monitoring recommendations
        boolean hasMonitoring = analysis.getServices().stream()
            .anyMatch(s -> s.getType() == com.devorchestrator.analyzer.model.DetectedService.ServiceType.MONITORING);
        if (!hasMonitoring) {
            analysis.addRecommendation("Consider adding monitoring tools like Prometheus and Grafana.");
        }
        
        // Security recommendations
        if (analysis.getEnvironmentVariables().containsKey("SECRET") || 
            analysis.getEnvironmentVariables().containsKey("PASSWORD") ||
            analysis.getEnvironmentVariables().containsKey("API_KEY")) {
            analysis.addRecommendation("Sensitive data detected in environment variables. Ensure proper secret management.");
        }
    }
    
    private void estimateResourceRequirements(ProjectAnalysis analysis) {
        ProjectAnalysis.ResourceRequirements requirements = analysis.getResourceRequirements();
        
        // Base requirements
        int baseMemory = 256;
        double baseCpu = 0.25;
        
        // Add based on detected technologies
        for (var framework : analysis.getFrameworks()) {
            switch (framework.getName().toLowerCase()) {
                case "spring boot":
                case "django":
                case "rails":
                    baseMemory += 512;
                    baseCpu += 0.5;
                    break;
                case "express":
                case "fastapi":
                case "flask":
                    baseMemory += 256;
                    baseCpu += 0.25;
                    break;
            }
        }
        
        // Add for databases
        for (var database : analysis.getDatabases()) {
            switch (database.getName().toLowerCase()) {
                case "postgresql":
                case "mysql":
                case "mongodb":
                    baseMemory += 512;
                    baseCpu += 0.5;
                    break;
                case "redis":
                case "memcached":
                    baseMemory += 128;
                    baseCpu += 0.1;
                    break;
            }
        }
        
        requirements.setEstimatedMemoryMb(baseMemory);
        requirements.setEstimatedCpuCores(baseCpu);
    }
    
    private void validateDetections(ProjectAnalysis analysis) {
        // Check for conflicting detections
        if (analysis.getLanguages().size() > 3) {
            analysis.addWarning("Multiple Languages", 
                "Detected many programming languages. This might indicate a polyglot project or false positives.");
        }
        
        // Validate framework-language compatibility
        for (var framework : analysis.getFrameworks()) {
            if (framework.getLanguage() != null) {
                boolean hasLanguage = analysis.getLanguages().stream()
                    .anyMatch(l -> l.getName().equalsIgnoreCase(framework.getLanguage()));
                if (!hasLanguage) {
                    analysis.addWarning("Framework Mismatch", 
                        String.format("Framework %s requires %s but language not detected", 
                            framework.getName(), framework.getLanguage()));
                }
            }
        }
    }
    
    private String extractProjectName(Path projectPath) {
        String dirName = projectPath.getFileName().toString();
        
        // Check for package.json
        Path packageJson = projectPath.resolve("package.json");
        if (Files.exists(packageJson)) {
            try {
                String content = Files.readString(packageJson);
                // Simple extraction - in production use proper JSON parser
                if (content.contains("\"name\"")) {
                    int start = content.indexOf("\"name\"") + 8;
                    int end = content.indexOf("\"", start);
                    if (end > start) {
                        return content.substring(start, end);
                    }
                }
            } catch (IOException e) {
                log.debug("Failed to read package.json", e);
            }
        }
        
        // Check for pom.xml
        Path pomXml = projectPath.resolve("pom.xml");
        if (Files.exists(pomXml)) {
            try {
                String content = Files.readString(pomXml);
                if (content.contains("<artifactId>")) {
                    int start = content.indexOf("<artifactId>") + 12;
                    int end = content.indexOf("</artifactId>", start);
                    if (end > start) {
                        return content.substring(start, end);
                    }
                }
            } catch (IOException e) {
                log.debug("Failed to read pom.xml", e);
            }
        }
        
        return dirName;
    }
    
    private String extractProjectNameFromGitUrl(String gitUrl) {
        String name = gitUrl;
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        return name;
    }
    
    private Path cloneRepository(String gitUrl, String branch) {
        try {
            Path tempDir = Files.createTempDirectory("devorchestrator-analyze-");
            
            ProcessBuilder pb = new ProcessBuilder(
                "git", "clone", "--depth", "1", "--branch", branch, gitUrl, tempDir.toString()
            );
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                throw new DevOrchestratorException("Failed to clone repository: " + gitUrl);
            }
            
            return tempDir;
        } catch (IOException | InterruptedException e) {
            throw new DevOrchestratorException("Error cloning repository", e);
        }
    }
    
    private void cleanupTempDirectory(Path tempDir) {
        try {
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("Failed to delete temp file: {}", path);
                        }
                    });
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup temp directory: {}", tempDir);
        }
    }
}