package com.devorchestrator.util;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

@Slf4j
public final class ValidationUtils {
    
    private static final Pattern USERNAME_PATTERN = Pattern.compile(Constants.USERNAME_PATTERN);
    private static final Pattern EMAIL_PATTERN = Pattern.compile(Constants.EMAIL_PATTERN);
    private static final Pattern TEMPLATE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-_]*[a-zA-Z0-9]$");
    private static final Pattern CONTAINER_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-_]*[a-zA-Z0-9]$");
    
    private ValidationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean isValidUsername(String username) {
        return username != null && USERNAME_PATTERN.matcher(username).matches();
    }

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean isValidPassword(String password) {
        if (password == null) {
            return false;
        }
        
        int length = password.length();
        return length >= Constants.MIN_PASSWORD_LENGTH && 
               length <= Constants.MAX_PASSWORD_LENGTH &&
               hasPasswordComplexity(password);
    }

    public static boolean isValidTemplateId(String templateId) {
        return templateId != null && 
               templateId.length() >= 3 && 
               templateId.length() <= 50 &&
               TEMPLATE_ID_PATTERN.matcher(templateId).matches();
    }

    public static boolean isValidContainerName(String containerName) {
        return containerName != null && 
               containerName.length() >= 3 && 
               containerName.length() <= 100 &&
               CONTAINER_NAME_PATTERN.matcher(containerName).matches();
    }

    public static boolean isValidPort(Integer port) {
        return port != null && port >= 1024 && port <= 65535;
    }

    public static boolean isValidPortRange(int startPort, int endPort) {
        return startPort >= 1024 && 
               endPort <= 65535 && 
               startPort < endPort &&
               (endPort - startPort) >= 10; // Minimum range of 10 ports
    }

    public static boolean isValidCpuLimit(Integer cpuLimit) {
        return cpuLimit != null && cpuLimit > 0 && cpuLimit <= 8000; // Max 8 CPU cores worth
    }

    public static boolean isValidMemoryLimit(Long memoryLimit) {
        return memoryLimit != null && 
               memoryLimit > 0 && 
               memoryLimit <= 32768; // Max 32GB
    }

    public static boolean isValidDiskLimit(Integer diskLimit) {
        return diskLimit != null && 
               diskLimit > 0 && 
               diskLimit <= 102400; // Max 100GB
    }

    public static boolean isValidImageName(String imageName) {
        if (imageName == null || imageName.trim().isEmpty()) {
            return false;
        }
        
        // Basic Docker image name validation
        // Format: [registry/]namespace/repository[:tag]
        String[] parts = imageName.split("/");
        if (parts.length > 3) {
            return false;
        }
        
        // Check for valid characters
        return imageName.matches("^[a-z0-9._-]+(/[a-z0-9._-]+)*(:[\\.a-zA-Z0-9_-]+)?$");
    }

    public static boolean isValidEnvironmentVariable(String key, String value) {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }
        
        // Environment variable names should follow convention
        if (!key.matches("^[A-Z][A-Z0-9_]*$")) {
            return false;
        }
        
        // Value can be null or empty, but if present should not contain control characters
        return value == null || value.chars().noneMatch(Character::isISOControl);
    }

    public static boolean isValidJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return false;
        }
        
        try {
            // Simple validation - check balanced braces and quotes
            int braceCount = 0;
            boolean inQuotes = false;
            boolean escaped = false;
            
            for (char c : jsonString.toCharArray()) {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                
                if (c == '"' && !escaped) {
                    inQuotes = !inQuotes;
                    continue;
                }
                
                if (!inQuotes) {
                    if (c == '{' || c == '[') {
                        braceCount++;
                    } else if (c == '}' || c == ']') {
                        braceCount--;
                        if (braceCount < 0) {
                            return false;
                        }
                    }
                }
            }
            
            return braceCount == 0 && !inQuotes;
        } catch (Exception e) {
            log.debug("JSON validation failed: {}", e.getMessage());
            return false;
        }
    }

    public static boolean isValidDockerComposeName(String name) {
        return name != null && 
               name.length() >= 1 && 
               name.length() <= 63 &&
               name.matches("^[a-z0-9][a-z0-9\\-]*[a-z0-9]$");
    }

    public static String getPasswordStrengthMessage(String password) {
        if (password == null) {
            return "Password cannot be null";
        }
        
        if (password.length() < Constants.MIN_PASSWORD_LENGTH) {
            return "Password must be at least " + Constants.MIN_PASSWORD_LENGTH + " characters long";
        }
        
        if (password.length() > Constants.MAX_PASSWORD_LENGTH) {
            return "Password must not exceed " + Constants.MAX_PASSWORD_LENGTH + " characters";
        }
        
        if (!hasUpperCase(password)) {
            return "Password must contain at least one uppercase letter";
        }
        
        if (!hasLowerCase(password)) {
            return "Password must contain at least one lowercase letter";
        }
        
        if (!hasDigit(password)) {
            return "Password must contain at least one digit";
        }
        
        if (!hasSpecialCharacter(password)) {
            return "Password must contain at least one special character";
        }
        
        return "Password meets requirements";
    }

    private static boolean hasPasswordComplexity(String password) {
        return hasUpperCase(password) && 
               hasLowerCase(password) && 
               hasDigit(password) && 
               hasSpecialCharacter(password);
    }

    private static boolean hasUpperCase(String password) {
        return password.chars().anyMatch(Character::isUpperCase);
    }

    private static boolean hasLowerCase(String password) {
        return password.chars().anyMatch(Character::isLowerCase);
    }

    private static boolean hasDigit(String password) {
        return password.chars().anyMatch(Character::isDigit);
    }

    private static boolean hasSpecialCharacter(String password) {
        return password.chars().anyMatch(c -> "!@#$%^&*()_+-=[]{}|;:,.<>?".indexOf(c) >= 0);
    }
}