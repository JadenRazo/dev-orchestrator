package com.devorchestrator.analyzer.detector;

import com.devorchestrator.analyzer.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class DependencyAnalyzerService implements TechnologyDetector {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Common development dependencies that we can skip for cleaner results
    private static final Set<String> DEV_DEPENDENCIES = Set.of(
        "jest", "mocha", "chai", "eslint", "prettier", "nodemon",
        "pytest", "black", "flake8", "mypy", "pylint",
        "junit", "mockito", "testcontainers",
        "rspec", "rubocop", "simplecov"
    );
    
    @Override
    public void detect(Path projectPath, ProjectAnalysis analysis) {
        log.info("Starting dependency analysis for project: {}", projectPath);
        
        // Analyze package.json for Node.js projects
        analyzePackageJson(projectPath, analysis);
        
        // Analyze requirements.txt for Python projects
        analyzeRequirementsTxt(projectPath, analysis);
        
        // Analyze go.mod for Go projects
        analyzeGoMod(projectPath, analysis);
        
        // Analyze pom.xml for Java projects
        analyzePomXml(projectPath, analysis);
        
        // Analyze Gemfile for Ruby projects
        analyzeGemfile(projectPath, analysis);
        
        // Analyze composer.json for PHP projects
        analyzeComposerJson(projectPath, analysis);
        
        // Analyze build.gradle for Gradle projects
        analyzeBuildGradle(projectPath, analysis);
        
        // Analyze Cargo.toml for Rust projects
        analyzeCargoToml(projectPath, analysis);
        
        // Analyze project.clj for Clojure projects
        analyzeProjectClj(projectPath, analysis);
        
        log.info("Completed dependency analysis");
    }
    
    private void analyzePackageJson(Path projectPath, ProjectAnalysis analysis) {
        Path packageJson = projectPath.resolve("package.json");
        if (!Files.exists(packageJson)) return;
        
        try {
            JsonNode root = objectMapper.readTree(Files.newBufferedReader(packageJson));
            
            // Extract package metadata
            String name = root.path("name").asText();
            String version = root.path("version").asText();
            String description = root.path("description").asText();
            
            if (!name.isEmpty()) {
                analysis.setProjectName(name);
            }
            
            // Process dependencies
            processDependencies(root.path("dependencies"), analysis, "npm", "runtime");
            processDependencies(root.path("devDependencies"), analysis, "npm", "dev");
            processDependencies(root.path("peerDependencies"), analysis, "npm", "peer");
            
            // Check for scripts that might indicate services
            JsonNode scripts = root.path("scripts");
            if (scripts.isObject()) {
                analyzeNpmScripts(scripts, analysis);
            }
            
            // Check for specific configurations
            checkNodeConfigurations(root, analysis);
            
        } catch (IOException e) {
            log.warn("Failed to parse package.json", e);
            analysis.addWarning("Dependency Analysis", "Failed to parse package.json: " + e.getMessage());
        }
    }
    
    private void processDependencies(JsonNode deps, ProjectAnalysis analysis, 
                                   String packageManager, String scope) {
        if (!deps.isObject()) return;
        
        deps.fields().forEachRemaining(entry -> {
            String depName = entry.getKey();
            String depVersion = entry.getValue().asText();
            
            // Skip dev dependencies in runtime scope for cleaner results
            if ("runtime".equals(scope) || !DEV_DEPENDENCIES.contains(depName)) {
                DetectedDependency dependency = DetectedDependency.builder()
                    .name(depName)
                    .version(depVersion)
                    .scope(scope)
                    .packageManager(packageManager)
                    .isDirect(true)
                    .purpose(categorizeDependency(depName))
                    .build();
                
                analysis.addDependency(dependency);
                
                // Detect technologies from dependencies
                detectTechnologyFromDependency(depName, depVersion, analysis);
            }
        });
    }
    
    private void analyzeNpmScripts(JsonNode scripts, ProjectAnalysis analysis) {
        scripts.fields().forEachRemaining(entry -> {
            String scriptName = entry.getKey();
            String scriptContent = entry.getValue().asText();
            
            // Detect build tools
            if (scriptContent.contains("webpack")) {
                analysis.addService(createBuildToolService("Webpack", 8080));
            }
            if (scriptContent.contains("vite")) {
                analysis.addService(createBuildToolService("Vite", 5173));
            }
            if (scriptContent.contains("parcel")) {
                analysis.addService(createBuildToolService("Parcel", 1234));
            }
            
            // Detect test runners
            if (scriptContent.contains("jest") || scriptContent.contains("vitest")) {
                analysis.addService(createTestService("Jest/Vitest"));
            }
            
            // Detect development servers
            if (scriptName.equals("dev") || scriptName.equals("start")) {
                analyzeDevScript(scriptContent, analysis);
            }
        });
    }
    
    private void analyzeDevScript(String script, ProjectAnalysis analysis) {
        Pattern portPattern = Pattern.compile("--port\\s+(\\d+)|PORT=(\\d+)");
        Matcher matcher = portPattern.matcher(script);
        if (matcher.find()) {
            String port = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            analysis.getResourceRequirements().getRequiredPorts().add(Integer.parseInt(port));
        }
    }
    
    private void checkNodeConfigurations(JsonNode root, ProjectAnalysis analysis) {
        // Check for TypeScript
        if (Files.exists(analysis.getProjectPath().resolve("tsconfig.json"))) {
            analysis.addService(createBuildToolService("TypeScript Compiler", null));
        }
        
        // Check for specific Node.js configurations
        JsonNode engines = root.path("engines");
        if (engines.has("node")) {
            String nodeVersion = engines.path("node").asText();
            analysis.getLanguages().stream()
                .filter(lang -> "JavaScript".equals(lang.getName()) || "TypeScript".equals(lang.getName()))
                .forEach(lang -> lang.setVersion(extractVersion(nodeVersion)));
        }
    }
    
    private void analyzeRequirementsTxt(Path projectPath, ProjectAnalysis analysis) {
        Path requirements = projectPath.resolve("requirements.txt");
        if (!Files.exists(requirements)) return;
        
        try {
            List<String> lines = Files.readAllLines(requirements);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                parsePythonDependency(line, analysis);
            }
        } catch (IOException e) {
            log.warn("Failed to read requirements.txt", e);
        }
        
        // Also check for other Python dependency files
        analyzePipfile(projectPath, analysis);
        analyzeSetupPy(projectPath, analysis);
        analyzePyprojectToml(projectPath, analysis);
    }
    
    private void parsePythonDependency(String line, ProjectAnalysis analysis) {
        // Remove comments
        int commentIndex = line.indexOf('#');
        if (commentIndex >= 0) {
            line = line.substring(0, commentIndex).trim();
        }
        
        // Parse dependency with version specifier
        Pattern pattern = Pattern.compile("([a-zA-Z0-9-_.]+)\\s*([<>=!~]+.*)?");
        Matcher matcher = pattern.matcher(line);
        
        if (matcher.matches()) {
            String name = matcher.group(1);
            String version = matcher.group(2) != null ? matcher.group(2).trim() : "latest";
            
            DetectedDependency dependency = DetectedDependency.builder()
                .name(name)
                .version(version)
                .scope("runtime")
                .packageManager("pip")
                .isDirect(true)
                .purpose(categorizeDependency(name))
                .build();
            
            analysis.addDependency(dependency);
            detectTechnologyFromDependency(name, version, analysis);
        }
    }
    
    private void analyzePipfile(Path projectPath, ProjectAnalysis analysis) {
        Path pipfile = projectPath.resolve("Pipfile");
        if (!Files.exists(pipfile)) return;
        
        try {
            List<String> lines = Files.readAllLines(pipfile);
            String currentSection = null;
            
            for (String line : lines) {
                line = line.trim();
                
                if (line.startsWith("[") && line.endsWith("]")) {
                    currentSection = line.substring(1, line.length() - 1);
                } else if (currentSection != null && line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        String name = parts[0].trim();
                        String version = parts[1].trim().replace("\"", "").replace("'", "");
                        String scope = "dev-packages".equals(currentSection) ? "dev" : "runtime";
                        
                        DetectedDependency dependency = DetectedDependency.builder()
                            .name(name)
                            .version(version)
                            .scope(scope)
                            .packageManager("pipenv")
                            .isDirect(true)
                            .purpose(categorizeDependency(name))
                            .build();
                        
                        analysis.addDependency(dependency);
                    }
                }
            }
        } catch (IOException e) {
            log.debug("Failed to read Pipfile", e);
        }
    }
    
    private void analyzeSetupPy(Path projectPath, ProjectAnalysis analysis) {
        Path setupPy = projectPath.resolve("setup.py");
        if (!Files.exists(setupPy)) return;
        
        try {
            String content = Files.readString(setupPy);
            
            // Extract install_requires
            Pattern pattern = Pattern.compile("install_requires\\s*=\\s*\\[(.*?)\\]", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(content);
            
            if (matcher.find()) {
                String requires = matcher.group(1);
                Pattern depPattern = Pattern.compile("['\"]([^'\"]+)['\"]");
                Matcher depMatcher = depPattern.matcher(requires);
                
                while (depMatcher.find()) {
                    parsePythonDependency(depMatcher.group(1), analysis);
                }
            }
        } catch (IOException e) {
            log.debug("Failed to read setup.py", e);
        }
    }
    
    private void analyzePyprojectToml(Path projectPath, ProjectAnalysis analysis) {
        Path pyproject = projectPath.resolve("pyproject.toml");
        if (!Files.exists(pyproject)) return;
        
        try {
            List<String> lines = Files.readAllLines(pyproject);
            boolean inDependencies = false;
            
            for (String line : lines) {
                line = line.trim();
                
                if (line.equals("[tool.poetry.dependencies]") || 
                    line.equals("[project.dependencies]")) {
                    inDependencies = true;
                } else if (line.startsWith("[") && inDependencies) {
                    inDependencies = false;
                } else if (inDependencies && line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        String name = parts[0].trim();
                        String version = parts[1].trim().replace("\"", "").replace("'", "");
                        
                        if (!name.equals("python")) {
                            DetectedDependency dependency = DetectedDependency.builder()
                                .name(name)
                                .version(version)
                                .scope("runtime")
                                .packageManager("poetry")
                                .isDirect(true)
                                .purpose(categorizeDependency(name))
                                .build();
                            
                            analysis.addDependency(dependency);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.debug("Failed to read pyproject.toml", e);
        }
    }
    
    private void analyzeGoMod(Path projectPath, ProjectAnalysis analysis) {
        Path goMod = projectPath.resolve("go.mod");
        if (!Files.exists(goMod)) return;
        
        try {
            List<String> lines = Files.readAllLines(goMod);
            boolean inRequire = false;
            
            for (String line : lines) {
                line = line.trim();
                
                if (line.startsWith("module ")) {
                    String moduleName = line.substring(7).trim();
                    if (analysis.getProjectName() == null) {
                        analysis.setProjectName(extractProjectNameFromModule(moduleName));
                    }
                } else if (line.equals("require (")) {
                    inRequire = true;
                } else if (line.equals(")") && inRequire) {
                    inRequire = false;
                } else if (inRequire || line.startsWith("require ")) {
                    parseGoRequire(line, analysis);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read go.mod", e);
        }
    }
    
    private void parseGoRequire(String line, ProjectAnalysis analysis) {
        line = line.replace("require ", "").trim();
        
        String[] parts = line.split("\\s+");
        if (parts.length >= 2) {
            String module = parts[0];
            String version = parts[1];
            
            DetectedDependency dependency = DetectedDependency.builder()
                .name(module)
                .version(version)
                .scope("runtime")
                .packageManager("go mod")
                .isDirect(true)
                .purpose(categorizeGoDependency(module))
                .build();
            
            analysis.addDependency(dependency);
            detectTechnologyFromDependency(module, version, analysis);
        }
    }
    
    private String extractProjectNameFromModule(String module) {
        String[] parts = module.split("/");
        return parts[parts.length - 1];
    }
    
    private void analyzePomXml(Path projectPath, ProjectAnalysis analysis) {
        Path pomXml = projectPath.resolve("pom.xml");
        if (!Files.exists(pomXml)) return;
        
        try {
            String content = Files.readString(pomXml);
            
            // Extract artifactId as project name
            Pattern artifactPattern = Pattern.compile("<artifactId>([^<]+)</artifactId>");
            Matcher artifactMatcher = artifactPattern.matcher(content);
            if (artifactMatcher.find() && analysis.getProjectName() == null) {
                analysis.setProjectName(artifactMatcher.group(1));
            }
            
            // Extract dependencies
            Pattern depPattern = Pattern.compile(
                "<dependency>\\s*" +
                "<groupId>([^<]+)</groupId>\\s*" +
                "<artifactId>([^<]+)</artifactId>\\s*" +
                "(?:<version>([^<]+)</version>)?",
                Pattern.DOTALL
            );
            
            Matcher depMatcher = depPattern.matcher(content);
            while (depMatcher.find()) {
                String groupId = depMatcher.group(1);
                String artifactId = depMatcher.group(2);
                String version = depMatcher.group(3) != null ? depMatcher.group(3) : "managed";
                
                String fullName = groupId + ":" + artifactId;
                String scope = content.contains("<scope>test</scope>") ? "test" : "runtime";
                
                DetectedDependency dependency = DetectedDependency.builder()
                    .name(fullName)
                    .version(version)
                    .scope(scope)
                    .packageManager("maven")
                    .isDirect(true)
                    .purpose(categorizeJavaDependency(artifactId))
                    .build();
                
                analysis.addDependency(dependency);
                detectTechnologyFromDependency(fullName, version, analysis);
            }
        } catch (IOException e) {
            log.warn("Failed to read pom.xml", e);
        }
    }
    
    private void analyzeBuildGradle(Path projectPath, ProjectAnalysis analysis) {
        List<Path> gradleFiles = Arrays.asList(
            projectPath.resolve("build.gradle"),
            projectPath.resolve("build.gradle.kts")
        );
        
        for (Path gradleFile : gradleFiles) {
            if (Files.exists(gradleFile)) {
                try {
                    String content = Files.readString(gradleFile);
                    
                    // Extract dependencies
                    Pattern depPattern = Pattern.compile(
                        "(?:implementation|compile|api|testImplementation)\\s*[\\(']([^'\"\\)]+)['\"]"
                    );
                    
                    Matcher depMatcher = depPattern.matcher(content);
                    while (depMatcher.find()) {
                        String dep = depMatcher.group(1);
                        String[] parts = dep.split(":");
                        
                        if (parts.length >= 3) {
                            String groupId = parts[0];
                            String artifactId = parts[1];
                            String version = parts[2];
                            
                            String fullName = groupId + ":" + artifactId;
                            
                            DetectedDependency dependency = DetectedDependency.builder()
                                .name(fullName)
                                .version(version)
                                .scope("runtime")
                                .packageManager("gradle")
                                .isDirect(true)
                                .purpose(categorizeJavaDependency(artifactId))
                                .build();
                            
                            analysis.addDependency(dependency);
                        }
                    }
                } catch (IOException e) {
                    log.debug("Failed to read {}", gradleFile, e);
                }
            }
        }
    }
    
    private void analyzeGemfile(Path projectPath, ProjectAnalysis analysis) {
        Path gemfile = projectPath.resolve("Gemfile");
        if (!Files.exists(gemfile)) return;
        
        try {
            List<String> lines = Files.readAllLines(gemfile);
            
            for (String line : lines) {
                line = line.trim();
                
                if (line.startsWith("gem ")) {
                    Pattern pattern = Pattern.compile("gem\\s+['\"]([^'\"]+)['\"](?:,\\s*['\"]([^'\"]+)['\"])?");
                    Matcher matcher = pattern.matcher(line);
                    
                    if (matcher.find()) {
                        String name = matcher.group(1);
                        String version = matcher.group(2) != null ? matcher.group(2) : "latest";
                        
                        DetectedDependency dependency = DetectedDependency.builder()
                            .name(name)
                            .version(version)
                            .scope("runtime")
                            .packageManager("bundler")
                            .isDirect(true)
                            .purpose(categorizeDependency(name))
                            .build();
                        
                        analysis.addDependency(dependency);
                        detectTechnologyFromDependency(name, version, analysis);
                    }
                }
            }
        } catch (IOException e) {
            log.debug("Failed to read Gemfile", e);
        }
    }
    
    private void analyzeComposerJson(Path projectPath, ProjectAnalysis analysis) {
        Path composerJson = projectPath.resolve("composer.json");
        if (!Files.exists(composerJson)) return;
        
        try {
            JsonNode root = objectMapper.readTree(Files.newBufferedReader(composerJson));
            
            String name = root.path("name").asText();
            if (!name.isEmpty() && analysis.getProjectName() == null) {
                analysis.setProjectName(name);
            }
            
            processDependencies(root.path("require"), analysis, "composer", "runtime");
            processDependencies(root.path("require-dev"), analysis, "composer", "dev");
            
        } catch (IOException e) {
            log.debug("Failed to parse composer.json", e);
        }
    }
    
    private void analyzeCargoToml(Path projectPath, ProjectAnalysis analysis) {
        Path cargoToml = projectPath.resolve("Cargo.toml");
        if (!Files.exists(cargoToml)) return;
        
        try {
            List<String> lines = Files.readAllLines(cargoToml);
            boolean inDependencies = false;
            
            for (String line : lines) {
                line = line.trim();
                
                if (line.equals("[dependencies]")) {
                    inDependencies = true;
                } else if (line.startsWith("[") && inDependencies) {
                    inDependencies = false;
                } else if (inDependencies && line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        String name = parts[0].trim();
                        String version = parts[1].trim().replace("\"", "").replace("'", "");
                        
                        DetectedDependency dependency = DetectedDependency.builder()
                            .name(name)
                            .version(version)
                            .scope("runtime")
                            .packageManager("cargo")
                            .isDirect(true)
                            .purpose(categorizeDependency(name))
                            .build();
                        
                        analysis.addDependency(dependency);
                    }
                }
            }
        } catch (IOException e) {
            log.debug("Failed to read Cargo.toml", e);
        }
    }
    
    private void analyzeProjectClj(Path projectPath, ProjectAnalysis analysis) {
        Path projectClj = projectPath.resolve("project.clj");
        if (!Files.exists(projectClj)) return;
        
        try {
            String content = Files.readString(projectClj);
            
            // Extract dependencies
            Pattern pattern = Pattern.compile(":dependencies\\s*\\[(.*?)\\]", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(content);
            
            if (matcher.find()) {
                String deps = matcher.group(1);
                Pattern depPattern = Pattern.compile("\\[([^\\s]+)\\s+\"([^\"]+)\"\\]");
                Matcher depMatcher = depPattern.matcher(deps);
                
                while (depMatcher.find()) {
                    String name = depMatcher.group(1);
                    String version = depMatcher.group(2);
                    
                    DetectedDependency dependency = DetectedDependency.builder()
                        .name(name)
                        .version(version)
                        .scope("runtime")
                        .packageManager("leiningen")
                        .isDirect(true)
                        .purpose(categorizeDependency(name))
                        .build();
                    
                    analysis.addDependency(dependency);
                }
            }
        } catch (IOException e) {
            log.debug("Failed to read project.clj", e);
        }
    }
    
    private String categorizeDependency(String name) {
        name = name.toLowerCase();
        
        // Web frameworks
        if (name.contains("express") || name.contains("fastify") || name.contains("koa")) {
            return "web framework";
        }
        
        // Databases
        if (name.contains("mysql") || name.contains("postgres") || name.contains("mongodb") ||
            name.contains("redis") || name.contains("sqlite")) {
            return "database driver";
        }
        
        // Testing
        if (name.contains("jest") || name.contains("mocha") || name.contains("pytest") ||
            name.contains("junit") || name.contains("test")) {
            return "testing";
        }
        
        // Authentication
        if (name.contains("jwt") || name.contains("auth") || name.contains("passport")) {
            return "authentication";
        }
        
        // Logging
        if (name.contains("log") || name.contains("winston") || name.contains("bunyan")) {
            return "logging";
        }
        
        // API/HTTP
        if (name.contains("axios") || name.contains("fetch") || name.contains("request")) {
            return "http client";
        }
        
        return "library";
    }
    
    private String categorizeGoDependency(String module) {
        if (module.contains("gin-gonic") || module.contains("echo") || module.contains("fiber")) {
            return "web framework";
        }
        if (module.contains("gorm") || module.contains("mongo-driver") || module.contains("redis")) {
            return "database driver";
        }
        if (module.contains("testify") || module.contains("gomock")) {
            return "testing";
        }
        if (module.contains("jwt") || module.contains("oauth")) {
            return "authentication";
        }
        if (module.contains("logrus") || module.contains("zap")) {
            return "logging";
        }
        return "library";
    }
    
    private String categorizeJavaDependency(String artifactId) {
        if (artifactId.contains("spring")) {
            return "framework";
        }
        if (artifactId.contains("junit") || artifactId.contains("test")) {
            return "testing";
        }
        if (artifactId.contains("jdbc") || artifactId.contains("jpa") || artifactId.contains("hibernate")) {
            return "database";
        }
        if (artifactId.contains("log") || artifactId.contains("slf4j")) {
            return "logging";
        }
        return "library";
    }
    
    private void detectTechnologyFromDependency(String name, String version, ProjectAnalysis analysis) {
        // This method can be extended to detect specific technologies from dependencies
        // For example, detecting that "react" dependency means React framework is used
        // This is handled by other detectors, so we'll keep this minimal
    }
    
    private String extractVersion(String versionSpec) {
        // Extract major version from version specifiers like ">=14.0.0"
        Pattern pattern = Pattern.compile("\\d+(\\.\\d+)?");
        Matcher matcher = pattern.matcher(versionSpec);
        if (matcher.find()) {
            return matcher.group();
        }
        return versionSpec;
    }
    
    private DetectedService createBuildToolService(String name, Integer port) {
        return DetectedService.builder()
            .name(name)
            .type(DetectedService.ServiceType.BUILD_TOOL)
            .confidence(0.9)
            .source(DetectedTechnology.DetectionSource.DEPENDENCY_FILE)
            .defaultPort(port)
            .build();
    }
    
    private DetectedService createTestService(String name) {
        return DetectedService.builder()
            .name(name)
            .type(DetectedService.ServiceType.TESTING)
            .confidence(0.9)
            .source(DetectedTechnology.DetectionSource.DEPENDENCY_FILE)
            .build();
    }
    
    @Override
    public int getPriority() {
        return 75; // Run after service detection
    }
    
    @Override
    public String getName() {
        return "Dependency Analyzer";
    }
}