package com.devorchestrator.websocket;

import com.devorchestrator.entity.ProjectRegistration;
import com.devorchestrator.entity.ResourceMetric;
import com.devorchestrator.repository.ResourceMetricRepository;
import com.devorchestrator.service.ProjectRegistryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class MetricsWebSocketHandler extends TextWebSocketHandler {
    
    private final ProjectRegistryService projectRegistryService;
    private final ResourceMetricRepository metricRepository;
    private final ObjectMapper objectMapper;
    
    // Track sessions by project ID
    private final Map<String, List<WebSocketSession>> projectSessions = new ConcurrentHashMap<>();
    
    // Track session metadata
    private final Map<String, SessionMetadata> sessionMetadata = new ConcurrentHashMap<>();
    
    public MetricsWebSocketHandler(ProjectRegistryService projectRegistryService,
                                  ResourceMetricRepository metricRepository,
                                  ObjectMapper objectMapper) {
        this.projectRegistryService = projectRegistryService;
        this.metricRepository = metricRepository;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connection established: {}", session.getId());
        
        // Send welcome message
        WebSocketMessage welcome = WebSocketMessage.builder()
            .type("CONNECTION")
            .message("Connected to metrics stream")
            .timestamp(LocalDateTime.now())
            .build();
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcome)));
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received message: {}", payload);
        
        try {
            WebSocketCommand command = objectMapper.readValue(payload, WebSocketCommand.class);
            
            switch (command.getAction()) {
                case "SUBSCRIBE":
                    handleSubscribe(session, command);
                    break;
                    
                case "UNSUBSCRIBE":
                    handleUnsubscribe(session, command);
                    break;
                    
                case "PING":
                    handlePing(session);
                    break;
                    
                default:
                    sendError(session, "Unknown command: " + command.getAction());
            }
            
        } catch (Exception e) {
            log.error("Error processing WebSocket message", e);
            sendError(session, "Invalid message format");
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket connection closed: {} with status: {}", session.getId(), status);
        
        // Remove session from all project subscriptions
        SessionMetadata metadata = sessionMetadata.remove(session.getId());
        if (metadata != null && metadata.subscribedProjects != null) {
            for (String projectId : metadata.subscribedProjects) {
                List<WebSocketSession> sessions = projectSessions.get(projectId);
                if (sessions != null) {
                    sessions.remove(session);
                    if (sessions.isEmpty()) {
                        projectSessions.remove(projectId);
                    }
                }
            }
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session: {}", session.getId(), exception);
        session.close(CloseStatus.SERVER_ERROR);
    }
    
    /**
     * Handles subscription to project metrics
     */
    private void handleSubscribe(WebSocketSession session, WebSocketCommand command) throws IOException {
        String projectId = command.getProjectId();
        if (projectId == null) {
            sendError(session, "Project ID required for subscription");
            return;
        }
        
        // Verify project access (simplified - should check user authorization)
        try {
            // Add session to project subscribers
            projectSessions.computeIfAbsent(projectId, k -> new CopyOnWriteArrayList<>()).add(session);
            
            // Track subscription in session metadata
            sessionMetadata.computeIfAbsent(session.getId(), k -> new SessionMetadata())
                .subscribedProjects.add(projectId);
            
            // Send confirmation
            WebSocketMessage confirmation = WebSocketMessage.builder()
                .type("SUBSCRIBED")
                .projectId(projectId)
                .message("Subscribed to project metrics")
                .timestamp(LocalDateTime.now())
                .build();
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(confirmation)));
            
            // Send latest metrics immediately
            sendLatestMetrics(session, projectId);
            
        } catch (Exception e) {
            sendError(session, "Failed to subscribe: " + e.getMessage());
        }
    }
    
    /**
     * Handles unsubscription from project metrics
     */
    private void handleUnsubscribe(WebSocketSession session, WebSocketCommand command) throws IOException {
        String projectId = command.getProjectId();
        if (projectId == null) {
            sendError(session, "Project ID required for unsubscription");
            return;
        }
        
        // Remove session from project subscribers
        List<WebSocketSession> sessions = projectSessions.get(projectId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                projectSessions.remove(projectId);
            }
        }
        
        // Update session metadata
        SessionMetadata metadata = sessionMetadata.get(session.getId());
        if (metadata != null) {
            metadata.subscribedProjects.remove(projectId);
        }
        
        // Send confirmation
        WebSocketMessage confirmation = WebSocketMessage.builder()
            .type("UNSUBSCRIBED")
            .projectId(projectId)
            .message("Unsubscribed from project metrics")
            .timestamp(LocalDateTime.now())
            .build();
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(confirmation)));
    }
    
    /**
     * Handles ping messages for keep-alive
     */
    private void handlePing(WebSocketSession session) throws IOException {
        WebSocketMessage pong = WebSocketMessage.builder()
            .type("PONG")
            .timestamp(LocalDateTime.now())
            .build();
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
    }
    
    /**
     * Sends error message to client
     */
    private void sendError(WebSocketSession session, String error) throws IOException {
        WebSocketMessage errorMsg = WebSocketMessage.builder()
            .type("ERROR")
            .message(error)
            .timestamp(LocalDateTime.now())
            .build();
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMsg)));
    }
    
    /**
     * Sends latest metrics for a project
     */
    private void sendLatestMetrics(WebSocketSession session, String projectId) throws IOException {
        List<ResourceMetric> latestMetrics = metricRepository.getLatestMetricsForProject(projectId);
        
        MetricsUpdate update = MetricsUpdate.builder()
            .type("METRICS_UPDATE")
            .projectId(projectId)
            .metrics(convertMetrics(latestMetrics))
            .timestamp(LocalDateTime.now())
            .build();
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(update)));
    }
    
    /**
     * Broadcasts metrics to all subscribers of a project
     */
    public void broadcastMetrics(String projectId, List<ResourceMetric> metrics) {
        List<WebSocketSession> sessions = projectSessions.get(projectId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        
        try {
            MetricsUpdate update = MetricsUpdate.builder()
                .type("METRICS_UPDATE")
                .projectId(projectId)
                .metrics(convertMetrics(metrics))
                .timestamp(LocalDateTime.now())
                .build();
            
            String message = objectMapper.writeValueAsString(update);
            TextMessage textMessage = new TextMessage(message);
            
            // Send to all subscribed sessions
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        log.error("Failed to send metrics to session: {}", session.getId(), e);
                    }
                } else {
                    // Remove closed sessions
                    sessions.remove(session);
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to broadcast metrics", e);
        }
    }
    
    /**
     * Scheduled task to send metrics updates
     */
    @Scheduled(fixedDelay = 5000) // Every 5 seconds
    public void sendScheduledMetricsUpdates() {
        for (Map.Entry<String, List<WebSocketSession>> entry : projectSessions.entrySet()) {
            String projectId = entry.getKey();
            List<WebSocketSession> sessions = entry.getValue();
            
            if (!sessions.isEmpty()) {
                // Get recent metrics (last 10 seconds)
                LocalDateTime since = LocalDateTime.now().minusSeconds(10);
                List<ResourceMetric> recentMetrics = metricRepository
                    .findByProjectIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                        projectId, since, LocalDateTime.now()
                    ).getContent();
                
                if (!recentMetrics.isEmpty()) {
                    broadcastMetrics(projectId, recentMetrics);
                }
            }
        }
    }
    
    /**
     * Converts ResourceMetric entities to DTOs
     */
    private List<MetricData> convertMetrics(List<ResourceMetric> metrics) {
        return metrics.stream()
            .map(metric -> MetricData.builder()
                .metricType(metric.getMetricType().name())
                .metricName(metric.getMetricName())
                .value(metric.getValue())
                .unit(metric.getUnit())
                .containerId(metric.getContainerId())
                .recordedAt(metric.getRecordedAt())
                .build())
            .toList();
    }
    
    // Helper classes
    private static class SessionMetadata {
        final Set<String> subscribedProjects = new HashSet<>();
    }
}

// Command sent by client
@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
class WebSocketCommand {
    private String action; // SUBSCRIBE, UNSUBSCRIBE, PING
    private String projectId;
    private Map<String, Object> params;
}

// Base message sent to client
@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Builder
class WebSocketMessage {
    private String type;
    private String projectId;
    private String message;
    private LocalDateTime timestamp;
}

// Metrics update message
@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Builder
class MetricsUpdate {
    private String type;
    private String projectId;
    private List<MetricData> metrics;
    private LocalDateTime timestamp;
}

// Individual metric data
@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Builder
class MetricData {
    private String metricType;
    private String metricName;
    private java.math.BigDecimal value;
    private String unit;
    private String containerId;
    private LocalDateTime recordedAt;
}