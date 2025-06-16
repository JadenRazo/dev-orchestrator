package com.devorchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("com.devorchestrator.entity")
@EnableJpaRepositories("com.devorchestrator.repository") 
@ComponentScan(basePackages = {
    "com.devorchestrator.controller",
    "com.devorchestrator.service", 
    "com.devorchestrator.config",
    "com.devorchestrator.security",
    "com.devorchestrator.exception"
})
public class TestEnvironmentApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestEnvironmentApplication.class, args);
    }
}