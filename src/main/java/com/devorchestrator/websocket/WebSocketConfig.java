package com.devorchestrator.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@Slf4j
public class WebSocketConfig implements WebSocketConfigurer {

    private final EnvironmentStatusWebSocketHandler environmentStatusHandler;
    private final ContainerLogsWebSocketHandler containerLogsHandler;
    private final WebSocketAuthInterceptor authInterceptor;

    @Value("${app.websocket.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String[] allowedOrigins;

    public WebSocketConfig(EnvironmentStatusWebSocketHandler environmentStatusHandler,
                          ContainerLogsWebSocketHandler containerLogsHandler,
                          WebSocketAuthInterceptor authInterceptor) {
        this.environmentStatusHandler = environmentStatusHandler;
        this.containerLogsHandler = containerLogsHandler;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        log.info("Registering WebSocket handlers with allowed origins: {}", String.join(",", allowedOrigins));
        
        // Environment status updates
        registry.addHandler(environmentStatusHandler, "/ws/environment/{id}/status")
            .setAllowedOrigins(allowedOrigins)
            .addInterceptors(authInterceptor)
            .withSockJS();

        // Container logs streaming
        registry.addHandler(containerLogsHandler, "/ws/environment/{id}/logs")
            .setAllowedOrigins(allowedOrigins)
            .addInterceptors(authInterceptor)
            .withSockJS();

        // Alternative endpoints without SockJS for modern browsers
        registry.addHandler(environmentStatusHandler, "/ws/environment/{id}/status")
            .setAllowedOrigins(allowedOrigins)
            .addInterceptors(authInterceptor);

        registry.addHandler(containerLogsHandler, "/ws/environment/{id}/logs")
            .setAllowedOrigins(allowedOrigins)
            .addInterceptors(authInterceptor);
    }
}