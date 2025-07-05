package com.devorchestrator.infrastructure.provider;

import com.devorchestrator.entity.Environment;
import com.devorchestrator.entity.InfrastructureProvider;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface CloudProviderService {
    
    InfrastructureProvider getProvider();
    
    void preProvision(Environment environment);
    
    void postProvision(Environment environment);
    
    void preDestroy(Environment environment);
    
    void postDestroy(Environment environment);
    
    CompletableFuture<Void> startResources(Environment environment);
    
    CompletableFuture<Void> stopResources(Environment environment);
    
    Map<String, Object> getResourceDetails(Environment environment);
    
    boolean validateTemplate(String templateContent);
    
    Map<String, String> getDefaultVariables();
    
    String generateTerraformTemplate(Map<String, Object> specifications);
}