package com.devorchestrator.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.validation.Validator;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
@EnableJpaAuditing
@EnableCaching
@EnableAsync
@EnableScheduling
public class AppConfig {

    private final AppProperties appProperties;

    public AppConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public Validator validator() {
        return new LocalValidatorFactoryBean();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        AppProperties.Security.Cors corsProps = appProperties.getSecurity().getCors();
        
        List<String> allowedOrigins = Arrays.asList(corsProps.getAllowedOrigins().split(","));
        configuration.setAllowedOriginPatterns(allowedOrigins);
        
        List<String> allowedMethods = Arrays.asList(corsProps.getAllowedMethods().split(","));
        configuration.setAllowedMethods(allowedMethods);
        
        List<String> allowedHeaders = Arrays.asList(corsProps.getAllowedHeaders().split(","));
        configuration.setAllowedHeaders(allowedHeaders);
        
        configuration.setAllowCredentials(corsProps.isAllowCredentials());
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }

    @Bean(name = "orchestratorTaskExecutor")
    public Executor orchestratorTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("orchestrator-");
        executor.setRejectedExecutionHandler((r, exec) -> {
            // Custom rejection handler - log and throw exception
            throw new RuntimeException("Orchestrator task queue is full. Cannot accept more tasks.");
        });
        executor.initialize();
        return executor;
    }
}