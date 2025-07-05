package com.devorchestrator.service;

import com.devorchestrator.analyzer.model.*;
import com.devorchestrator.entity.*;
import com.devorchestrator.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class TemplateGeneratorService {
    
    private final ProjectAnalysisRepository analysisRepository;
    private final EnvironmentTemplateRepository templateRepository;
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;
    private final SecureRandom secureRandom;
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
    
    public TemplateGeneratorService(ProjectAnalysisRepository analysisRepository,
                                  EnvironmentTemplateRepository templateRepository,
                                  ObjectMapper jsonMapper) {
        this.analysisRepository = analysisRepository;
        this.templateRepository = templateRepository;
        this.jsonMapper = jsonMapper;
        this.yamlMapper = new ObjectMapper(new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        this.secureRandom = new SecureRandom();
    }
    
    /**
     * Generate a secure random password for development environments
     */
    private String generateSecurePassword() {
        StringBuilder password = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            password.append(CHARS.charAt(secureRandom.nextInt(CHARS.length())));
        }
        return password.toString();
    }
    
    /**
     * Generate Docker Compose template from project analysis
     */
    public EnvironmentTemplate generateDockerComposeTemplate(String projectId, String analysisId) {
        log.info("Generating Docker Compose template for project: {}", projectId);
        
        ProjectAnalysisEntity analysis = analysisRepository.findById(analysisId)
            .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));
        
        Map<String, Object> dockerCompose = new LinkedHashMap<>();
        dockerCompose.put("version", "3.8");
        
        Map<String, Object> services = new LinkedHashMap<>();
        Map<String, Object> volumes = new LinkedHashMap<>();
        Map<String, Object> networks = new LinkedHashMap<>();
        
        // Generate main application service
        Map<String, Object> appService = generateApplicationService(analysis);
        services.put("app", appService);
        
        // Generate database services
        List<DetectedDatabase> databases = analysis.getDetectedDatabases();
        for (DetectedDatabase db : databases) {
            Map<String, Object> dbService = generateDatabaseService(db);
            services.put(db.getDatabaseType().toLowerCase(), dbService);
            volumes.put(db.getDatabaseType().toLowerCase() + "_data", new HashMap<>());
        }
        
        // Generate cache services
        if (hasRedis(analysis)) {
            services.put("redis", generateRedisService());
            volumes.put("redis_data", new HashMap<>());
        }
        
        // Generate monitoring services if detected
        if (hasMonitoring(analysis)) {
            services.put("prometheus", generatePrometheusService());
            services.put("grafana", generateGrafanaService());
            volumes.put("prometheus_data", new HashMap<>());
            volumes.put("grafana_data", new HashMap<>());
        }
        
        // Add services, volumes, and networks to compose
        dockerCompose.put("services", services);
        if (!volumes.isEmpty()) {
            dockerCompose.put("volumes", volumes);
        }
        
        // Default network configuration
        networks.put("app_network", Map.of("driver", "bridge"));
        dockerCompose.put("networks", networks);
        
        // Convert to YAML
        String dockerComposeYaml = convertToYaml(dockerCompose);
        
        // Create environment template
        EnvironmentTemplate template = EnvironmentTemplate.builder()
            .id(UUID.randomUUID().toString())
            .name(analysis.getProject().getName() + " - Docker Compose")
            .description("Auto-generated Docker Compose template")
            .dockerComposeContent(dockerComposeYaml)
            .infrastructureType(InfrastructureProvider.DOCKER)
            .memoryLimitMb(calculateMemoryLimit(analysis))
            .cpuLimit(calculateCpuLimit(analysis))
            .isPublic(false)
            .build();
        
        // Set environment variables
        template.setEnvironmentVariables(generateEnvironmentVariables(analysis));
        
        // Set exposed ports
        Set<Integer> exposedPorts = extractExposedPorts(analysis);
        template.setExposedPorts(exposedPorts);
        
        return templateRepository.save(template);
    }
    
    /**
     * Generate Kubernetes manifests from project analysis
     */
    public String generateKubernetesManifests(String projectId, String analysisId) {
        log.info("Generating Kubernetes manifests for project: {}", projectId);
        
        ProjectAnalysisEntity analysis = analysisRepository.findById(analysisId)
            .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));
        
        List<Map<String, Object>> manifests = new ArrayList<>();
        
        // Generate namespace
        manifests.add(generateK8sNamespace(analysis.getProject().getName()));
        
        // Generate config maps
        manifests.add(generateK8sConfigMap(analysis));
        
        // Generate deployments
        manifests.add(generateK8sDeployment(analysis, "app"));
        
        // Generate services
        manifests.add(generateK8sService(analysis, "app"));
        
        // Generate database deployments
        for (DetectedDatabase db : analysis.getDetectedDatabases()) {
            manifests.add(generateK8sDatabaseDeployment(db));
            manifests.add(generateK8sDatabaseService(db));
            manifests.add(generateK8sPersistentVolumeClaim(db));
        }
        
        // Generate ingress
        if (hasWebFramework(analysis)) {
            manifests.add(generateK8sIngress(analysis));
        }
        
        // Convert to YAML with document separators
        return manifests.stream()
            .map(this::convertToYaml)
            .collect(Collectors.joining("\n---\n"));
    }
    
    /**
     * Generate Terraform configuration from project analysis
     */
    public String generateTerraformConfiguration(String projectId, String analysisId,
                                                InfrastructureProvider provider) {
        log.info("Generating Terraform configuration for project: {} provider: {}", 
                projectId, provider);
        
        ProjectAnalysisEntity analysis = analysisRepository.findById(analysisId)
            .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));
        
        Map<String, Object> terraform = new LinkedHashMap<>();
        
        // Provider configuration
        terraform.put("terraform", Map.of(
            "required_providers", Map.of(
                provider.name().toLowerCase(), Map.of(
                    "source", getProviderSource(provider),
                    "version", getProviderVersion(provider)
                )
            )
        ));
        
        // Provider block
        terraform.put("provider", Map.of(
            provider.name().toLowerCase(), getProviderConfig(provider)
        ));
        
        // Variables
        terraform.put("variable", generateTerraformVariables(analysis));
        
        // Resources
        Map<String, Object> resources = new LinkedHashMap<>();
        
        switch (provider) {
            case AWS:
                resources.putAll(generateAWSResources(analysis));
                break;
            case AZURE:
                resources.putAll(generateAzureResources(analysis));
                break;
            case GCP:
                resources.putAll(generateGCPResources(analysis));
                break;
            case DIGITAL_OCEAN:
                resources.putAll(generateDigitalOceanResources(analysis));
                break;
        }
        
        terraform.put("resource", resources);
        
        // Outputs
        terraform.put("output", generateTerraformOutputs(provider));
        
        // Convert to HCL format
        return convertToHCL(terraform);
    }
    
    /**
     * Generate application service for Docker Compose
     */
    private Map<String, Object> generateApplicationService(ProjectAnalysisEntity analysis) {
        Map<String, Object> service = new LinkedHashMap<>();
        
        // Determine base image
        String primaryLanguage = analysis.getPrimaryLanguage();
        DetectedLanguage language = analysis.getDetectedLanguages().stream()
            .filter(l -> l.getName().equals(primaryLanguage))
            .findFirst()
            .orElse(null);
        
        if (language != null && language.getDockerImage() != null) {
            service.put("image", language.getDockerImage());
        } else {
            // Use Dockerfile if no standard image
            service.put("build", Map.of(
                "context", ".",
                "dockerfile", "Dockerfile"
            ));
        }
        
        // Container name
        service.put("container_name", analysis.getProject().getName() + "_app");
        
        // Environment variables
        List<String> environment = new ArrayList<>();
        environment.add("NODE_ENV=development");
        
        // Add database connections
        for (DetectedDatabase db : analysis.getDetectedDatabases()) {
            String dbType = db.getDatabaseType().toLowerCase();
            environment.add(db.getConnectionVariable() + "=" + 
                generateConnectionString(db, dbType));
        }
        
        service.put("environment", environment);
        
        // Ports
        List<String> ports = new ArrayList<>();
        if (hasWebFramework(analysis)) {
            ports.add("3000:3000");
        }
        service.put("ports", ports);
        
        // Networks
        service.put("networks", Arrays.asList("app_network"));
        
        // Volumes
        List<String> volumes = new ArrayList<>();
        volumes.add("./:/app");
        service.put("volumes", volumes);
        
        // Depends on
        List<String> dependsOn = analysis.getDetectedDatabases().stream()
            .map(db -> db.getDatabaseType().toLowerCase())
            .collect(Collectors.toList());
        if (!dependsOn.isEmpty()) {
            service.put("depends_on", dependsOn);
        }
        
        // Restart policy
        service.put("restart", "unless-stopped");
        
        return service;
    }
    
    /**
     * Generate database service for Docker Compose
     */
    private Map<String, Object> generateDatabaseService(DetectedDatabase db) {
        Map<String, Object> service = new LinkedHashMap<>();
        
        // Generate secure passwords for each database
        String dbPassword = generateSecurePassword();
        
        switch (db.getDatabaseType().toUpperCase()) {
            case "POSTGRESQL":
                service.put("image", "postgres:15-alpine");
                service.put("container_name", "postgres");
                service.put("environment", Arrays.asList(
                    "POSTGRES_DB=" + (db.getDatabaseName() != null ? db.getDatabaseName() : "app_db"),
                    "POSTGRES_USER=postgres",
                    "POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-" + dbPassword + "}",
                    "# WARNING: This is a development configuration. Use environment variables in production."
                ));
                service.put("ports", Arrays.asList("5432:5432"));
                service.put("volumes", Arrays.asList("postgres_data:/var/lib/postgresql/data"));
                break;
                
            case "MYSQL":
                service.put("image", "mysql:8.0");
                service.put("container_name", "mysql");
                service.put("environment", Arrays.asList(
                    "MYSQL_DATABASE=" + (db.getDatabaseName() != null ? db.getDatabaseName() : "app_db"),
                    "MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD:-" + dbPassword + "}",
                    "# WARNING: This is a development configuration. Use environment variables in production."
                ));
                service.put("ports", Arrays.asList("3306:3306"));
                service.put("volumes", Arrays.asList("mysql_data:/var/lib/mysql"));
                break;
                
            case "MONGODB":
                service.put("image", "mongo:6.0");
                service.put("container_name", "mongodb");
                service.put("environment", Arrays.asList(
                    "MONGO_INITDB_ROOT_USERNAME=mongo",
                    "MONGO_INITDB_ROOT_PASSWORD=${MONGO_ROOT_PASSWORD:-" + dbPassword + "}",
                    "# WARNING: This is a development configuration. Use environment variables in production."
                ));
                service.put("ports", Arrays.asList("27017:27017"));
                service.put("volumes", Arrays.asList("mongodb_data:/data/db"));
                break;
        }
        
        service.put("networks", Arrays.asList("app_network"));
        service.put("restart", "unless-stopped");
        
        return service;
    }
    
    /**
     * Generate Redis service
     */
    private Map<String, Object> generateRedisService() {
        return Map.of(
            "image", "redis:7-alpine",
            "container_name", "redis",
            "ports", Arrays.asList("6379:6379"),
            "volumes", Arrays.asList("redis_data:/data"),
            "networks", Arrays.asList("app_network"),
            "restart", "unless-stopped"
        );
    }
    
    /**
     * Generate Prometheus service
     */
    private Map<String, Object> generatePrometheusService() {
        return Map.of(
            "image", "prom/prometheus:latest",
            "container_name", "prometheus",
            "ports", Arrays.asList("9090:9090"),
            "volumes", Arrays.asList(
                "./prometheus.yml:/etc/prometheus/prometheus.yml",
                "prometheus_data:/prometheus"
            ),
            "networks", Arrays.asList("app_network"),
            "restart", "unless-stopped"
        );
    }
    
    /**
     * Generate Grafana service
     */
    private Map<String, Object> generateGrafanaService() {
        return Map.of(
            "image", "grafana/grafana:latest",
            "container_name", "grafana",
            "ports", Arrays.asList("3001:3000"),
            "volumes", Arrays.asList("grafana_data:/var/lib/grafana"),
            "networks", Arrays.asList("app_network"),
            "environment", Arrays.asList(
                "GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD:-" + generateSecurePassword() + "}",
                "# WARNING: This is a development configuration. Use environment variables in production."
            ),
            "restart", "unless-stopped"
        );
    }
    
    /**
     * Generate Kubernetes namespace
     */
    private Map<String, Object> generateK8sNamespace(String projectName) {
        return Map.of(
            "apiVersion", "v1",
            "kind", "Namespace",
            "metadata", Map.of(
                "name", projectName.toLowerCase().replaceAll("[^a-z0-9-]", "-")
            )
        );
    }
    
    /**
     * Generate Kubernetes ConfigMap
     */
    private Map<String, Object> generateK8sConfigMap(ProjectAnalysisEntity analysis) {
        Map<String, Object> data = new HashMap<>();
        data.put("NODE_ENV", "production");
        
        return Map.of(
            "apiVersion", "v1",
            "kind", "ConfigMap",
            "metadata", Map.of(
                "name", analysis.getProject().getName() + "-config",
                "namespace", analysis.getProject().getName().toLowerCase()
            ),
            "data", data
        );
    }
    
    /**
     * Generate Kubernetes Deployment
     */
    private Map<String, Object> generateK8sDeployment(ProjectAnalysisEntity analysis, String name) {
        String appName = analysis.getProject().getName().toLowerCase();
        
        Map<String, Object> deployment = new LinkedHashMap<>();
        deployment.put("apiVersion", "apps/v1");
        deployment.put("kind", "Deployment");
        deployment.put("metadata", Map.of(
            "name", appName + "-" + name,
            "namespace", appName
        ));
        
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("replicas", 3);
        spec.put("selector", Map.of(
            "matchLabels", Map.of("app", appName)
        ));
        
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("metadata", Map.of(
            "labels", Map.of("app", appName)
        ));
        
        Map<String, Object> containerSpec = new LinkedHashMap<>();
        containerSpec.put("name", name);
        containerSpec.put("image", appName + ":latest");
        containerSpec.put("ports", Arrays.asList(
            Map.of("containerPort", 3000)
        ));
        containerSpec.put("envFrom", Arrays.asList(
            Map.of("configMapRef", Map.of("name", appName + "-config"))
        ));
        
        template.put("spec", Map.of(
            "containers", Arrays.asList(containerSpec)
        ));
        
        spec.put("template", template);
        deployment.put("spec", spec);
        
        return deployment;
    }
    
    /**
     * Generate Kubernetes Service
     */
    private Map<String, Object> generateK8sService(ProjectAnalysisEntity analysis, String name) {
        String appName = analysis.getProject().getName().toLowerCase();
        
        return Map.of(
            "apiVersion", "v1",
            "kind", "Service",
            "metadata", Map.of(
                "name", appName + "-" + name,
                "namespace", appName
            ),
            "spec", Map.of(
                "selector", Map.of("app", appName),
                "ports", Arrays.asList(
                    Map.of(
                        "protocol", "TCP",
                        "port", 80,
                        "targetPort", 3000
                    )
                ),
                "type", "ClusterIP"
            )
        );
    }
    
    /**
     * Generate connection string for database
     */
    private String generateConnectionString(DetectedDatabase db, String containerName) {
        String dbName = db.getDatabaseName() != null ? db.getDatabaseName() : "app_db";
        switch (db.getDatabaseType().toUpperCase()) {
            case "POSTGRESQL":
                return "postgresql://postgres:${POSTGRES_PASSWORD}@" + containerName + ":5432/" + dbName;
            case "MYSQL":
                return "mysql://root:${MYSQL_ROOT_PASSWORD}@" + containerName + ":3306/" + dbName;
            case "MONGODB":
                return "mongodb://mongo:${MONGO_ROOT_PASSWORD}@" + containerName + ":27017/" + dbName;
            default:
                return "";
        }
    }
    
    /**
     * Helper methods
     */
    private boolean hasRedis(ProjectAnalysisEntity analysis) {
        return analysis.getDetectedDatabases().stream()
            .anyMatch(db -> db.getDatabaseType().equalsIgnoreCase("REDIS")) ||
            analysis.getDetectedServices().stream()
            .anyMatch(s -> s.getServiceType().equalsIgnoreCase("CACHE"));
    }
    
    private boolean hasMonitoring(ProjectAnalysisEntity analysis) {
        return analysis.getDetectedServices().stream()
            .anyMatch(s -> s.getServiceType().equalsIgnoreCase("MONITORING"));
    }
    
    private boolean hasWebFramework(ProjectAnalysisEntity analysis) {
        return analysis.getDetectedFrameworks().stream()
            .anyMatch(f -> f.getCategory().equalsIgnoreCase("web"));
    }
    
    private String convertToYaml(Object data) {
        try {
            return yamlMapper.writeValueAsString(data);
        } catch (IOException e) {
            log.error("Error converting to YAML", e);
            return "";
        }
    }
    
    private String convertToHCL(Map<String, Object> terraform) {
        // Simplified HCL generation
        StringBuilder hcl = new StringBuilder();
        terraform.forEach((key, value) -> {
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) value;
                map.forEach((k, v) -> {
                    hcl.append(key).append(" \"").append(k).append("\" {\n");
                    appendHCLContent(hcl, v, 2);
                    hcl.append("}\n\n");
                });
            }
        });
        return hcl.toString();
    }
    
    private void appendHCLContent(StringBuilder hcl, Object value, int indent) {
        String indentStr = "  ".repeat(indent);
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            map.forEach((k, v) -> {
                hcl.append(indentStr).append(k).append(" = ");
                if (v instanceof String) {
                    hcl.append("\"").append(v).append("\"");
                } else {
                    hcl.append(v);
                }
                hcl.append("\n");
            });
        }
    }
    
    private Integer calculateMemoryLimit(ProjectAnalysisEntity analysis) {
        // Base memory plus additional for each service
        int baseMemory = 512;
        int dbCount = analysis.getDetectedDatabases().size();
        int serviceCount = analysis.getDetectedServices().size();
        return baseMemory + (dbCount * 512) + (serviceCount * 256);
    }
    
    private Double calculateCpuLimit(ProjectAnalysisEntity analysis) {
        // Base CPU plus additional for each service
        double baseCpu = 1.0;
        int dbCount = analysis.getDetectedDatabases().size();
        int serviceCount = analysis.getDetectedServices().size();
        return baseCpu + (dbCount * 0.5) + (serviceCount * 0.25);
    }
    
    private Set<Integer> extractExposedPorts(ProjectAnalysisEntity analysis) {
        Set<Integer> ports = new HashSet<>();
        ports.add(3000); // Default app port
        
        // Add database ports
        for (DetectedDatabase db : analysis.getDetectedDatabases()) {
            switch (db.getDatabaseType().toUpperCase()) {
                case "POSTGRESQL":
                    ports.add(5432);
                    break;
                case "MYSQL":
                    ports.add(3306);
                    break;
                case "MONGODB":
                    ports.add(27017);
                    break;
                case "REDIS":
                    ports.add(6379);
                    break;
            }
        }
        
        return ports;
    }
    
    private String generateEnvironmentVariables(ProjectAnalysisEntity analysis) {
        Map<String, String> envVars = new HashMap<>();
        envVars.put("NODE_ENV", "development");
        
        try {
            return jsonMapper.writeValueAsString(envVars);
        } catch (IOException e) {
            return "{}";
        }
    }
    
    // Provider-specific methods
    private String getProviderSource(InfrastructureProvider provider) {
        switch (provider) {
            case AWS: return "hashicorp/aws";
            case AZURE: return "hashicorp/azurerm";
            case GCP: return "hashicorp/google";
            case DIGITAL_OCEAN: return "digitalocean/digitalocean";
            default: return "";
        }
    }
    
    private String getProviderVersion(InfrastructureProvider provider) {
        switch (provider) {
            case AWS: return "~> 5.0";
            case AZURE: return "~> 3.0";
            case GCP: return "~> 4.0";
            case DIGITAL_OCEAN: return "~> 2.0";
            default: return "";
        }
    }
    
    private Map<String, Object> getProviderConfig(InfrastructureProvider provider) {
        Map<String, Object> config = new HashMap<>();
        switch (provider) {
            case AWS:
                config.put("region", "${var.aws_region}");
                break;
            case AZURE:
                config.put("features", new HashMap<>());
                break;
            case GCP:
                config.put("project", "${var.gcp_project}");
                config.put("region", "${var.gcp_region}");
                break;
            case DIGITAL_OCEAN:
                config.put("token", "${var.do_token}");
                break;
        }
        return config;
    }
    
    private Map<String, Object> generateTerraformVariables(ProjectAnalysisEntity analysis) {
        Map<String, Object> variables = new LinkedHashMap<>();
        
        variables.put("project_name", Map.of(
            "description", "Project name",
            "type", "string",
            "default", analysis.getProject().getName()
        ));
        
        variables.put("environment", Map.of(
            "description", "Environment name",
            "type", "string",
            "default", "development"
        ));
        
        return variables;
    }
    
    private Map<String, Object> generateAWSResources(ProjectAnalysisEntity analysis) {
        Map<String, Object> resources = new LinkedHashMap<>();
        
        // VPC
        resources.put("aws_vpc \"main\"", Map.of(
            "cidr_block", "10.0.0.0/16",
            "tags", Map.of("Name", "${var.project_name}-vpc")
        ));
        
        // EC2 Instance
        resources.put("aws_instance \"app\"", Map.of(
            "ami", "ami-0c55b159cbfafe1f0",
            "instance_type", "t3.medium",
            "tags", Map.of("Name", "${var.project_name}-app")
        ));
        
        return resources;
    }
    
    private Map<String, Object> generateAzureResources(ProjectAnalysisEntity analysis) {
        // Similar implementation for Azure
        return new LinkedHashMap<>();
    }
    
    private Map<String, Object> generateGCPResources(ProjectAnalysisEntity analysis) {
        // Similar implementation for GCP
        return new LinkedHashMap<>();
    }
    
    private Map<String, Object> generateDigitalOceanResources(ProjectAnalysisEntity analysis) {
        // Similar implementation for Digital Ocean
        return new LinkedHashMap<>();
    }
    
    private Map<String, Object> generateTerraformOutputs(InfrastructureProvider provider) {
        Map<String, Object> outputs = new LinkedHashMap<>();
        
        outputs.put("app_ip", Map.of(
            "description", "Application server IP address",
            "value", provider == InfrastructureProvider.AWS ? 
                "${aws_instance.app.public_ip}" : ""
        ));
        
        return outputs;
    }
    
    private Map<String, Object> generateK8sDatabaseDeployment(DetectedDatabase db) {
        // Implementation for K8s database deployment
        return new LinkedHashMap<>();
    }
    
    private Map<String, Object> generateK8sDatabaseService(DetectedDatabase db) {
        // Implementation for K8s database service
        return new LinkedHashMap<>();
    }
    
    private Map<String, Object> generateK8sPersistentVolumeClaim(DetectedDatabase db) {
        // Implementation for K8s PVC
        return new LinkedHashMap<>();
    }
    
    private Map<String, Object> generateK8sIngress(ProjectAnalysisEntity analysis) {
        // Implementation for K8s ingress
        return new LinkedHashMap<>();
    }
}