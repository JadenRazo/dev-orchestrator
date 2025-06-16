package com.devorchestrator.util;

import com.devorchestrator.entity.Environment;
import com.devorchestrator.entity.EnvironmentStatus;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public final class EnvironmentUtils {
    
    private static final Pattern ENVIRONMENT_NAME_PATTERN = Pattern.compile(Constants.ENVIRONMENT_NAME_PATTERN);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    
    private EnvironmentUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean isValidEnvironmentName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = name.trim();
        return trimmed.length() >= Constants.MIN_ENVIRONMENT_NAME_LENGTH &&
               trimmed.length() <= Constants.MAX_ENVIRONMENT_NAME_LENGTH &&
               ENVIRONMENT_NAME_PATTERN.matcher(trimmed).matches();
    }

    public static String sanitizeEnvironmentName(String name) {
        if (name == null) {
            return null;
        }
        
        // Remove leading/trailing whitespace
        String sanitized = name.trim();
        
        // Replace multiple spaces with single space
        sanitized = sanitized.replaceAll("\\s+", " ");
        
        // Remove any characters that aren't alphanumeric, hyphen, underscore, or space
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9\\-_\\s]", "");
        
        return sanitized;
    }

    public static String generateUniqueEnvironmentName(String baseName, String username) {
        String sanitizedBase = sanitizeEnvironmentName(baseName);
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        
        return String.format("%s-%s-%s", sanitizedBase, username, timestamp);
    }

    public static boolean isEnvironmentRunning(Environment environment) {
        return environment != null && environment.getStatus() == EnvironmentStatus.RUNNING;
    }

    public static boolean isEnvironmentStopped(Environment environment) {
        return environment != null && environment.getStatus() == EnvironmentStatus.STOPPED;
    }

    public static boolean isEnvironmentTransitioning(Environment environment) {
        if (environment == null) {
            return false;
        }
        
        return environment.getStatus() == EnvironmentStatus.CREATING ||
               environment.getStatus() == EnvironmentStatus.STARTING ||
               environment.getStatus() == EnvironmentStatus.STOPPING;
    }

    public static boolean canEnvironmentBeStarted(Environment environment) {
        return environment != null && 
               (environment.getStatus() == EnvironmentStatus.STOPPED ||
                environment.getStatus() == EnvironmentStatus.FAILED);
    }

    public static boolean canEnvironmentBeStopped(Environment environment) {
        return environment != null && environment.getStatus() == EnvironmentStatus.RUNNING;
    }

    public static boolean canEnvironmentBeDeleted(Environment environment) {
        return environment != null && 
               environment.getStatus() != EnvironmentStatus.CREATING &&
               environment.getStatus() != EnvironmentStatus.STARTING &&
               environment.getStatus() != EnvironmentStatus.STOPPING;
    }

    public static boolean isEnvironmentStale(Environment environment, int staleHours) {
        if (environment == null || environment.getLastAccessedAt() == null) {
            return false;
        }
        
        LocalDateTime cutoff = LocalDateTime.now().minusHours(staleHours);
        return environment.getLastAccessedAt().isBefore(cutoff) &&
               isEnvironmentRunning(environment);
    }

    public static List<Environment> filterStaleEnvironments(List<Environment> environments, int staleHours) {
        return environments.stream()
            .filter(env -> isEnvironmentStale(env, staleHours))
            .toList();
    }

    public static String getEnvironmentDisplayName(Environment environment) {
        if (environment == null) {
            return "Unknown Environment";
        }
        
        String displayName = environment.getName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = "Environment-" + environment.getId().substring(0, 8);
        }
        
        return displayName;
    }

    public static String formatEnvironmentUptime(Environment environment) {
        if (environment == null || environment.getCreatedAt() == null) {
            return "Unknown";
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime created = environment.getCreatedAt();
        
        long hours = java.time.Duration.between(created, now).toHours();
        long days = hours / 24;
        hours = hours % 24;
        
        if (days > 0) {
            return String.format("%d days, %d hours", days, hours);
        } else {
            return String.format("%d hours", hours);
        }
    }

    public static String getEnvironmentStatusDescription(EnvironmentStatus status) {
        return switch (status) {
            case CREATING -> "Environment is being created";
            case STARTING -> "Environment is starting up";
            case RUNNING -> "Environment is running and accessible";
            case STOPPING -> "Environment is shutting down";
            case STOPPED -> "Environment is stopped";
            case DELETING -> "Environment is being deleted";
            case DESTROYED -> "Environment has been destroyed";
            case FAILED -> "Environment creation or operation failed";
            case ERROR -> "Environment encountered an error";
            case null -> "Unknown status";
        };
    }

    public static double calculateResourceUtilization(int used, int total) {
        if (total <= 0) {
            return 0.0;
        }
        return (double) used / total * 100.0;
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        
        String[] units = {"KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;
        
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        
        return String.format("%.2f %s", size, units[unitIndex]);
    }

    public static String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        }
    }
}