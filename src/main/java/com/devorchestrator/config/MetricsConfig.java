package com.devorchestrator.config;

import com.devorchestrator.repository.EnvironmentRepository;
import com.devorchestrator.repository.ContainerInstanceRepository;
import com.devorchestrator.entity.EnvironmentStatus;
import com.devorchestrator.entity.ContainerStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.aop.TimedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    private final EnvironmentRepository environmentRepository;
    private final ContainerInstanceRepository containerInstanceRepository;

    public MetricsConfig(EnvironmentRepository environmentRepository,
                        ContainerInstanceRepository containerInstanceRepository) {
        this.environmentRepository = environmentRepository;
        this.containerInstanceRepository = containerInstanceRepository;
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public Counter environmentCreationCounter(MeterRegistry registry) {
        return Counter.builder("environments.created.total")
                .description("Total number of environments created")
                .register(registry);
    }

    @Bean
    public Counter environmentDestroyedCounter(MeterRegistry registry) {
        return Counter.builder("environments.destroyed.total")
                .description("Total number of environments destroyed")
                .register(registry);
    }

    @Bean
    public Counter containerStartCounter(MeterRegistry registry) {
        return Counter.builder("containers.started.total")
                .description("Total number of containers started")
                .register(registry);
    }

    @Bean
    public Counter containerStopCounter(MeterRegistry registry) {
        return Counter.builder("containers.stopped.total")
                .description("Total number of containers stopped")
                .register(registry);
    }

    @Bean
    public Counter dockerOperationFailureCounter(MeterRegistry registry) {
        return Counter.builder("docker.operations.failed.total")
                .description("Total number of failed Docker operations")
                .register(registry);
    }

    @Bean
    public Timer environmentCreationTimer(MeterRegistry registry) {
        return Timer.builder("environments.creation.duration")
                .description("Time taken to create environments")
                .register(registry);
    }

    @Bean
    public Timer containerStartTimer(MeterRegistry registry) {
        return Timer.builder("containers.start.duration")
                .description("Time taken to start containers")
                .register(registry);
    }

    // TODO: Fix Gauge metrics - commenting out for now to enable compilation

    private double getActiveEnvironmentsCount() {
        return environmentRepository.countActiveEnvironments();
    }

    private double getRunningEnvironmentsCount() {
        return environmentRepository.countByStatus(EnvironmentStatus.RUNNING);
    }

    private double getStoppedEnvironmentsCount() {
        return environmentRepository.countByStatus(EnvironmentStatus.STOPPED);
    }

    private double getActiveContainersCount() {
        return containerInstanceRepository.countActiveContainers();
    }

    private double getRunningContainersCount() {
        return containerInstanceRepository.countByStatus(ContainerStatus.RUNNING);
    }
}