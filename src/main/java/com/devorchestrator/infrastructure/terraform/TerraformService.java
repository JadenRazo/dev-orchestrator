package com.devorchestrator.infrastructure.terraform;

import com.devorchestrator.entity.Environment;
import com.devorchestrator.entity.EnvironmentStatus;
import com.devorchestrator.entity.EnvironmentTemplate;
import com.devorchestrator.exception.DevOrchestratorException;
import com.devorchestrator.infrastructure.state.TerraformStateService;
import com.devorchestrator.repository.EnvironmentRepository;
import com.devorchestrator.service.WebSocketNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class TerraformService {

    private final TerraformExecutor terraformExecutor;
    private final TerraformStateService stateService;
    private final EnvironmentRepository environmentRepository;
    private final WebSocketNotificationService notificationService;
    private final Path workspaceBasePath;

    public TerraformService(TerraformExecutor terraformExecutor,
                          TerraformStateService stateService,
                          EnvironmentRepository environmentRepository,
                          WebSocketNotificationService notificationService) {
        this.terraformExecutor = terraformExecutor;
        this.stateService = stateService;
        this.environmentRepository = environmentRepository;
        this.notificationService = notificationService;
        this.workspaceBasePath = Paths.get(System.getProperty("java.io.tmpdir"), "terraform-workspaces");
        createWorkspaceDirectory();
    }

    @Async
    @Transactional
    public CompletableFuture<Void> provisionInfrastructure(Environment environment) {
        log.info("Starting infrastructure provisioning for environment: {}", environment.getId());
        
        try {
            String workspaceId = UUID.randomUUID().toString();
            Path workspacePath = createWorkspace(workspaceId, environment);
            
            updateEnvironmentStatus(environment, EnvironmentStatus.CREATING);
            notificationService.sendEnvironmentUpdate(environment.getOwner().getId(), environment.getId(), 
                "Initializing Terraform workspace");

            terraformExecutor.init(workspacePath);
            notificationService.sendEnvironmentUpdate(environment.getOwner().getId(), environment.getId(), 
                "Planning infrastructure changes");

            String planOutput = terraformExecutor.plan(workspacePath, environment.getTemplate().getTerraformVariables());
            notificationService.sendEnvironmentUpdate(environment.getOwner().getId(), environment.getId(), 
                "Applying infrastructure changes");

            Map<String, String> outputs = terraformExecutor.apply(workspacePath);
            
            String stateId = stateService.saveState(workspaceId, workspacePath);
            environment.setTerraformStateId(stateId);
            
            for (Map.Entry<String, String> output : outputs.entrySet()) {
                environment.addCloudResource(output.getKey(), output.getValue());
            }
            
            updateEnvironmentStatus(environment, EnvironmentStatus.RUNNING);
            notificationService.sendEnvironmentUpdate(environment.getOwner().getId(), environment.getId(), 
                "Infrastructure provisioning completed successfully");
            
            log.info("Successfully provisioned infrastructure for environment: {}", environment.getId());
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Failed to provision infrastructure for environment: {}", environment.getId(), e);
            updateEnvironmentStatus(environment, EnvironmentStatus.ERROR);
            notificationService.sendEnvironmentUpdate(environment.getOwner().getId(), environment.getId(), 
                "Infrastructure provisioning failed: " + e.getMessage());
            throw new DevOrchestratorException("Failed to provision infrastructure", e);
        }
    }

    @Async
    @Transactional
    public CompletableFuture<Void> destroyInfrastructure(Environment environment) {
        log.info("Starting infrastructure destruction for environment: {}", environment.getId());
        
        if (environment.getTerraformStateId() == null) {
            log.warn("No Terraform state found for environment: {}", environment.getId());
            return CompletableFuture.completedFuture(null);
        }
        
        try {
            updateEnvironmentStatus(environment, EnvironmentStatus.DELETING);
            notificationService.sendEnvironmentUpdate(environment.getOwner().getId(), environment.getId(), 
                "Destroying infrastructure resources");

            Path workspacePath = stateService.restoreWorkspace(environment.getTerraformStateId());
            
            terraformExecutor.destroy(workspacePath);
            
            stateService.deleteState(environment.getTerraformStateId());
            environment.setTerraformStateId(null);
            environment.getCloudResourceIds().clear();
            
            updateEnvironmentStatus(environment, EnvironmentStatus.DESTROYED);
            notificationService.sendEnvironmentUpdate(environment.getOwner().getId(), environment.getId(), 
                "Infrastructure destroyed successfully");
            
            cleanupWorkspace(workspacePath);
            
            log.info("Successfully destroyed infrastructure for environment: {}", environment.getId());
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Failed to destroy infrastructure for environment: {}", environment.getId(), e);
            updateEnvironmentStatus(environment, EnvironmentStatus.ERROR);
            notificationService.sendEnvironmentUpdate(environment.getOwner().getId(), environment.getId(), 
                "Infrastructure destruction failed: " + e.getMessage());
            throw new DevOrchestratorException("Failed to destroy infrastructure", e);
        }
    }

    public String getPlanOutput(Environment environment) {
        if (environment.getTerraformStateId() == null) {
            throw new DevOrchestratorException("No Terraform state found for environment");
        }
        
        try {
            Path workspacePath = stateService.restoreWorkspace(environment.getTerraformStateId());
            return terraformExecutor.plan(workspacePath, environment.getTemplate().getTerraformVariables());
        } catch (Exception e) {
            log.error("Failed to generate Terraform plan for environment: {}", environment.getId(), e);
            throw new DevOrchestratorException("Failed to generate Terraform plan", e);
        }
    }

    public Map<String, String> getOutputs(Environment environment) {
        if (environment.getTerraformStateId() == null) {
            throw new DevOrchestratorException("No Terraform state found for environment");
        }
        
        try {
            Path workspacePath = stateService.restoreWorkspace(environment.getTerraformStateId());
            return terraformExecutor.getOutputs(workspacePath);
        } catch (Exception e) {
            log.error("Failed to get Terraform outputs for environment: {}", environment.getId(), e);
            throw new DevOrchestratorException("Failed to get Terraform outputs", e);
        }
    }

    private Path createWorkspace(String workspaceId, Environment environment) throws IOException {
        Path workspacePath = workspaceBasePath.resolve(workspaceId);
        Files.createDirectories(workspacePath);
        
        EnvironmentTemplate template = environment.getTemplate();
        String terraformConfig = processTerraformTemplate(template.getTerraformTemplate(), environment);
        
        Files.writeString(workspacePath.resolve("main.tf"), terraformConfig);
        
        if (template.getTerraformVariables() != null) {
            Files.writeString(workspacePath.resolve("terraform.tfvars"), template.getTerraformVariables());
        }
        
        return workspacePath;
    }

    private String processTerraformTemplate(String template, Environment environment) {
        return template
            .replace("${environment_id}", environment.getId())
            .replace("${environment_name}", environment.getName())
            .replace("${owner_id}", environment.getOwner().getId().toString());
    }

    private void createWorkspaceDirectory() {
        try {
            Files.createDirectories(workspaceBasePath);
        } catch (IOException e) {
            throw new DevOrchestratorException("Failed to create Terraform workspace directory", e);
        }
    }

    private void cleanupWorkspace(Path workspacePath) {
        try {
            Files.walk(workspacePath)
                .sorted((a, b) -> -a.compareTo(b))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        log.warn("Failed to delete file: {}", path, e);
                    }
                });
        } catch (IOException e) {
            log.warn("Failed to cleanup workspace: {}", workspacePath, e);
        }
    }

    private void updateEnvironmentStatus(Environment environment, EnvironmentStatus status) {
        environment.setStatus(status);
        environmentRepository.save(environment);
    }
}