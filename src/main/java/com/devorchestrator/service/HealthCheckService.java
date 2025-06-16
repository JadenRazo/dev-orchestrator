package com.devorchestrator.service;

import com.devorchestrator.entity.ContainerInstance;
import com.devorchestrator.entity.ContainerStatus;
import com.devorchestrator.repository.ContainerInstanceRepository;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class HealthCheckService {

    private final DockerClient dockerClient;
    private final ContainerInstanceRepository containerRepository;
    private final WebSocketNotificationService notificationService;
    private final HttpClient httpClient;

    public HealthCheckService(DockerClient dockerClient,
                            ContainerInstanceRepository containerRepository,
                            WebSocketNotificationService notificationService) {
        this.dockerClient = dockerClient;
        this.containerRepository = containerRepository;
        this.notificationService = notificationService;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    @Scheduled(fixedRateString = "${app.docker.health-check-interval:30}000")
    @Transactional
    public void performHealthChecks() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(2);
        List<ContainerInstance> containersToCheck = containerRepository
            .findRunningContainersNeedingHealthCheck(cutoff);

        log.debug("Performing health checks for {} containers", containersToCheck.size());

        containersToCheck.parallelStream()
            .forEach(this::checkContainerHealth);
    }

    public void checkContainerHealth(ContainerInstance container) {
        try {
            boolean isHealthy = performDockerHealthCheck(container);
            
            if (isHealthy && container.hasHealthCheck()) {
                isHealthy = performHttpHealthCheck(container).get();
            }

            updateContainerHealthStatus(container, isHealthy);
            
        } catch (Exception e) {
            log.warn("Health check failed for container {}: {}", 
                container.getDockerContainerId(), e.getMessage());
            updateContainerHealthStatus(container, false);
        }
    }

    private boolean performDockerHealthCheck(ContainerInstance container) {
        try {
            InspectContainerResponse response = dockerClient
                .inspectContainerCmd(container.getDockerContainerId())
                .exec();

            InspectContainerResponse.ContainerState state = response.getState();
            
            if (state.getRunning() == null || !state.getRunning()) {
                log.debug("Container {} is not running", container.getDockerContainerId());
                return false;
            }

            // Check if container has built-in health check
            if (state.getHealth() != null) {
                String healthStatus = state.getHealth().getStatus();
                boolean isHealthy = "healthy".equalsIgnoreCase(healthStatus);
                
                log.debug("Container {} Docker health status: {}", 
                    container.getDockerContainerId(), healthStatus);
                return isHealthy;
            }

            // If no built-in health check, consider running container as healthy
            return true;

        } catch (Exception e) {
            log.warn("Docker health check failed for container {}: {}", 
                container.getDockerContainerId(), e.getMessage());
            return false;
        }
    }

    private CompletableFuture<Boolean> performHttpHealthCheck(ContainerInstance container) {
        if (container.getHealthCheckUrl() == null || container.getHealthCheckUrl().trim().isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String healthUrl = buildHealthCheckUrl(container);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());

                boolean isHealthy = response.statusCode() >= 200 && response.statusCode() < 300;
                
                log.debug("HTTP health check for container {} ({}): status={}, healthy={}", 
                    container.getDockerContainerId(), healthUrl, response.statusCode(), isHealthy);
                
                return isHealthy;

            } catch (Exception e) {
                log.debug("HTTP health check failed for container {}: {}", 
                    container.getDockerContainerId(), e.getMessage());
                return false;
            }
        });
    }

    private String buildHealthCheckUrl(ContainerInstance container) {
        String baseUrl = container.getHealthCheckUrl();
        
        // If URL doesn't contain host, use localhost with mapped port
        if (!baseUrl.startsWith("http")) {
            if (container.getHostPort() != null) {
                baseUrl = "http://localhost:" + container.getHostPort() + baseUrl;
            } else {
                throw new IllegalStateException("No host port available for health check");
            }
        }
        
        return baseUrl;
    }

    private void updateContainerHealthStatus(ContainerInstance container, boolean isHealthy) {
        ContainerStatus newStatus = isHealthy ? ContainerStatus.RUNNING : ContainerStatus.ERROR;
        
        if (container.getStatus() != newStatus) {
            container.setStatus(newStatus);
            container.updateHealthCheck();
            containerRepository.save(container);
            
            // Notify WebSocket clients about status change
            notificationService.notifyContainerStatusChange(
                container.getEnvironment().getId(),
                container.getServiceName(),
                newStatus.name()
            );
            
            log.info("Container {} health status changed to: {}", 
                container.getServiceName(), newStatus);
        } else {
            // Just update the last health check time
            container.updateHealthCheck();
            containerRepository.save(container);
        }
    }

    public boolean isContainerHealthy(String containerId) {
        return containerRepository.findByDockerContainerId(containerId)
            .map(container -> {
                checkContainerHealth(container);
                return container.getStatus() == ContainerStatus.RUNNING;
            })
            .orElse(false);
    }

    public void scheduleHealthCheck(ContainerInstance container) {
        CompletableFuture.runAsync(() -> checkContainerHealth(container));
    }

    @Scheduled(fixedRateString = "${app.docker.cleanup-interval:300}000")
    @Transactional
    public void cleanupUnhealthyContainers() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        List<ContainerInstance> unhealthyContainers = containerRepository
            .findByLastHealthCheckBeforeAndStatus(cutoff, ContainerStatus.ERROR);

        log.debug("Found {} unhealthy containers for potential cleanup", unhealthyContainers.size());

        for (ContainerInstance container : unhealthyContainers) {
            try {
                // Try to restart unhealthy containers
                log.info("Attempting to restart unhealthy container: {}", container.getServiceName());
                
                dockerClient.restartContainerCmd(container.getDockerContainerId())
                    .withTimeout(30)
                    .exec();
                
                container.setStatus(ContainerStatus.STARTING);
                containerRepository.save(container);
                
                // Schedule immediate health check after restart
                scheduleHealthCheck(container);
                
            } catch (Exception e) {
                log.error("Failed to restart unhealthy container {}: {}", 
                    container.getServiceName(), e.getMessage());
                
                notificationService.notifyEnvironmentError(
                    container.getEnvironment().getId(),
                    "Failed to restart unhealthy container: " + container.getServiceName()
                );
            }
        }
    }
}