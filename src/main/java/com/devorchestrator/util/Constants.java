package com.devorchestrator.util;

public final class Constants {
    
    private Constants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // Environment Constants
    public static final int DEFAULT_ENVIRONMENT_TIMEOUT_SECONDS = 300;
    public static final int MAX_ENVIRONMENT_NAME_LENGTH = 100;
    public static final int MIN_ENVIRONMENT_NAME_LENGTH = 3;
    public static final String ENVIRONMENT_NAME_PATTERN = "^[a-zA-Z0-9][a-zA-Z0-9-_\\s]*[a-zA-Z0-9]$";
    
    // Docker Constants
    public static final String DOCKER_LABEL_ENVIRONMENT_ID = "dev-orchestrator.environment.id";
    public static final String DOCKER_LABEL_SERVICE_NAME = "dev-orchestrator.service.name";
    public static final String DOCKER_LABEL_USER_ID = "dev-orchestrator.user.id";
    public static final String DOCKER_NETWORK_PREFIX = "dev-env-";
    public static final int DOCKER_DEFAULT_STOP_TIMEOUT = 30;
    
    // Port Constants
    public static final int DEFAULT_PORT_RANGE_START = 8000;
    public static final int DEFAULT_PORT_RANGE_END = 9000;
    public static final int PORT_ALLOCATION_MAX_ATTEMPTS = 100;
    
    // Resource Constants
    public static final int DEFAULT_CPU_LIMIT_MB = 1000;
    public static final long DEFAULT_MEMORY_LIMIT_MB = 1024;
    public static final int DEFAULT_DISK_LIMIT_MB = 5120;
    public static final int MAX_CPU_PERCENT = 80;
    public static final int MAX_MEMORY_PERCENT = 80;
    public static final int MAX_DISK_PERCENT = 85;
    
    // Security Constants
    public static final String JWT_TOKEN_PREFIX = "Bearer ";
    public static final String JWT_HEADER_NAME = "Authorization";
    public static final int JWT_DEFAULT_EXPIRATION_HOURS = 24;
    public static final int JWT_REFRESH_EXPIRATION_DAYS = 7;
    
    // Cache Constants
    public static final String CACHE_ENVIRONMENTS = "environments";
    public static final String CACHE_TEMPLATES = "templates";
    public static final String CACHE_USERS = "users";
    public static final String CACHE_SYSTEM_RESOURCES = "system-resources";
    public static final int CACHE_TTL_MINUTES = 10;
    
    // Validation Constants
    public static final String USERNAME_PATTERN = "^[a-zA-Z0-9_]{3,50}$";
    public static final String EMAIL_PATTERN = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 100;
    
    // Template Constants
    public static final String TEMPLATE_NODEJS_REACT = "nodejs-react-dev";
    public static final String TEMPLATE_SPRING_BOOT = "spring-boot-api";
    public static final String TEMPLATE_PYTHON_FLASK = "python-flask-dev";
    public static final String TEMPLATE_JAVA_MICROSERVICE = "java-microservice";
    
    // WebSocket Constants
    public static final String WEBSOCKET_ENDPOINT_ENVIRONMENT_STATUS = "/ws/environment/{id}/status";
    public static final String WEBSOCKET_ENDPOINT_ENVIRONMENT_LOGS = "/ws/environment/{id}/logs";
    public static final String WEBSOCKET_DESTINATION_PREFIX = "/topic";
    public static final String WEBSOCKET_APPLICATION_PREFIX = "/app";
    
    // Error Messages
    public static final String ERROR_ENVIRONMENT_NOT_FOUND = "Environment not found";
    public static final String ERROR_TEMPLATE_NOT_FOUND = "Template not found";
    public static final String ERROR_USER_NOT_FOUND = "User not found";
    public static final String ERROR_INSUFFICIENT_RESOURCES = "Insufficient system resources";
    public static final String ERROR_ENVIRONMENT_LIMIT_EXCEEDED = "Environment limit exceeded";
    public static final String ERROR_INVALID_ENVIRONMENT_STATE = "Invalid environment state";
    public static final String ERROR_DOCKER_OPERATION_FAILED = "Docker operation failed";
    public static final String ERROR_PORT_ALLOCATION_FAILED = "Port allocation failed";
    
    // Success Messages
    public static final String SUCCESS_ENVIRONMENT_CREATED = "Environment created successfully";
    public static final String SUCCESS_ENVIRONMENT_STARTED = "Environment started successfully";
    public static final String SUCCESS_ENVIRONMENT_STOPPED = "Environment stopped successfully";
    public static final String SUCCESS_ENVIRONMENT_DELETED = "Environment deleted successfully";
    
    // API Paths
    public static final String API_BASE_PATH = "/api/v1";
    public static final String API_ENVIRONMENTS_PATH = API_BASE_PATH + "/environments";
    public static final String API_TEMPLATES_PATH = API_BASE_PATH + "/templates";
    public static final String API_SYSTEM_PATH = API_BASE_PATH + "/system";
    public static final String API_AUTH_PATH = API_BASE_PATH + "/auth";
    
    // Actuator Endpoints
    public static final String ACTUATOR_HEALTH = "/actuator/health";
    public static final String ACTUATOR_METRICS = "/actuator/metrics";
    public static final String ACTUATOR_INFO = "/actuator/info";
    public static final String ACTUATOR_PROMETHEUS = "/actuator/prometheus";
}