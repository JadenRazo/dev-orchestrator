package com.devorchestrator.config;

import com.devorchestrator.security.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
            .addPathPatterns("/v1/**")  // Apply to all API endpoints
            .excludePathPatterns(
                "/v1/health/**",        // Health checks
                "/v1/auth/login",       // Login endpoint
                "/v1/templates",        // Public template list
                "/actuator/**"          // Actuator endpoints
            );
    }
}