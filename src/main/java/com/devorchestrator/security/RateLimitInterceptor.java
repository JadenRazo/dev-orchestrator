package com.devorchestrator.security;

import com.devorchestrator.config.RateLimitConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitConfig.RateLimitService rateLimitService;

    public RateLimitInterceptor(RateLimitConfig.RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) 
            throws Exception {
        
        String clientId = getClientIdentifier(request);
        
        if (!rateLimitService.tryConsume(clientId)) {
            handleRateLimitExceeded(request, response, clientId);
            return false;
        }
        
        // Add rate limit headers
        long availableTokens = rateLimitService.getAvailableTokens(clientId);
        response.setHeader("X-RateLimit-Remaining", String.valueOf(availableTokens));
        response.setHeader("X-RateLimit-Limit", "60");
        response.setHeader("X-RateLimit-Window", "60");
        
        return true;
    }

    private String getClientIdentifier(HttpServletRequest request) {
        // Priority order for client identification:
        // 1. Authenticated user ID (if available)
        // 2. API key (if available)  
        // 3. IP address (fallback)
        
        String userId = extractUserIdFromRequest(request);
        if (userId != null && !userId.isEmpty()) {
            return "user:" + userId;
        }
        
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isEmpty()) {
            return "api:" + apiKey;
        }
        
        String clientIp = getClientIpAddress(request);
        return "ip:" + clientIp;
    }

    private String extractUserIdFromRequest(HttpServletRequest request) {
        // Extract user ID from JWT token if available
        try {
            return SecurityUtils.getCurrentUserId().orElse(null);
        } catch (Exception e) {
            // No authentication or invalid token
            return null;
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    private void handleRateLimitExceeded(HttpServletRequest request, HttpServletResponse response, String clientId) 
            throws IOException {
        
        log.warn("Rate limit exceeded for client: {} on endpoint: {} {}", 
            clientId, request.getMethod(), request.getRequestURI());
        
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setHeader("Retry-After", "60");
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setHeader("X-RateLimit-Limit", "60");
        response.setHeader("X-RateLimit-Window", "60");
        
        String jsonResponse = """
            {
                "error": "RATE_LIMIT_EXCEEDED",
                "message": "Too many requests. Please try again later.",
                "code": 429,
                "retryAfter": 60
            }
            """;
        
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
}