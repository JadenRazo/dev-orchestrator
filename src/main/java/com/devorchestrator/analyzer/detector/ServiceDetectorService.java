package com.devorchestrator.analyzer.detector;

import com.devorchestrator.analyzer.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
@Slf4j
public class ServiceDetectorService implements TechnologyDetector {
    
    private static final Map<String, ServicePattern> SERVICE_PATTERNS = new HashMap<>();
    
    static {
        initializeMonitoringServices();
        initializeWebServers();
        initializeAPIGateways();
        initializeServiceMesh();
        initializeContainerization();
        initializeCI_CD();
        initializeLogging();
        initializeSchedulers();
        initializeWorkers();
    }
    
    private static void initializeMonitoringServices() {
        // Prometheus
        SERVICE_PATTERNS.put("prometheus", new ServicePattern(
            "Prometheus",
            DetectedService.ServiceType.MONITORING,
            Arrays.asList("prometheus.yml", "prometheus.yaml"),
            Arrays.asList("prom/prometheus", "prometheus/node-exporter", "prometheus/client_golang"),
            Arrays.asList("prometheus", "/metrics", "promhttp", "prometheus_client"),
            9090,
            "prom/prometheus:latest"
        ));
        
        // Grafana
        SERVICE_PATTERNS.put("grafana", new ServicePattern(
            "Grafana",
            DetectedService.ServiceType.MONITORING,
            Arrays.asList("grafana.ini", "dashboards/*.json"),
            Arrays.asList("grafana/grafana"),
            Arrays.asList("grafana", "datasource"),
            3000,
            "grafana/grafana:latest"
        ));
        
        // Jaeger
        SERVICE_PATTERNS.put("jaeger", new ServicePattern(
            "Jaeger",
            DetectedService.ServiceType.MONITORING,
            Arrays.asList("jaeger-*.yaml"),
            Arrays.asList("jaegertracing/all-in-one", "jaeger-client", "opentracing"),
            Arrays.asList("jaeger", "opentracing", "tracing", "spans"),
            16686,
            "jaegertracing/all-in-one:latest"
        ));
        
        // ELK Stack
        SERVICE_PATTERNS.put("elasticsearch", new ServicePattern(
            "Elasticsearch",
            DetectedService.ServiceType.SEARCH,
            Arrays.asList("elasticsearch.yml"),
            Arrays.asList("elasticsearch", "@elastic/elasticsearch"),
            Arrays.asList("elasticsearch", "elastic.co"),
            9200,
            "elasticsearch:8.11.3"
        ));
        
        SERVICE_PATTERNS.put("logstash", new ServicePattern(
            "Logstash",
            DetectedService.ServiceType.LOGGING,
            Arrays.asList("logstash.conf", "pipeline/*.conf"),
            Arrays.asList("logstash"),
            Arrays.asList("logstash"),
            5000,
            "logstash:8.11.3"
        ));
        
        SERVICE_PATTERNS.put("kibana", new ServicePattern(
            "Kibana",
            DetectedService.ServiceType.MONITORING,
            Arrays.asList("kibana.yml"),
            Arrays.asList("kibana"),
            Arrays.asList("kibana"),
            5601,
            "kibana:8.11.3"
        ));
        
        // New Relic
        SERVICE_PATTERNS.put("newrelic", new ServicePattern(
            "New Relic",
            DetectedService.ServiceType.MONITORING,
            Arrays.asList("newrelic.yml", "newrelic.js"),
            Arrays.asList("newrelic"),
            Arrays.asList("newrelic", "NEW_RELIC_"),
            null,
            null
        ));
        
        // DataDog
        SERVICE_PATTERNS.put("datadog", new ServicePattern(
            "DataDog",
            DetectedService.ServiceType.MONITORING,
            Arrays.asList("datadog.yaml"),
            Arrays.asList("datadog/agent", "datadog-api-client"),
            Arrays.asList("datadog", "DD_", "ddtrace"),
            8126,
            "datadog/agent:latest"
        ));
    }
    
    private static void initializeWebServers() {
        // Nginx
        SERVICE_PATTERNS.put("nginx", new ServicePattern(
            "Nginx",
            DetectedService.ServiceType.WEB_SERVER,
            Arrays.asList("nginx.conf", "nginx/*.conf", "sites-available/*", "sites-enabled/*"),
            Arrays.asList("nginx"),
            Arrays.asList("nginx", "server_name", "proxy_pass", "location"),
            80,
            "nginx:alpine"
        ));
        
        // Apache
        SERVICE_PATTERNS.put("apache", new ServicePattern(
            "Apache",
            DetectedService.ServiceType.WEB_SERVER,
            Arrays.asList("httpd.conf", "apache2.conf", ".htaccess"),
            Arrays.asList("httpd", "apache2"),
            Arrays.asList("apache", "httpd", "RewriteRule", "VirtualHost"),
            80,
            "httpd:alpine"
        ));
        
        // Caddy
        SERVICE_PATTERNS.put("caddy", new ServicePattern(
            "Caddy",
            DetectedService.ServiceType.WEB_SERVER,
            Arrays.asList("Caddyfile", "caddy.json"),
            Arrays.asList("caddy"),
            Arrays.asList("caddy", "reverse_proxy"),
            80,
            "caddy:alpine"
        ));
        
        // Traefik
        SERVICE_PATTERNS.put("traefik", new ServicePattern(
            "Traefik",
            DetectedService.ServiceType.API_GATEWAY,
            Arrays.asList("traefik.yml", "traefik.toml"),
            Arrays.asList("traefik"),
            Arrays.asList("traefik", "entryPoints", "routers"),
            80,
            "traefik:latest"
        ));
    }
    
    private static void initializeAPIGateways() {
        // Kong
        SERVICE_PATTERNS.put("kong", new ServicePattern(
            "Kong",
            DetectedService.ServiceType.API_GATEWAY,
            Arrays.asList("kong.conf", "kong.yml"),
            Arrays.asList("kong"),
            Arrays.asList("kong", "kong-gateway"),
            8000,
            "kong:latest"
        ));
        
        // Zuul
        SERVICE_PATTERNS.put("zuul", new ServicePattern(
            "Zuul",
            DetectedService.ServiceType.API_GATEWAY,
            null,
            Arrays.asList("spring-cloud-starter-netflix-zuul"),
            Arrays.asList("@EnableZuulProxy", "zuul.routes"),
            8080,
            null
        ));
        
        // AWS API Gateway
        SERVICE_PATTERNS.put("api-gateway", new ServicePattern(
            "AWS API Gateway",
            DetectedService.ServiceType.API_GATEWAY,
            Arrays.asList("serverless.yml", "sam-template.yml"),
            Arrays.asList("aws-sdk", "serverless"),
            Arrays.asList("apigateway", "x-amazon-apigateway"),
            null,
            null
        ));
    }
    
    private static void initializeServiceMesh() {
        // Istio
        SERVICE_PATTERNS.put("istio", new ServicePattern(
            "Istio",
            DetectedService.ServiceType.SERVICE_MESH,
            Arrays.asList("istio-*.yaml", "virtualservice.yaml", "destinationrule.yaml"),
            Arrays.asList("istio"),
            Arrays.asList("istio", "VirtualService", "DestinationRule"),
            15000,
            null
        ));
        
        // Linkerd
        SERVICE_PATTERNS.put("linkerd", new ServicePattern(
            "Linkerd",
            DetectedService.ServiceType.SERVICE_MESH,
            Arrays.asList("linkerd.yaml"),
            Arrays.asList("linkerd"),
            Arrays.asList("linkerd", "linkerd.io"),
            4191,
            null
        ));
        
        // Consul
        SERVICE_PATTERNS.put("consul", new ServicePattern(
            "Consul",
            DetectedService.ServiceType.SERVICE_DISCOVERY,
            Arrays.asList("consul.json", "consul.hcl"),
            Arrays.asList("consul"),
            Arrays.asList("consul", "service_name"),
            8500,
            "consul:latest"
        ));
    }
    
    private static void initializeContainerization() {
        // Docker
        SERVICE_PATTERNS.put("docker", new ServicePattern(
            "Docker",
            DetectedService.ServiceType.CONTAINER_RUNTIME,
            Arrays.asList("Dockerfile", "docker-compose.yml", "docker-compose.yaml", ".dockerignore"),
            null,
            Arrays.asList("FROM", "EXPOSE", "docker", "container"),
            null,
            null
        ));
        
        // Kubernetes
        SERVICE_PATTERNS.put("kubernetes", new ServicePattern(
            "Kubernetes",
            DetectedService.ServiceType.ORCHESTRATOR,
            Arrays.asList("*.yaml", "*.yml", "kustomization.yaml", "skaffold.yaml"),
            Arrays.asList("kubectl", "kubernetes-client"),
            Arrays.asList("apiVersion:", "kind:", "Deployment", "Service", "ConfigMap"),
            null,
            null
        ));
        
        // Helm
        SERVICE_PATTERNS.put("helm", new ServicePattern(
            "Helm",
            DetectedService.ServiceType.PACKAGE_MANAGER,
            Arrays.asList("Chart.yaml", "values.yaml", "templates/*.yaml"),
            Arrays.asList("helm"),
            Arrays.asList("{{ .Values", "helm.sh"),
            null,
            null
        ));
    }
    
    private static void initializeCI_CD() {
        // Jenkins
        SERVICE_PATTERNS.put("jenkins", new ServicePattern(
            "Jenkins",
            DetectedService.ServiceType.CI_CD,
            Arrays.asList("Jenkinsfile", "jenkins.yml"),
            Arrays.asList("jenkins"),
            Arrays.asList("pipeline", "stage", "jenkins"),
            8080,
            "jenkins/jenkins:lts"
        ));
        
        // GitLab CI
        SERVICE_PATTERNS.put("gitlab-ci", new ServicePattern(
            "GitLab CI",
            DetectedService.ServiceType.CI_CD,
            Arrays.asList(".gitlab-ci.yml"),
            null,
            Arrays.asList("stages:", "script:", "gitlab-ci"),
            null,
            null
        ));
        
        // GitHub Actions
        SERVICE_PATTERNS.put("github-actions", new ServicePattern(
            "GitHub Actions",
            DetectedService.ServiceType.CI_CD,
            Arrays.asList(".github/workflows/*.yml", ".github/workflows/*.yaml"),
            null,
            Arrays.asList("runs-on:", "steps:", "uses:"),
            null,
            null
        ));
        
        // CircleCI
        SERVICE_PATTERNS.put("circleci", new ServicePattern(
            "CircleCI",
            DetectedService.ServiceType.CI_CD,
            Arrays.asList(".circleci/config.yml"),
            null,
            Arrays.asList("version:", "orbs:", "workflows:"),
            null,
            null
        ));
    }
    
    private static void initializeLogging() {
        // Fluentd
        SERVICE_PATTERNS.put("fluentd", new ServicePattern(
            "Fluentd",
            DetectedService.ServiceType.LOGGING,
            Arrays.asList("fluent.conf", "td-agent.conf"),
            Arrays.asList("fluentd", "fluent-logger"),
            Arrays.asList("fluentd", "<source>", "<match>"),
            24224,
            "fluentd:latest"
        ));
        
        // Logrus (Go logging)
        SERVICE_PATTERNS.put("logrus", new ServicePattern(
            "Logrus",
            DetectedService.ServiceType.LOGGING,
            null,
            Arrays.asList("github.com/sirupsen/logrus"),
            Arrays.asList("logrus", "WithFields", "log.Info"),
            null,
            null
        ));
        
        // Winston (Node.js logging)
        SERVICE_PATTERNS.put("winston", new ServicePattern(
            "Winston",
            DetectedService.ServiceType.LOGGING,
            null,
            Arrays.asList("winston"),
            Arrays.asList("winston", "createLogger", "transports"),
            null,
            null
        ));
    }
    
    private static void initializeSchedulers() {
        // Cron
        SERVICE_PATTERNS.put("cron", new ServicePattern(
            "Cron",
            DetectedService.ServiceType.SCHEDULER,
            Arrays.asList("crontab", "*.cron"),
            Arrays.asList("node-cron", "cron"),
            Arrays.asList("cron", "* * * * *", "crontab"),
            null,
            null
        ));
        
        // Celery
        SERVICE_PATTERNS.put("celery", new ServicePattern(
            "Celery",
            DetectedService.ServiceType.TASK_QUEUE,
            Arrays.asList("celeryconfig.py", "celery.py"),
            Arrays.asList("celery"),
            Arrays.asList("celery", "@task", "@shared_task", "Celery"),
            null,
            null
        ));
        
        // Sidekiq
        SERVICE_PATTERNS.put("sidekiq", new ServicePattern(
            "Sidekiq",
            DetectedService.ServiceType.TASK_QUEUE,
            Arrays.asList("sidekiq.yml"),
            Arrays.asList("sidekiq"),
            Arrays.asList("sidekiq", "perform_async", "Worker"),
            null,
            null
        ));
        
        // Bull (Node.js queue)
        SERVICE_PATTERNS.put("bull", new ServicePattern(
            "Bull",
            DetectedService.ServiceType.TASK_QUEUE,
            null,
            Arrays.asList("bull", "bullmq"),
            Arrays.asList("Bull", "Queue", "process"),
            null,
            null
        ));
    }
    
    private static void initializeWorkers() {
        // Background workers pattern
        SERVICE_PATTERNS.put("worker", new ServicePattern(
            "Background Worker",
            DetectedService.ServiceType.WORKER,
            Arrays.asList("worker.js", "worker.py", "worker.go", "cmd/worker/*"),
            null,
            Arrays.asList("worker", "process", "consume", "subscribe"),
            null,
            null
        ));
        
        // WebSocket servers
        SERVICE_PATTERNS.put("websocket", new ServicePattern(
            "WebSocket Server",
            DetectedService.ServiceType.WEBSOCKET,
            null,
            Arrays.asList("ws", "socket.io", "gorilla/websocket", "websockets"),
            Arrays.asList("WebSocket", "ws://", "wss://", "socket.on", "io.on"),
            null,
            null
        ));
    }
    
    @Override
    public void detect(Path projectPath, ProjectAnalysis analysis) {
        log.info("Starting service detection for project: {}", projectPath);
        
        Map<String, ServiceInfo> detectedServices = new HashMap<>();
        
        // Scan configuration files
        scanConfigurationFiles(projectPath, detectedServices);
        
        // Scan Docker and orchestration files
        scanContainerFiles(projectPath, detectedServices);
        
        // Scan source code
        scanSourceCode(projectPath, detectedServices);
        
        // Check CI/CD configurations
        scanCICDFiles(projectPath, detectedServices);
        
        // Scan dependencies
        scanDependencies(projectPath, detectedServices);
        
        // Convert to DetectedService objects
        detectedServices.values().stream()
            .map(this::createDetectedService)
            .forEach(analysis::addService);
        
        log.info("Detected {} services in project", detectedServices.size());
    }
    
    private void scanConfigurationFiles(Path projectPath, Map<String, ServiceInfo> detected) {
        try (Stream<Path> paths = Files.walk(projectPath)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> isConfigFile(path))
                .forEach(file -> analyzeConfigFile(file, detected));
        } catch (IOException e) {
            log.debug("Error scanning configuration files", e);
        }
    }
    
    private boolean isConfigFile(Path file) {
        String fileName = file.getFileName().toString();
        String pathStr = file.toString();
        
        // Skip common non-source directories
        if (pathStr.contains("node_modules") || pathStr.contains(".git") ||
            pathStr.contains("vendor") || pathStr.contains("build")) {
            return false;
        }
        
        return fileName.endsWith(".conf") || fileName.endsWith(".yml") ||
               fileName.endsWith(".yaml") || fileName.endsWith(".json") ||
               fileName.endsWith(".toml") || fileName.endsWith(".hcl") ||
               fileName.endsWith(".ini");
    }
    
    private void analyzeConfigFile(Path file, Map<String, ServiceInfo> detected) {
        String fileName = file.getFileName().toString();
        
        for (Map.Entry<String, ServicePattern> entry : SERVICE_PATTERNS.entrySet()) {
            ServicePattern pattern = entry.getValue();
            
            if (pattern.configFiles != null) {
                for (String configPattern : pattern.configFiles) {
                    if (matchesFilePattern(fileName, file, configPattern)) {
                        addDetection(detected, entry.getKey(), pattern, 0.8);
                        
                        // Also check file content
                        try {
                            String content = Files.readString(file);
                            if (pattern.codePatterns != null) {
                                for (String codePattern : pattern.codePatterns) {
                                    if (content.contains(codePattern)) {
                                        addDetection(detected, entry.getKey(), pattern, 0.2);
                                    }
                                }
                            }
                        } catch (IOException e) {
                            // Ignore
                        }
                    }
                }
            }
        }
    }
    
    private boolean matchesFilePattern(String fileName, Path file, String pattern) {
        if (pattern.contains("*")) {
            // Handle wildcards
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return fileName.matches(regex) || file.toString().contains(pattern.replace("*", ""));
        }
        return fileName.equals(pattern);
    }
    
    private void scanContainerFiles(Path projectPath, Map<String, ServiceInfo> detected) {
        // Check for Docker files
        if (Files.exists(projectPath.resolve("Dockerfile")) ||
            Files.exists(projectPath.resolve("docker-compose.yml")) ||
            Files.exists(projectPath.resolve("docker-compose.yaml"))) {
            addDetection(detected, "docker", SERVICE_PATTERNS.get("docker"), 0.9);
        }
        
        // Check for Kubernetes files
        try (Stream<Path> paths = Files.walk(projectPath)) {
            boolean hasK8sFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString();
                    return (name.endsWith(".yaml") || name.endsWith(".yml")) &&
                           !name.contains("docker-compose");
                })
                .anyMatch(file -> {
                    try {
                        String content = Files.readString(file);
                        return content.contains("apiVersion:") && content.contains("kind:");
                    } catch (IOException e) {
                        return false;
                    }
                });
            
            if (hasK8sFiles) {
                addDetection(detected, "kubernetes", SERVICE_PATTERNS.get("kubernetes"), 0.8);
            }
        } catch (IOException e) {
            log.debug("Error checking for Kubernetes files", e);
        }
    }
    
    private void scanSourceCode(Path projectPath, Map<String, ServiceInfo> detected) {
        try (Stream<Path> paths = Files.walk(projectPath)) {
            paths.filter(Files::isRegularFile)
                .filter(this::isSourceFile)
                .limit(100) // Limit for performance
                .forEach(file -> analyzeSourceFile(file, detected));
        } catch (IOException e) {
            log.debug("Error scanning source code", e);
        }
    }
    
    private boolean isSourceFile(Path file) {
        String fileName = file.getFileName().toString();
        String pathStr = file.toString();
        
        // Skip common non-source directories
        if (pathStr.contains("node_modules") || pathStr.contains(".git") ||
            pathStr.contains("vendor") || pathStr.contains("build") ||
            pathStr.contains("dist") || pathStr.contains("target")) {
            return false;
        }
        
        return fileName.endsWith(".java") || fileName.endsWith(".py") ||
               fileName.endsWith(".js") || fileName.endsWith(".ts") ||
               fileName.endsWith(".go") || fileName.endsWith(".rb") ||
               fileName.endsWith(".php") || fileName.endsWith(".cs");
    }
    
    private void analyzeSourceFile(Path file, Map<String, ServiceInfo> detected) {
        try {
            String content = Files.readString(file);
            
            for (Map.Entry<String, ServicePattern> entry : SERVICE_PATTERNS.entrySet()) {
                ServicePattern pattern = entry.getValue();
                
                if (pattern.codePatterns != null) {
                    for (String codePattern : pattern.codePatterns) {
                        if (content.contains(codePattern)) {
                            addDetection(detected, entry.getKey(), pattern, 0.5);
                        }
                    }
                }
                
                // Check imports/dependencies
                if (pattern.dependencies != null) {
                    for (String dep : pattern.dependencies) {
                        if (content.contains(dep)) {
                            addDetection(detected, entry.getKey(), pattern, 0.6);
                        }
                    }
                }
            }
            
            // Special checks for workers
            String fileName = file.getFileName().toString().toLowerCase();
            if (fileName.contains("worker") || fileName.contains("consumer") ||
                fileName.contains("processor") || fileName.contains("handler")) {
                addDetection(detected, "worker", SERVICE_PATTERNS.get("worker"), 0.5);
            }
            
        } catch (IOException e) {
            // Ignore
        }
    }
    
    private void scanCICDFiles(Path projectPath, Map<String, ServiceInfo> detected) {
        // GitHub Actions
        Path githubWorkflows = projectPath.resolve(".github/workflows");
        if (Files.exists(githubWorkflows) && Files.isDirectory(githubWorkflows)) {
            addDetection(detected, "github-actions", SERVICE_PATTERNS.get("github-actions"), 0.9);
        }
        
        // GitLab CI
        if (Files.exists(projectPath.resolve(".gitlab-ci.yml"))) {
            addDetection(detected, "gitlab-ci", SERVICE_PATTERNS.get("gitlab-ci"), 0.9);
        }
        
        // Jenkins
        if (Files.exists(projectPath.resolve("Jenkinsfile"))) {
            addDetection(detected, "jenkins", SERVICE_PATTERNS.get("jenkins"), 0.9);
        }
        
        // CircleCI
        if (Files.exists(projectPath.resolve(".circleci/config.yml"))) {
            addDetection(detected, "circleci", SERVICE_PATTERNS.get("circleci"), 0.9);
        }
    }
    
    private void scanDependencies(Path projectPath, Map<String, ServiceInfo> detected) {
        // Check package.json
        Path packageJson = projectPath.resolve("package.json");
        if (Files.exists(packageJson)) {
            checkDependencyFile(packageJson, detected);
        }
        
        // Check requirements.txt
        Path requirements = projectPath.resolve("requirements.txt");
        if (Files.exists(requirements)) {
            checkDependencyFile(requirements, detected);
        }
        
        // Check go.mod
        Path goMod = projectPath.resolve("go.mod");
        if (Files.exists(goMod)) {
            checkDependencyFile(goMod, detected);
        }
        
        // Check pom.xml
        Path pomXml = projectPath.resolve("pom.xml");
        if (Files.exists(pomXml)) {
            checkDependencyFile(pomXml, detected);
        }
    }
    
    private void checkDependencyFile(Path depFile, Map<String, ServiceInfo> detected) {
        try {
            String content = Files.readString(depFile);
            
            for (Map.Entry<String, ServicePattern> entry : SERVICE_PATTERNS.entrySet()) {
                ServicePattern pattern = entry.getValue();
                if (pattern.dependencies != null) {
                    for (String dep : pattern.dependencies) {
                        if (content.contains(dep)) {
                            addDetection(detected, entry.getKey(), pattern, 0.7);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.debug("Failed to read dependency file: {}", depFile, e);
        }
    }
    
    private void addDetection(Map<String, ServiceInfo> detected, String key,
                             ServicePattern pattern, double confidence) {
        detected.compute(key, (k, existing) -> {
            if (existing == null) {
                return new ServiceInfo(pattern, confidence);
            } else {
                existing.increaseConfidence(confidence);
                return existing;
            }
        });
    }
    
    private DetectedService createDetectedService(ServiceInfo info) {
        ServicePattern pattern = info.pattern;
        
        return DetectedService.builder()
            .name(pattern.name)
            .type(pattern.type)
            .confidence(Math.min(1.0, info.confidence))
            .source(DetectedTechnology.DetectionSource.CONFIG_FILE)
            .defaultPort(pattern.defaultPort)
            .dockerImage(pattern.dockerImage)
            .build();
    }
    
    @Override
    public int getPriority() {
        return 80; // Run after database detection
    }
    
    @Override
    public String getName() {
        return "Service Detector";
    }
    
    private static class ServicePattern {
        final String name;
        final DetectedService.ServiceType type;
        final List<String> configFiles;
        final List<String> dependencies;
        final List<String> codePatterns;
        final Integer defaultPort;
        final String dockerImage;
        
        ServicePattern(String name, DetectedService.ServiceType type,
                      List<String> configFiles, List<String> dependencies,
                      List<String> codePatterns, Integer defaultPort, String dockerImage) {
            this.name = name;
            this.type = type;
            this.configFiles = configFiles;
            this.dependencies = dependencies;
            this.codePatterns = codePatterns;
            this.defaultPort = defaultPort;
            this.dockerImage = dockerImage;
        }
    }
    
    private static class ServiceInfo {
        final ServicePattern pattern;
        double confidence;
        
        ServiceInfo(ServicePattern pattern, double confidence) {
            this.pattern = pattern;
            this.confidence = confidence;
        }
        
        void increaseConfidence(double amount) {
            confidence = Math.min(1.0, confidence + amount * 0.5);
        }
    }
}