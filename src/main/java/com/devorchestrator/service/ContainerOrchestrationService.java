package com.devorchestrator.service;

import com.devorchestrator.entity.ContainerInstance;
import com.devorchestrator.entity.ContainerStatus;
import com.devorchestrator.entity.Environment;
import com.devorchestrator.entity.EnvironmentTemplate;
import com.devorchestrator.exception.DockerOperationException;
import com.devorchestrator.repository.ContainerInstanceRepository;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Transactional
@Slf4j
public class ContainerOrchestrationService {

    private final DockerClient dockerClient;
    private final ContainerInstanceRepository containerRepository;
    private final PortAllocationService portService;
    private final WebSocketNotificationService notificationService;

    public ContainerOrchestrationService(DockerClient dockerClient,
                                       ContainerInstanceRepository containerRepository,
                                       PortAllocationService portService,
                                       WebSocketNotificationService notificationService) {
        this.dockerClient = dockerClient;
        this.containerRepository = containerRepository;
        this.portService = portService;
        this.notificationService = notificationService;
    }

    @Async("orchestratorTaskExecutor")
    public CompletableFuture<Void> createEnvironment(Environment environment, EnvironmentTemplate template) {
        try {
            List<ContainerInstance> containers = new ArrayList<>();
            
            // Parse template configuration and create containers
            Map<String, Object> services = parseDockerComposeServices(template.getDockerComposeContent());
            
            for (Map.Entry<String, Object> service : services.entrySet()) {
                String serviceName = service.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> serviceConfig = (Map<String, Object>) service.getValue();
                
                ContainerInstance container = createContainer(environment, serviceName, serviceConfig);
                containers.add(container);
            }
            
            // Start all containers in dependency order
            for (ContainerInstance container : containers) {
                startContainer(container);
            }
            
            log.info("Successfully created environment {} with {} containers", 
                environment.getId(), containers.size());
            
            // Notify WebSocket clients of completion
            notificationService.notifyEnvironmentStatusChange(environment);
            return CompletableFuture.completedFuture(null);
                
        } catch (Exception e) {
            log.error("Failed to create environment {}: {}", environment.getId(), e.getMessage());
            notificationService.notifyEnvironmentError(environment.getId(), "Failed to create environment: " + e.getMessage());
            cleanupFailedEnvironment(environment);
            return CompletableFuture.failedFuture(new DockerOperationException("Failed to create environment: " + e.getMessage(), e));
        }
    }

    @Async("orchestratorTaskExecutor")
    public CompletableFuture<Void> startEnvironment(Environment environment) {
        try {
            List<ContainerInstance> containers = containerRepository.findByEnvironmentId(environment.getId());
            
            for (ContainerInstance container : containers) {
                if (container.getStatus() == ContainerStatus.STOPPED) {
                    startContainer(container);
                }
            }
            
            log.info("Successfully started environment {}", environment.getId());
            notificationService.notifyEnvironmentStatusChange(environment);
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Failed to start environment {}: {}", environment.getId(), e.getMessage());
            notificationService.notifyEnvironmentError(environment.getId(), "Failed to start environment: " + e.getMessage());
            return CompletableFuture.failedFuture(new DockerOperationException("Failed to start environment: " + e.getMessage(), e));
        }
    }

    @Async("orchestratorTaskExecutor")
    public CompletableFuture<Void> stopEnvironment(Environment environment) {
        try {
            List<ContainerInstance> containers = containerRepository.findByEnvironmentId(environment.getId());
            
            for (ContainerInstance container : containers) {
                if (container.getStatus() == ContainerStatus.RUNNING) {
                    stopContainer(container);
                }
            }
            
            log.info("Successfully stopped environment {}", environment.getId());
            notificationService.notifyEnvironmentStatusChange(environment);
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Failed to stop environment {}: {}", environment.getId(), e.getMessage());
            notificationService.notifyEnvironmentError(environment.getId(), "Failed to stop environment: " + e.getMessage());
            return CompletableFuture.failedFuture(new DockerOperationException("Failed to stop environment: " + e.getMessage(), e));
        }
    }

    @Async("orchestratorTaskExecutor")
    public CompletableFuture<Void> destroyEnvironment(Environment environment) {
        try {
            List<ContainerInstance> containers = containerRepository.findByEnvironmentId(environment.getId());
            
            for (ContainerInstance container : containers) {
                try {
                    // Stop container if running
                    if (container.getStatus() == ContainerStatus.RUNNING) {
                        dockerClient.stopContainerCmd(container.getDockerContainerId()).exec();
                    }
                    
                    // Remove container
                    dockerClient.removeContainerCmd(container.getDockerContainerId())
                        .withForce(true)
                        .exec();
                    
                    // Release allocated ports
                    if (container.getHostPort() != null) {
                        portService.releasePort(container.getHostPort());
                    }
                    
                    // Remove from database
                    containerRepository.deleteById(container.getId());
                    
                } catch (Exception e) {
                    log.warn("Failed to cleanup container {}: {}", container.getDockerContainerId(), e.getMessage());
                }
            }
            
            log.info("Successfully destroyed environment {}", environment.getId());
            notificationService.notifyEnvironmentStatusChange(environment);
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Failed to destroy environment {}: {}", environment.getId(), e.getMessage());
            notificationService.notifyEnvironmentError(environment.getId(), "Failed to destroy environment: " + e.getMessage());
            return CompletableFuture.failedFuture(new DockerOperationException("Failed to destroy environment: " + e.getMessage(), e));
        }
    }

    public List<ContainerInstance> getEnvironmentContainers(String environmentId) {
        return containerRepository.findByEnvironmentId(environmentId);
    }

    private ContainerInstance createContainer(Environment environment, String serviceName, 
                                            Map<String, Object> serviceConfig) {
        try {
            String imageName = (String) serviceConfig.get("image");
            @SuppressWarnings("unchecked")
            List<String> exposedPorts = (List<String>) serviceConfig.getOrDefault("ports", List.of());
            @SuppressWarnings("unchecked")
            Map<String, String> environmentVars = (Map<String, String>) serviceConfig.getOrDefault("environment", Map.of());
            
            // Allocate host port if container exposes ports
            Integer hostPort = null;
            if (!exposedPorts.isEmpty()) {
                hostPort = portService.allocatePort();
            }
            
            // Create port bindings
            List<PortBinding> portBindings = new ArrayList<>();
            List<ExposedPort> exposedPortList = new ArrayList<>();
            
            if (hostPort != null && !exposedPorts.isEmpty()) {
                ExposedPort exposedPort = ExposedPort.tcp(Integer.parseInt(exposedPorts.get(0)));
                exposedPortList.add(exposedPort);
                portBindings.add(PortBinding.parse(hostPort + ":" + exposedPorts.get(0)));
            }
            
            // Build environment variables with container-specific values
            List<String> envList = new ArrayList<>();
            environmentVars.forEach((key, value) -> envList.add(key + "=" + value));
            
            // Add environment-specific variables
            envList.add("ENVIRONMENT_ID=" + environment.getId());
            envList.add("SERVICE_NAME=" + serviceName);
            
            // Create container
            CreateContainerResponse containerResponse = dockerClient.createContainerCmd(imageName)
                .withName(generateContainerName(environment.getId(), serviceName))
                .withExposedPorts(exposedPortList)
                .withHostConfig(HostConfig.newHostConfig()
                    .withPortBindings(portBindings)
                    .withAutoRemove(false))
                .withEnv(envList)
                .exec();
            
            // Save container instance
            ContainerInstance container = ContainerInstance.builder()
                .id(UUID.randomUUID().toString())
                .environment(environment)
                .dockerContainerId(containerResponse.getId())
                .serviceName(serviceName)
                .containerName(generateContainerName(environment.getId(), serviceName))
                .status(ContainerStatus.STARTING)
                .hostPort(hostPort)
                .build();
            
            return containerRepository.save(container);
            
        } catch (Exception e) {
            log.error("Failed to create container for service {}: {}", serviceName, e.getMessage());
            throw new DockerOperationException("Failed to create container: " + e.getMessage(), e);
        }
    }

    private void startContainer(ContainerInstance container) {
        try {
            dockerClient.startContainerCmd(container.getDockerContainerId()).exec();
            
            // Update container status
            container.setStatus(ContainerStatus.RUNNING);
            containerRepository.save(container);
            
            log.debug("Started container {} for service {}", 
                container.getDockerContainerId(), container.getServiceName());
                
        } catch (Exception e) {
            container.setStatus(ContainerStatus.FAILED);
            containerRepository.save(container);
            throw new DockerOperationException("Failed to start container: " + e.getMessage(), e);
        }
    }

    private void stopContainer(ContainerInstance container) {
        try {
            dockerClient.stopContainerCmd(container.getDockerContainerId())
                .withTimeout(30)
                .exec();
            
            // Update container status
            container.setStatus(ContainerStatus.STOPPED);
            containerRepository.save(container);
            
            log.debug("Stopped container {} for service {}", 
                container.getDockerContainerId(), container.getServiceName());
                
        } catch (Exception e) {
            log.error("Failed to stop container {}: {}", container.getDockerContainerId(), e.getMessage());
            throw new DockerOperationException("Failed to stop container: " + e.getMessage(), e);
        }
    }

    private void cleanupFailedEnvironment(Environment environment) {
        try {
            List<ContainerInstance> containers = containerRepository.findByEnvironmentId(environment.getId());
            
            for (ContainerInstance container : containers) {
                try {
                    dockerClient.removeContainerCmd(container.getDockerContainerId())
                        .withForce(true)
                        .exec();
                    
                    if (container.getHostPort() != null) {
                        portService.releasePort(container.getHostPort());
                    }
                    
                    containerRepository.deleteById(container.getId());
                } catch (Exception e) {
                    log.warn("Failed to cleanup failed container {}: {}", 
                        container.getDockerContainerId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to cleanup failed environment {}: {}", environment.getId(), e.getMessage());
        }
    }

    private String generateContainerName(String environmentId, String serviceName) {
        return String.format("dev-env-%s-%s", environmentId.substring(0, 8), serviceName);
    }

    public boolean isContainerHealthy(String containerId) {
        try {
            InspectContainerResponse response = dockerClient.inspectContainerCmd(containerId).exec();
            return response.getState().getRunning() != null && response.getState().getRunning();
        } catch (Exception e) {
            log.warn("Failed to check container health for {}: {}", containerId, e.getMessage());
            return false;
        }
    }

    public boolean isDockerAvailable() {
        try {
            dockerClient.pingCmd().exec();
            return true;
        } catch (Exception e) {
            log.warn("Docker daemon not available: {}", e.getMessage());
            return false;
        }
    }

    public String getDockerVersion() {
        try {
            return dockerClient.versionCmd().exec().getVersion();
        } catch (Exception e) {
            log.warn("Failed to get Docker version: {}", e.getMessage());
            return "Unknown";
        }
    }

    public int getRunningContainerCount() {
        try {
            return dockerClient.listContainersCmd().withShowAll(false).exec().size();
        } catch (Exception e) {
            log.warn("Failed to get running container count: {}", e.getMessage());
            return 0;
        }
    }

    private Map<String, Object> parseDockerComposeServices(String dockerComposeContent) {
        try {
            if (dockerComposeContent == null || dockerComposeContent.trim().isEmpty()) {
                log.warn("Empty Docker Compose content provided, returning default service");
                return createDefaultService();
            }
            
            Yaml yaml = new Yaml();
            Map<String, Object> dockerCompose = yaml.load(dockerComposeContent);
            
            if (dockerCompose == null) {
                log.warn("Failed to parse Docker Compose YAML, returning default service");
                return createDefaultService();
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> services = (Map<String, Object>) dockerCompose.get("services");
            
            if (services == null || services.isEmpty()) {
                log.warn("No services found in Docker Compose, returning default service");
                return createDefaultService();
            }
            
            // Validate and sanitize services
            Map<String, Object> validatedServices = new HashMap<>();
            for (Map.Entry<String, Object> entry : services.entrySet()) {
                String serviceName = entry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> serviceConfig = (Map<String, Object>) entry.getValue();
                
                if (isValidServiceConfig(serviceName, serviceConfig)) {
                    validatedServices.put(serviceName, sanitizeServiceConfig(serviceConfig));
                } else {
                    log.warn("Invalid service configuration for '{}', skipping", serviceName);
                }
            }
            
            if (validatedServices.isEmpty()) {
                log.warn("No valid services found after validation, returning default service");
                return createDefaultService();
            }
            
            return validatedServices;
            
        } catch (Exception e) {
            log.error("Failed to parse Docker Compose YAML: {}", e.getMessage());
            log.debug("Docker Compose content that failed to parse: {}", dockerComposeContent);
            return createDefaultService();
        }
    }
    
    private Map<String, Object> createDefaultService() {
        Map<String, Object> services = new HashMap<>();
        Map<String, Object> defaultService = new HashMap<>();
        defaultService.put("image", "nginx:alpine");
        defaultService.put("ports", List.of("80"));
        defaultService.put("environment", new HashMap<>());
        services.put("web", defaultService);
        return services;
    }
    
    private boolean isValidServiceConfig(String serviceName, Map<String, Object> serviceConfig) {
        if (serviceConfig == null) {
            log.warn("Service '{}' has null configuration", serviceName);
            return false;
        }
        
        String image = (String) serviceConfig.get("image");
        if (image == null || image.trim().isEmpty()) {
            log.warn("Service '{}' missing required 'image' field", serviceName);
            return false;
        }
        
        // Validate image name format (basic validation)
        if (!image.matches("^[a-zA-Z0-9._/-]+:[a-zA-Z0-9._-]+$") && !image.matches("^[a-zA-Z0-9._/-]+$")) {
            log.warn("Service '{}' has invalid image format: {}", serviceName, image);
            return false;
        }
        
        return true;
    }
    
    private Map<String, Object> sanitizeServiceConfig(Map<String, Object> serviceConfig) {
        Map<String, Object> sanitized = new HashMap<>();
        
        // Copy image (required)
        sanitized.put("image", serviceConfig.get("image"));
        
        // Sanitize ports
        Object ports = serviceConfig.get("ports");
        if (ports instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> portList = (List<Object>) ports;
            List<String> sanitizedPorts = new ArrayList<>();
            for (Object port : portList) {
                if (port instanceof String) {
                    String portStr = (String) port;
                    // Extract container port from mapping like "8080:80" or just "80"
                    String containerPort = portStr.contains(":") ? portStr.split(":")[1] : portStr;
                    if (containerPort.matches("\\d+")) {
                        sanitizedPorts.add(containerPort);
                    }
                } else if (port instanceof Integer) {
                    sanitizedPorts.add(port.toString());
                }
            }
            sanitized.put("ports", sanitizedPorts);
        } else {
            sanitized.put("ports", Collections.emptyList());
        }
        
        // Sanitize environment variables
        Object environment = serviceConfig.get("environment");
        if (environment instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> envMap = (Map<String, Object>) environment;
            Map<String, String> sanitizedEnv = new HashMap<>();
            for (Map.Entry<String, Object> entry : envMap.entrySet()) {
                if (entry.getValue() != null) {
                    sanitizedEnv.put(entry.getKey(), entry.getValue().toString());
                }
            }
            sanitized.put("environment", sanitizedEnv);
        } else if (environment instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> envList = (List<String>) environment;
            Map<String, String> sanitizedEnv = new HashMap<>();
            for (String envVar : envList) {
                if (envVar.contains("=")) {
                    String[] parts = envVar.split("=", 2);
                    sanitizedEnv.put(parts[0], parts[1]);
                }
            }
            sanitized.put("environment", sanitizedEnv);
        } else {
            sanitized.put("environment", new HashMap<>());
        }
        
        return sanitized;
    }
}