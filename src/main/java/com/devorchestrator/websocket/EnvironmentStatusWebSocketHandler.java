package com.devorchestrator.websocket;

import com.devorchestrator.entity.Environment;
import com.devorchestrator.service.EnvironmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class EnvironmentStatusWebSocketHandler implements WebSocketHandler {

    private final EnvironmentService environmentService;
    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToEnvironment = new ConcurrentHashMap<>();

    public EnvironmentStatusWebSocketHandler(EnvironmentService environmentService, ObjectMapper objectMapper) {
        this.environmentService = environmentService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String environmentId = extractEnvironmentId(session);
        Long userId = extractUserId(session);
        
        if (environmentId == null || userId == null) {
            log.warn("Invalid connection attempt - missing environment ID or user ID");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        try {
            // Verify user has access to this environment
            Environment environment = environmentService.getEnvironment(environmentId, userId);
            
            sessions.put(session.getId(), session);
            sessionToEnvironment.put(session.getId(), environmentId);
            
            log.info("WebSocket connection established for environment {} by user {}", environmentId, userId);
            
            // Send initial status
            sendEnvironmentStatus(session, environment);
            
        } catch (Exception e) {
            log.error("Failed to establish WebSocket connection: {}", e.getMessage());
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
                    case "ping" -> handlePing(session);
                    case "subscribe" -> handleSubscribe(session, payload);
                    case "unsubscribe" -> handleUnsubscribe(session, payload);
                    default -> log.warn("Unknown WebSocket action: {}", action);
                }
            } catch (Exception e) {
                log.error("Error handling WebSocket message: {}", e.getMessage());
                sendError(session, "Invalid message format");
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
        cleanupSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        log.info("WebSocket connection closed for session {}: {}", session.getId(), closeStatus);
        cleanupSession(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    public void broadcastEnvironmentStatus(String environmentId, Environment environment) {
        sessions.entrySet().stream()
            .filter(entry -> environmentId.equals(sessionToEnvironment.get(entry.getKey())))
            .forEach(entry -> {
                try {
                    sendEnvironmentStatus(entry.getValue(), environment);
                } catch (Exception e) {
                    log.error("Failed to send status update to session {}: {}", entry.getKey(), e.getMessage());
                }
            });
    }

    public void broadcastResourceUpdate(String environmentId, Map<String, Object> resourceData) {
        sessions.entrySet().stream()
            .filter(entry -> environmentId.equals(sessionToEnvironment.get(entry.getKey())))
            .forEach(entry -> {
                try {
                    sendResourceUpdate(entry.getValue(), resourceData);
                } catch (Exception e) {
                    log.error("Failed to send resource update to session {}: {}", entry.getKey(), e.getMessage());
                }
            });
    }

    private void handlePing(WebSocketSession session) throws IOException {
        Map<String, Object> pong = Map.of(
            "type", "pong",
            "timestamp", LocalDateTime.now().toString()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
    }

    private void handleSubscribe(WebSocketSession session, Map<String, Object> payload) throws IOException {
        String eventType = (String) payload.get("eventType");
        
        Map<String, Object> response = Map.of(
            "type", "subscription",
            "eventType", eventType,
            "status", "subscribed",
            "timestamp", LocalDateTime.now().toString()
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        log.debug("Session {} subscribed to {}", session.getId(), eventType);
    }

    private void handleUnsubscribe(WebSocketSession session, Map<String, Object> payload) throws IOException {
        String eventType = (String) payload.get("eventType");
        
        Map<String, Object> response = Map.of(
            "type", "subscription",
            "eventType", eventType,
            "status", "unsubscribed",
            "timestamp", LocalDateTime.now().toString()
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        log.debug("Session {} unsubscribed from {}", session.getId(), eventType);
    }

    private void sendEnvironmentStatus(WebSocketSession session, Environment environment) throws IOException {
        Map<String, Object> statusUpdate = Map.of(
            "type", "environment-status",
            "environmentId", environment.getId(),
            "status", environment.getStatus().toString(),
            "name", environment.getName(),
            "timestamp", LocalDateTime.now().toString(),
            "updatedAt", environment.getUpdatedAt().toString()
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(statusUpdate)));
    }

    private void sendResourceUpdate(WebSocketSession session, Map<String, Object> resourceData) throws IOException {
        Map<String, Object> update = Map.of(
            "type", "resource-update",
            "data", resourceData,
            "timestamp", LocalDateTime.now().toString()
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(update)));
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
            log.error("Failed to send error message to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private String extractEnvironmentId(WebSocketSession session) {
        String path = session.getUri().getPath();
        // Extract from path like /ws/environment/{id}/status
        String[] parts = path.split("/");
        return parts.length >= 4 ? parts[3] : null;
    }

    private Long extractUserId(WebSocketSession session) {
        // Extract user ID from session attributes or JWT token
        // This is a simplified implementation
        Object userIdAttr = session.getAttributes().get("userId");
        return userIdAttr instanceof Long ? (Long) userIdAttr : 1L; // Placeholder
    }

    private void cleanupSession(WebSocketSession session) {
        sessions.remove(session.getId());
        sessionToEnvironment.remove(session.getId());
    }
}