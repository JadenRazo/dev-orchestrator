package com.devorchestrator.infrastructure.provisioner;

import com.devorchestrator.entity.Environment;
import com.devorchestrator.entity.EnvironmentStatus;
import com.devorchestrator.entity.InfrastructureProvider;
import com.devorchestrator.exception.DevOrchestratorException;
import com.devorchestrator.infrastructure.provider.CloudProviderService;
import com.devorchestrator.infrastructure.terraform.TerraformService;
import com.devorchestrator.repository.EnvironmentRepository;
import com.devorchestrator.service.ContainerOrchestrationService;
import com.devorchestrator.service.WebSocketNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class InfrastructureProvisioningService {

    private final TerraformService terraformService;
    private final ContainerOrchestrationService containerService;
    private final Map<InfrastructureProvider, CloudProviderService> cloudProviders;
    private final EnvironmentRepository environmentRepository;
    private final WebSocketNotificationService notificationService;

    public InfrastructureProvisioningService(
            TerraformService terraformService,
            ContainerOrchestrationService containerService,
            List<CloudProviderService> cloudProvidersList,
            EnvironmentRepository environmentRepository,
            WebSocketNotificationService notificationService) {
        
        this.terraformService = terraformService;
        this.containerService = containerService;
        this.environmentRepository = environmentRepository;
        this.notificationService = notificationService;
        
        this.cloudProviders = cloudProvidersList.stream()
            .collect(java.util.stream.Collectors.toMap(
                CloudProviderService::getProvider,
                provider -> provider
            ));
    }

    @Transactional
    public CompletableFuture<Void> provisionEnvironment(Environment environment) {
        InfrastructureProvider provider = environment.getInfrastructureProvider();
        
        log.info("Provisioning environment {} with provider {}", environment.getId(), provider);
        
        if (provider == InfrastructureProvider.DOCKER) {
            return containerService.createEnvironment(environment);
        } else if (provider == InfrastructureProvider.HYBRID) {
            return provisionHybridEnvironment(environment);
        } else {
            return provisionCloudEnvironment(environment);
        }
    }

    @Transactional
    public CompletableFuture<Void> destroyEnvironment(Environment environment) {
        InfrastructureProvider provider = environment.getInfrastructureProvider();
        
        log.info("Destroying environment {} with provider {}", environment.getId(), provider);
        
        if (provider == InfrastructureProvider.DOCKER) {
            return containerService.destroyEnvironment(environment);
        } else if (provider == InfrastructureProvider.HYBRID) {
            return destroyHybridEnvironment(environment);
        } else {
            return destroyCloudEnvironment(environment);
        }
    }

    @Transactional
    public CompletableFuture<Void> startEnvironment(Environment environment) {
        InfrastructureProvider provider = environment.getInfrastructureProvider();
        
        if (provider == InfrastructureProvider.DOCKER) {
            return containerService.startEnvironment(environment);
        } else {
            CloudProviderService cloudProvider = getCloudProvider(provider);
            return cloudProvider.startResources(environment);
        }
    }

    @Transactional
    public CompletableFuture<Void> stopEnvironment(Environment environment) {
        InfrastructureProvider provider = environment.getInfrastructureProvider();
        
        if (provider == InfrastructureProvider.DOCKER) {
            return containerService.stopEnvironment(environment);
        } else {
            CloudProviderService cloudProvider = getCloudProvider(provider);
            return cloudProvider.stopResources(environment);
        }
    }

    public Map<String, Object> getEnvironmentDetails(Environment environment) {
        InfrastructureProvider provider = environment.getInfrastructureProvider();
        
        if (provider == InfrastructureProvider.DOCKER) {
            return Map.of(
                "containers", containerService.getContainerDetails(environment),
                "ports", environment.getPortMappings()
            );
        } else {
            CloudProviderService cloudProvider = getCloudProvider(provider);
            return cloudProvider.getResourceDetails(environment);
        }
    }

    public boolean validateTemplate(String templateContent, InfrastructureProvider provider) {
        if (provider == InfrastructureProvider.DOCKER) {
            return containerService.validateDockerCompose(templateContent);
        } else {
            CloudProviderService cloudProvider = getCloudProvider(provider);
            return cloudProvider.validateTemplate(templateContent);
        }
    }

    private CompletableFuture<Void> provisionCloudEnvironment(Environment environment) {
        CloudProviderService cloudProvider = getCloudProvider(environment.getInfrastructureProvider());
        
        return CompletableFuture
            .runAsync(() -> cloudProvider.preProvision(environment))
            .thenCompose(v -> terraformService.provisionInfrastructure(environment))
            .thenRunAsync(() -> cloudProvider.postProvision(environment))
            .exceptionally(throwable -> {
                log.error("Failed to provision cloud environment", throwable);
                updateEnvironmentStatus(environment, EnvironmentStatus.ERROR);
                throw new DevOrchestratorException("Failed to provision cloud environment", throwable);
            });
    }

    private CompletableFuture<Void> destroyCloudEnvironment(Environment environment) {
        CloudProviderService cloudProvider = getCloudProvider(environment.getInfrastructureProvider());
        
        return CompletableFuture
            .runAsync(() -> cloudProvider.preDestroy(environment))
            .thenCompose(v -> terraformService.destroyInfrastructure(environment))
            .thenRunAsync(() -> cloudProvider.postDestroy(environment))
            .exceptionally(throwable -> {
                log.error("Failed to destroy cloud environment", throwable);
                updateEnvironmentStatus(environment, EnvironmentStatus.ERROR);
                throw new DevOrchestratorException("Failed to destroy cloud environment", throwable);
            });
    }

    private CompletableFuture<Void> provisionHybridEnvironment(Environment environment) {
        notificationService.sendEnvironmentUpdate(
            environment.getOwner().getId(), 
            environment.getId(), 
            "Provisioning hybrid environment (cloud + containers)"
        );
        
        return terraformService.provisionInfrastructure(environment)
            .thenCompose(v -> {
                Map<String, String> outputs = terraformService.getOutputs(environment);
                String dockerHost = outputs.get("docker_host");
                if (dockerHost != null) {
                    environment.addCloudResource("docker_host", dockerHost);
                }
                return containerService.createEnvironment(environment);
            })
            .exceptionally(throwable -> {
                log.error("Failed to provision hybrid environment", throwable);
                updateEnvironmentStatus(environment, EnvironmentStatus.ERROR);
                throw new DevOrchestratorException("Failed to provision hybrid environment", throwable);
            });
    }

    private CompletableFuture<Void> destroyHybridEnvironment(Environment environment) {
        notificationService.sendEnvironmentUpdate(
            environment.getOwner().getId(), 
            environment.getId(), 
            "Destroying hybrid environment"
        );
        
        return containerService.destroyEnvironment(environment)
            .thenCompose(v -> terraformService.destroyInfrastructure(environment))
            .exceptionally(throwable -> {
                log.error("Failed to destroy hybrid environment", throwable);
                updateEnvironmentStatus(environment, EnvironmentStatus.ERROR);
                throw new DevOrchestratorException("Failed to destroy hybrid environment", throwable);
            });
    }

    private CloudProviderService getCloudProvider(InfrastructureProvider provider) {
        CloudProviderService cloudProvider = cloudProviders.get(provider);
        if (cloudProvider == null) {
            throw new DevOrchestratorException("Cloud provider not supported: " + provider);
        }
        return cloudProvider;
    }

    private void updateEnvironmentStatus(Environment environment, EnvironmentStatus status) {
        environment.setStatus(status);
        environmentRepository.save(environment);
    }
}