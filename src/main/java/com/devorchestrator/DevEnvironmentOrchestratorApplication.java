package com.devorchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for Development Environment Orchestrator.
 * 
 * This Spring Boot application manages and monitors local development environments,
 * providing comprehensive Docker container orchestration, resource monitoring,
 * and real-time status updates through WebSocket connections.
 * 
 * Key Features:
 * - Environment template management
 * - Docker container lifecycle management
 * - Real-time resource monitoring
 * - Port conflict resolution
 * - Multi-user environment isolation
 * - WebSocket-based live updates
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
@EnableAsync
@EnableScheduling
public class DevEnvironmentOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevEnvironmentOrchestratorApplication.class, args);
    }
}