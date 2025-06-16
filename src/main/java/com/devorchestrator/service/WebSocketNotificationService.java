package com.devorchestrator.service;

import com.devorchestrator.entity.Environment;
import com.devorchestrator.websocket.EnvironmentStatusWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class WebSocketNotificationService {

    private final EnvironmentStatusWebSocketHandler environmentStatusHandler;

    public WebSocketNotificationService(EnvironmentStatusWebSocketHandler environmentStatusHandler) {
        this.environmentStatusHandler = environmentStatusHandler;
    }

    public void notifyEnvironmentStatusChange(Environment environment) {
        try {
            environmentStatusHandler.broadcastEnvironmentStatus(environment.getId(), environment);
            log.debug("Broadcast environment status change for environment {}: {}", 
                environment.getId(), environment.getStatus());
        } catch (Exception e) {
            log.error("Failed to broadcast environment status change for {}: {}", 
                environment.getId(), e.getMessage());
        }
    }

    public void notifyResourceUpdate(String environmentId, Map<String, Object> resourceData) {
        try {
            environmentStatusHandler.broadcastResourceUpdate(environmentId, resourceData);
            log.debug("Broadcast resource update for environment {}", environmentId);
        } catch (Exception e) {
            log.error("Failed to broadcast resource update for {}: {}", 
                environmentId, e.getMessage());
        }
    }

    public void notifyContainerStatusChange(String environmentId, String containerName, String status) {
        Map<String, Object> containerUpdate = Map.of(
            "type", "container-status",
            "containerName", containerName,
            "status", status,
            "timestamp", java.time.LocalDateTime.now().toString()
        );
        
        try {
            environmentStatusHandler.broadcastResourceUpdate(environmentId, containerUpdate);
            log.debug("Broadcast container status change for container {} in environment {}: {}", 
                containerName, environmentId, status);
        } catch (Exception e) {
            log.error("Failed to broadcast container status change for {} in environment {}: {}", 
                containerName, environmentId, e.getMessage());
        }
    }

    public void notifyEnvironmentError(String environmentId, String errorMessage) {
        Map<String, Object> errorData = Map.of(
            "type", "environment-error",
            "message", errorMessage,
            "timestamp", java.time.LocalDateTime.now().toString()
        );
        
        try {
            environmentStatusHandler.broadcastResourceUpdate(environmentId, errorData);
            log.debug("Broadcast environment error for environment {}: {}", environmentId, errorMessage);
        } catch (Exception e) {
            log.error("Failed to broadcast environment error for {}: {}", environmentId, e.getMessage());
        }
    }
}