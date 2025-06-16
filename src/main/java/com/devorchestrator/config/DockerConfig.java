package com.devorchestrator.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class DockerConfig {

    private static final Logger log = LoggerFactory.getLogger(DockerConfig.class);

    private final AppProperties appProperties;

    public DockerConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public DockerClientConfig dockerClientConfig() {
        AppProperties.Docker dockerProps = appProperties.getDocker();
        
        DefaultDockerClientConfig.Builder configBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerProps.getHost())
                .withApiVersion(dockerProps.getApiVersion());

        if (dockerProps.isTlsVerify()) {
            configBuilder.withDockerTlsVerify(true);
            if (dockerProps.getCertPath() != null && !dockerProps.getCertPath().isEmpty()) {
                configBuilder.withDockerCertPath(dockerProps.getCertPath());
            }
        }

        DockerClientConfig config = configBuilder.build();
        
        log.info("Docker client configured with host: {}, API version: {}, TLS verify: {}", 
                dockerProps.getHost(), dockerProps.getApiVersion(), dockerProps.isTlsVerify());
        
        return config;
    }

    @Bean
    public DockerHttpClient dockerHttpClient(DockerClientConfig dockerClientConfig) {
        AppProperties.Docker dockerProps = appProperties.getDocker();
        
        return new ApacheDockerHttpClient.Builder()
                .dockerHost(dockerClientConfig.getDockerHost())
                .sslConfig(dockerClientConfig.getSSLConfig())
                .connectionTimeout(Duration.ofMillis(dockerProps.getConnectionTimeout()))
                .responseTimeout(Duration.ofMillis(dockerProps.getReadTimeout()))
                .build();
    }

    @Bean
    public DockerClient dockerClient(DockerClientConfig dockerClientConfig, 
                                   DockerHttpClient dockerHttpClient) {
        DockerClient dockerClient = DockerClientImpl.getInstance(dockerClientConfig, dockerHttpClient);
        
        try {
            // Test connection
            dockerClient.pingCmd().exec();
            log.info("Successfully connected to Docker daemon");
        } catch (Exception e) {
            log.error("Failed to connect to Docker daemon: {}", e.getMessage());
            throw new RuntimeException("Docker connection failed", e);
        }
        
        return dockerClient;
    }
}