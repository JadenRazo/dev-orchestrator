package com.devorchestrator.controller;

import com.devorchestrator.dto.*;
import com.devorchestrator.entity.Environment;
import com.devorchestrator.infrastructure.provisioner.InfrastructureProvisioningService;
import com.devorchestrator.infrastructure.terraform.TerraformService;
import com.devorchestrator.mapper.EnvironmentMapper;
import com.devorchestrator.security.SecurityUtils;
import com.devorchestrator.service.EnvironmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/environments")
@Tag(name = "Environment Management", description = "APIs for managing development environments")
@Slf4j
public class EnvironmentController {

    private final EnvironmentService environmentService;
    private final EnvironmentMapper environmentMapper;
    private final InfrastructureProvisioningService infrastructureService;
    private final TerraformService terraformService;

    public EnvironmentController(EnvironmentService environmentService, 
                               EnvironmentMapper environmentMapper,
                               InfrastructureProvisioningService infrastructureService,
                               TerraformService terraformService) {
        this.environmentService = environmentService;
        this.environmentMapper = environmentMapper;
        this.infrastructureService = infrastructureService;
        this.terraformService = terraformService;
    }

    @PostMapping
    @Operation(summary = "Create new environment", description = "Creates a new development environment from a template")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<EnvironmentDto> createEnvironment(
            @Valid @RequestBody CreateEnvironmentRequest request,
            Authentication authentication) {
        
        Long userId = getCurrentUserId(authentication);
        
        Environment environment = environmentService.createEnvironment(
            request.getTemplateId(),
            userId,
            request.getName()
        );
        
        log.info("Created environment {} for user {}", environment.getId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(environmentMapper.toDto(environment));
    }

    @GetMapping
    @Operation(summary = "List user environments", description = "Retrieves all environments for the current user")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Page<EnvironmentDto>> getUserEnvironments(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        
        Long userId = getCurrentUserId(authentication);
        Page<Environment> environments = environmentService.getUserEnvironments(userId, pageable);
        
        return ResponseEntity.ok(environments.map(environmentMapper::toDto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get environment details", description = "Retrieves detailed information about a specific environment")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<EnvironmentDto> getEnvironment(
            @Parameter(description = "Environment ID") @PathVariable String id,
            Authentication authentication) {
        
        Long userId = getCurrentUserId(authentication);
        Environment environment = environmentService.getEnvironment(id, userId);
        
        return ResponseEntity.ok(environmentMapper.toDto(environment));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start environment", description = "Starts a stopped environment")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<EnvironmentDto> startEnvironment(
            @Parameter(description = "Environment ID") @PathVariable String id,
            Authentication authentication) {
        
        Long userId = getCurrentUserId(authentication);
        Environment environment = environmentService.startEnvironment(id, userId);
        
        log.info("Started environment {} for user {}", id, userId);
        return ResponseEntity.ok(environmentMapper.toDto(environment));
    }

    @PostMapping("/{id}/stop")
    @Operation(summary = "Stop environment", description = "Stops a running environment")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<EnvironmentDto> stopEnvironment(
            @Parameter(description = "Environment ID") @PathVariable String id,
            Authentication authentication) {
        
        Long userId = getCurrentUserId(authentication);
        Environment environment = environmentService.stopEnvironment(id, userId);
        
        log.info("Stopped environment {} for user {}", id, userId);
        return ResponseEntity.ok(environmentMapper.toDto(environment));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete environment", description = "Permanently deletes an environment and all its resources")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Void> deleteEnvironment(
            @Parameter(description = "Environment ID") @PathVariable String id,
            Authentication authentication) {
        
        Long userId = getCurrentUserId(authentication);
        environmentService.deleteEnvironment(id, userId);
        
        log.info("Deleted environment {} for user {}", id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cleanup")
    @Operation(summary = "Cleanup stale environments", description = "Stops stale environments (admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> cleanupStaleEnvironments(
            @RequestParam(defaultValue = "24") int hours) {
        
        environmentService.cleanupStaleEnvironments(hours);
        
        return ResponseEntity.ok(Map.of(
            "message", "Cleanup process initiated",
            "staleHours", hours
        ));
    }

    @PostMapping("/infrastructure")
    @Operation(summary = "Create cloud infrastructure environment", description = "Creates a new environment with cloud infrastructure")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<EnvironmentDto> createInfrastructureEnvironment(
            @Valid @RequestBody InfrastructureProvisioningRequest request,
            Authentication authentication) {
        
        Long userId = getCurrentUserId(authentication);
        
        Environment environment = environmentService.createInfrastructureEnvironment(
            request, userId
        );
        
        infrastructureService.provisionEnvironment(environment);
        
        log.info("Created infrastructure environment {} for user {}", environment.getId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(environmentMapper.toDto(environment));
    }

    @GetMapping("/{id}/terraform/plan")
    @Operation(summary = "Get Terraform plan", description = "Retrieves the Terraform plan for a cloud environment")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> getTerraformPlan(
            @Parameter(description = "Environment ID") @PathVariable String id,
            Authentication authentication) {
        
        Long userId = getCurrentUserId(authentication);
        Environment environment = environmentService.getEnvironment(id, userId);
        
        String planOutput = terraformService.getPlanOutput(environment);
        
        return ResponseEntity.ok(Map.of(
            "environmentId", id,
            "plan", planOutput
        ));
    }

    @GetMapping("/{id}/terraform/outputs")
    @Operation(summary = "Get Terraform outputs", description = "Retrieves Terraform outputs for a cloud environment")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> getTerraformOutputs(
            @Parameter(description = "Environment ID") @PathVariable String id,
            Authentication authentication) {
        
        Long userId = getCurrentUserId(authentication);
        Environment environment = environmentService.getEnvironment(id, userId);
        
        Map<String, String> outputs = terraformService.getOutputs(environment);
        
        return ResponseEntity.ok(outputs);
    }

    @GetMapping("/{id}/resources")
    @Operation(summary = "Get cloud resources", description = "Retrieves cloud resources for an environment")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<CloudResourceDto>> getCloudResources(
            @Parameter(description = "Environment ID") @PathVariable String id,
            Authentication authentication) {
        
        Long userId = getCurrentUserId(authentication);
        Environment environment = environmentService.getEnvironment(id, userId);
        
        List<CloudResourceDto> resources = environment.getCloudResourceIds().entrySet().stream()
            .map(entry -> CloudResourceDto.builder()
                .resourceType(entry.getKey())
                .resourceId(entry.getValue())
                .provider(environment.getInfrastructureProvider().toString())
                .region(environment.getTemplate().getCloudRegion())
                .status("ACTIVE")
                .build())
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/{id}/details")
    @Operation(summary = "Get infrastructure details", description = "Retrieves detailed infrastructure information")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getInfrastructureDetails(
            @Parameter(description = "Environment ID") @PathVariable String id,
            Authentication authentication) {
        
        Long userId = getCurrentUserId(authentication);
        Environment environment = environmentService.getEnvironment(id, userId);
        
        Map<String, Object> details = infrastructureService.getEnvironmentDetails(environment);
        
        return ResponseEntity.ok(details);
    }

    private Long getCurrentUserId(Authentication authentication) {
        return SecurityUtils.getCurrentUserId()
                .map(userIdStr -> {
                    try {
                        return Long.parseLong(userIdStr);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid user ID format in JWT: {}", userIdStr);
                        throw new IllegalArgumentException("Invalid user ID in authentication token", e);
                    }
                })
                .orElseThrow(() -> new IllegalStateException("User ID not found in authentication token"));
    }

    public static class CreateEnvironmentRequest {
        private String templateId;
        private String name;

        public String getTemplateId() {
            return templateId;
        }

        public void setTemplateId(String templateId) {
            this.templateId = templateId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}