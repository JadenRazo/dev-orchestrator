package com.devorchestrator.websocket;

import com.devorchestrator.entity.Environment;
import com.devorchestrator.service.ContainerOrchestrationService;
import com.devorchestrator.service.EnvironmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class ContainerLogsWebSocketHandler implements WebSocketHandler {

    private final EnvironmentService environmentService;
    private final ContainerOrchestrationService containerService;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService executorService;
    
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToEnvironment = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> logStreamTasks = new ConcurrentHashMap<>();

    public ContainerLogsWebSocketHandler(EnvironmentService environmentService,
                                       ContainerOrchestrationService containerService,
                                       ObjectMapper objectMapper) {
        this.environmentService = environmentService;
        this.containerService = containerService;
        this.objectMapper = objectMapper;
        this.executorService = Executors.newScheduledThreadPool(10);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String environmentId = extractEnvironmentId(session);
        Long userId = extractUserId(session);
        
        if (environmentId == null || userId == null) {
            log.warn("Invalid log streaming connection - missing environment ID or user ID");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        try {
            // Verify user has access to this environment
            Environment environment = environmentService.getEnvironment(environmentId, userId);
            
            sessions.put(session.getId(), session);
            sessionToEnvironment.put(session.getId(), environmentId);
            
            log.info("WebSocket log streaming connection established for environment {} by user {}", 
                environmentId, userId);
            
            // Send connection confirmation
            sendConnectionConfirmation(session, environmentId);
            
        } catch (Exception e) {
            log.error("Failed to establish log streaming connection: {}", e.getMessage());
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (message instanceof TextMessage textMessage) {
            try {
                Map<String, Object> payload = objectMapper.readValue(textMessage.getPayload(), Map.class);
                String action = (String) payload.get("action");
                
                switch (action) {
                    case "start-logs" -> handleStartLogs(session, payload);
                    case "stop-logs" -> handleStopLogs(session);
                    case "ping" -> handlePing(session);
                    default -> log.warn("Unknown log streaming action: {}", action);
                }
            } catch (Exception e) {
                log.error("Error handling log streaming message: {}", e.getMessage());
                sendError(session, "Invalid message format");
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for log streaming session {}: {}", 
            session.getId(), exception.getMessage());
        cleanupSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        log.info("WebSocket log streaming connection closed for session {}: {}", 
            session.getId(), closeStatus);
        cleanupSession(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private void handleStartLogs(WebSocketSession session, Map<String, Object> payload) {
        String environmentId = sessionToEnvironment.get(session.getId());
        if (environmentId == null) {
            sendError(session, "Environment not found");
            return;
        }

        String containerName = (String) payload.get("container");
        boolean follow = Boolean.TRUE.equals(payload.get("follow"));
        int tailLines = (Integer) payload.getOrDefault("tail", 100);

        try {
            // Start log streaming task
            ScheduledFuture<?> task = executorService.scheduleWithFixedDelay(
                () -> streamContainerLogs(session, environmentId, containerName, tailLines),
                0, 1, TimeUnit.SECONDS
            );
            
            logStreamTasks.put(session.getId(), task);
            
            Map<String, Object> response = Map.of(
                "type", "log-stream-started",
                "container", containerName != null ? containerName : "all",
                "follow", follow,
                "tail", tailLines,
                "timestamp", LocalDateTime.now().toString()
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            
        } catch (Exception e) {
            log.error("Failed to start log streaming: {}", e.getMessage());
            sendError(session, "Failed to start log streaming");
        }
    }

    private void handleStopLogs(WebSocketSession session) {
        ScheduledFuture<?> task = logStreamTasks.remove(session.getId());
        if (task != null) {
            task.cancel(true);
        }

        try {
            Map<String, Object> response = Map.of(
                "type", "log-stream-stopped",
                "timestamp", LocalDateTime.now().toString()
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (IOException e) {
            log.error("Failed to send log stream stop confirmation: {}", e.getMessage());
        }
    }

    private void handlePing(WebSocketSession session) throws IOException {
        Map<String, Object> pong = Map.of(
            "type", "pong",
            "timestamp", LocalDateTime.now().toString()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
    }

    private void streamContainerLogs(WebSocketSession session, String environmentId, 
                                   String containerName, int tailLines) {
        try {
            if (!session.isOpen()) {
                return;
            }

            // Get container logs (this is a simplified implementation)
            // In a real implementation, you would use Docker API to stream logs
            String logs = getContainerLogs(environmentId, containerName, tailLines);
            
            if (logs != null && !logs.isEmpty()) {
                Map<String, Object> logMessage = Map.of(
                    "type", "container-logs",
                    "environmentId", environmentId,
                    "container", containerName != null ? containerName : "all",
                    "logs", logs,
                    "timestamp", LocalDateTime.now().toString()
                );
                
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(logMessage)));
            }
            
        } catch (Exception e) {
            log.error("Error streaming container logs: {}", e.getMessage());
            // Stop streaming on error
            handleStopLogs(session);
        }
    }

    private String getContainerLogs(String environmentId, String containerName, int tailLines) {
        // TODO: DOCKER INTEGRATION IMPLEMENTATION REQUIRED
        // This method currently returns simulated log data for development.
        // 
        // PRODUCTION IMPLEMENTATION REQUIREMENTS:
        // 1. Integrate with Docker API to retrieve actual container logs
        // 2. Use ContainerOrchestrationService.getContainerLogs() method
        // 3. Handle log filtering by container name and tail line limits
        // 4. Stream logs efficiently without loading everything into memory
        // 5. Handle Docker API errors and network timeouts gracefully
        // 
        // CURRENT BEHAVIOR: Returns simulated log messages for testing
        
        try {
            var containers = containerService.getEnvironmentContainers(environmentId);
            
            if (containers.isEmpty()) {
                return null;
            }
            
            // Simulate getting logs
            StringBuilder logs = new StringBuilder();
            for (var container : containers) {
                if (containerName == null || containerName.equals(container.getServiceName())) {
                    logs.append(String.format("[%s] Container %s is running\n", 
                        LocalDateTime.now(), container.getServiceName()));
                }
            }
            
            return logs.toString();
            
        } catch (Exception e) {
            log.error("Failed to get container logs: {}", e.getMessage());
            return null;
        }
    }

    private void sendConnectionConfirmation(WebSocketSession session, String environmentId) throws IOException {
        Map<String, Object> confirmation = Map.of(
            "type", "connection-established",
            "environmentId", environmentId,
            "timestamp", LocalDateTime.now().toString()
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(confirmation)));
    }

    private void sendError(WebSocketSession session, String message) {
        try {
            Map<String, Object> error = Map.of(
                "type", "error",
                "message", message,
                "timestamp", LocalDateTime.now().toString()
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
        } catch (IOException e) {
            log.error("Failed to send error message to log streaming session {}: {}", 
                session.getId(), e.getMessage());
        }
    }

    private String extractEnvironmentId(WebSocketSession session) {
        String path = session.getUri().getPath();
        // Extract from path like /ws/environment/{id}/logs
        String[] parts = path.split("/");
        return parts.length >= 4 ? parts[3] : null;
    }

    private Long extractUserId(WebSocketSession session) {
        // TODO: AUTHENTICATION IMPLEMENTATION REQUIRED
        // This method currently returns a hardcoded fallback user ID.
        // 
        // PRODUCTION IMPLEMENTATION REQUIREMENTS:
        // 1. Extract user ID from session attributes set by WebSocketAuthInterceptor
        // 2. Validate the user ID is not null and represents an active user
        // 3. Handle cases where authentication info is missing or invalid
        // 
        // CURRENT BEHAVIOR: 
        // - Attempts to get userId from session attributes (set by WebSocketAuthInterceptor)
        // - Falls back to hardcoded user ID: 1L if not found
        // 
        // SECURITY RISK: Fallback allows unauthenticated access with hardcoded identity
        
        Object userIdAttr = session.getAttributes().get("userId");
        return userIdAttr instanceof Long ? (Long) userIdAttr : 1L; // TODO: Remove fallback, reject unauthenticated connections
    }

    private void cleanupSession(WebSocketSession session) {
        sessions.remove(session.getId());
        sessionToEnvironment.remove(session.getId());
        
        ScheduledFuture<?> task = logStreamTasks.remove(session.getId());
        if (task != null) {
            task.cancel(true);
        }
    }
}