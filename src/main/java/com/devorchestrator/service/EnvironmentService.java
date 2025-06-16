package com.devorchestrator.service;

import com.devorchestrator.entity.Environment;
import com.devorchestrator.entity.EnvironmentStatus;
import com.devorchestrator.entity.EnvironmentTemplate;
import com.devorchestrator.entity.User;
import com.devorchestrator.exception.EnvironmentLimitExceededException;
import com.devorchestrator.exception.EnvironmentNotFoundException;
import com.devorchestrator.exception.InsufficientResourcesException;
import com.devorchestrator.exception.InvalidEnvironmentStateException;
import com.devorchestrator.exception.TemplateNotFoundException;
import com.devorchestrator.repository.EnvironmentRepository;
import com.devorchestrator.repository.EnvironmentTemplateRepository;
import com.devorchestrator.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
public class EnvironmentService {

    private final EnvironmentRepository environmentRepository;
    private final EnvironmentTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final ContainerOrchestrationService containerService;
    private final ResourceMonitoringService resourceService;
    private final WebSocketNotificationService notificationService;

    @Value("${app.environment.max-environments-per-user}")
    private int maxEnvironmentsPerUser;

    public EnvironmentService(EnvironmentRepository environmentRepository,
                            EnvironmentTemplateRepository templateRepository,
                            UserRepository userRepository,
                            ContainerOrchestrationService containerService,
                            ResourceMonitoringService resourceService,
                            WebSocketNotificationService notificationService) {
        this.environmentRepository = environmentRepository;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.containerService = containerService;
        this.resourceService = resourceService;
        this.notificationService = notificationService;
    }

    public Environment createEnvironment(String templateId, Long userId, String name) {
        User user = validateUser(userId);
        EnvironmentTemplate template = validateTemplate(templateId);
        validateUserEnvironmentLimit(userId);
        validateResourceAvailability(template);

        Environment environment = Environment.builder()
            .id(UUID.randomUUID().toString())
            .name(name)
            .template(template)
            .owner(user)
            .status(EnvironmentStatus.CREATING)
            .build();

        Environment saved = environmentRepository.save(environment);
        
        // Start async container creation with proper error handling
        containerService.createEnvironment(saved, template)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to create environment {}: {}", saved.getId(), throwable.getMessage());
                    updateEnvironmentStatus(saved.getId(), EnvironmentStatus.FAILED);
                } else {
                    log.info("Successfully created environment {} for user {}", saved.getId(), userId);
                    updateEnvironmentStatus(saved.getId(), EnvironmentStatus.RUNNING);
                }
            });
        
        log.info("Started async creation of environment {} for user {}", saved.getId(), userId);

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Environment> getUserEnvironments(Long userId, Pageable pageable) {
        return environmentRepository.findByOwnerId(userId, pageable);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "environments", key = "#environmentId + ':' + #userId")
    public Environment getEnvironment(String environmentId, Long userId) {
        return environmentRepository.findByIdAndOwnerId(environmentId, userId)
            .orElseThrow(() -> new EnvironmentNotFoundException(environmentId));
    }

    public Environment startEnvironment(String environmentId, Long userId) {
        Environment environment = getEnvironment(environmentId, userId);
        
        if (environment.getStatus() == EnvironmentStatus.RUNNING) {
            return environment;
        }
        
        if (environment.getStatus() != EnvironmentStatus.STOPPED) {
            throw new InvalidEnvironmentStateException(
                "Environment must be stopped to start it. Current status: " + environment.getStatus()
            );
        }

        updateEnvironmentStatus(environmentId, EnvironmentStatus.STARTING);
        
        // Start async operation - status updates will come via WebSocket
        containerService.startEnvironment(environment)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to start environment {}: {}", environmentId, throwable.getMessage());
                    updateEnvironmentStatus(environmentId, EnvironmentStatus.FAILED);
                } else {
                    log.info("Successfully started environment {} for user {}", environmentId, userId);
                    updateEnvironmentStatus(environmentId, EnvironmentStatus.RUNNING);
                }
            });

        return environmentRepository.findById(environmentId).orElseThrow();
    }

    public Environment stopEnvironment(String environmentId, Long userId) {
        Environment environment = getEnvironment(environmentId, userId);
        
        if (environment.getStatus() == EnvironmentStatus.STOPPED) {
            return environment;
        }
        
        if (environment.getStatus() != EnvironmentStatus.RUNNING) {
            throw new InvalidEnvironmentStateException(
                "Environment must be running to stop it. Current status: " + environment.getStatus()
            );
        }

        updateEnvironmentStatus(environmentId, EnvironmentStatus.STOPPING);
        
        // Start async operation - status updates will come via WebSocket
        containerService.stopEnvironment(environment)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to stop environment {}: {}", environmentId, throwable.getMessage());
                    updateEnvironmentStatus(environmentId, EnvironmentStatus.FAILED);
                } else {
                    log.info("Successfully stopped environment {} for user {}", environmentId, userId);
                    updateEnvironmentStatus(environmentId, EnvironmentStatus.STOPPED);
                }
            });

        return environmentRepository.findById(environmentId).orElseThrow();
    }

    public void deleteEnvironment(String environmentId, Long userId) {
        Environment environment = getEnvironment(environmentId, userId);
        
        updateEnvironmentStatus(environmentId, EnvironmentStatus.DELETING);
        
        // Stop first if running, then destroy
        if (environment.getStatus() == EnvironmentStatus.RUNNING) {
            containerService.stopEnvironment(environment)
                .thenCompose(result -> containerService.destroyEnvironment(environment))
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to delete environment {}: {}", environmentId, throwable.getMessage());
                        updateEnvironmentStatus(environmentId, EnvironmentStatus.FAILED);
                    } else {
                        environmentRepository.deleteById(environmentId);
                        log.info("Successfully deleted environment {} for user {}", environmentId, userId);
                    }
                });
        } else {
            containerService.destroyEnvironment(environment)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to delete environment {}: {}", environmentId, throwable.getMessage());
                        updateEnvironmentStatus(environmentId, EnvironmentStatus.FAILED);
                    } else {
                        environmentRepository.deleteById(environmentId);
                        log.info("Successfully deleted environment {} for user {}", environmentId, userId);
                    }
                });
        }
    }

    @Transactional(readOnly = true)
    public List<Environment> findStaleEnvironments(int hours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);
        return environmentRepository.findByStatusAndLastAccessedBefore(EnvironmentStatus.RUNNING, cutoff);
    }

    public void cleanupStaleEnvironments(int hours) {
        List<Environment> staleEnvironments = findStaleEnvironments(hours);
        
        for (Environment environment : staleEnvironments) {
            try {
                log.info("Cleaning up stale environment: {}", environment.getId());
                stopEnvironment(environment.getId(), environment.getOwner().getId());
            } catch (Exception e) {
                log.error("Failed to cleanup stale environment {}: {}", environment.getId(), e.getMessage());
            }
        }
    }

    private User validateUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }

    private EnvironmentTemplate validateTemplate(String templateId) {
        return templateRepository.findById(templateId)
            .orElseThrow(() -> new TemplateNotFoundException(templateId));
    }

    private void validateUserEnvironmentLimit(Long userId) {
        long currentCount = environmentRepository.countActiveEnvironmentsByOwnerId(userId);
        
        if (currentCount >= maxEnvironmentsPerUser) {
            throw new EnvironmentLimitExceededException(
                String.format("User %d has reached the maximum limit of %d environments", 
                    userId, maxEnvironmentsPerUser)
            );
        }
    }

    private void validateResourceAvailability(EnvironmentTemplate template) {
        if (!resourceService.hasAvailableResources(template.getCpuLimit().intValue(), template.getMemoryLimitMb().longValue())) {
            throw new InsufficientResourcesException(
                "Insufficient system resources to create environment with template: " + template.getId()
            );
        }
    }

    @CacheEvict(value = "environments", allEntries = true)
    private void updateEnvironmentStatus(String environmentId, EnvironmentStatus status) {
        environmentRepository.updateEnvironmentStatus(environmentId, status);
        
        // Notify WebSocket clients of status change
        Environment updatedEnvironment = environmentRepository.findById(environmentId).orElse(null);
        if (updatedEnvironment != null) {
            notificationService.notifyEnvironmentStatusChange(updatedEnvironment);
        }
    }
}