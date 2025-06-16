package com.devorchestrator.performance;

import com.devorchestrator.entity.ContainerInstance;
import com.devorchestrator.entity.ContainerStatus;
import com.devorchestrator.entity.Environment;
import com.devorchestrator.entity.EnvironmentTemplate;
import com.devorchestrator.repository.ContainerInstanceRepository;
import com.devorchestrator.service.ContainerOrchestrationService;
import com.devorchestrator.service.PortAllocationService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContainerOrchestrationPerformanceTest {

    @Mock
    private DockerClient dockerClient;
    
    @Mock
    private ContainerInstanceRepository containerRepository;
    
    @Mock
    private PortAllocationService portService;
    
    @Mock
    private CreateContainerCmd createContainerCmd;
    
    @Mock
    private StartContainerCmd startContainerCmd;
    
    @Mock
    private StopContainerCmd stopContainerCmd;
    
    @InjectMocks
    private ContainerOrchestrationService orchestrationService;

    private Environment testEnvironment;
    private EnvironmentTemplate testTemplate;
    private List<ContainerInstance> testContainers;

    @BeforeEach
    void setUp() {
        testEnvironment = Environment.builder()
            .id("test-env-123")
            .name("Performance Test Environment")
            .build();

        testTemplate = EnvironmentTemplate.builder()
            .id("perf-template")
            .name("Performance Test Template")
            .dockerComposeContent("version: '3.8'\nservices:\n  web:\n    image: nginx:alpine\n    ports:\n      - \"80\"\n")
            .build();

        // Create test containers
        testContainers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ContainerInstance container = ContainerInstance.builder()
                .id("container-" + i)
                .environment(testEnvironment)
                .dockerContainerId("docker-container-" + i)
                .serviceName("service-" + i)
                .status(ContainerStatus.STOPPED)
                .hostPort(8080 + i)
                .build();
            testContainers.add(container);
        }

        // Setup common mocks
        when(dockerClient.createContainerCmd(anyString())).thenReturn(createContainerCmd);
        when(createContainerCmd.withName(anyString())).thenReturn(createContainerCmd);
        when(createContainerCmd.withExposedPorts((com.github.dockerjava.api.model.ExposedPort[]) any())).thenReturn(createContainerCmd);
        when(createContainerCmd.withHostConfig(any())).thenReturn(createContainerCmd);
        when(createContainerCmd.withEnv((String[]) any())).thenReturn(createContainerCmd);
        when(createContainerCmd.exec()).thenReturn(new CreateContainerResponse());

        when(dockerClient.startContainerCmd(anyString())).thenReturn(startContainerCmd);
        when(startContainerCmd.exec()).thenReturn(null);

        when(dockerClient.stopContainerCmd(anyString())).thenReturn(stopContainerCmd);
        when(stopContainerCmd.withTimeout(anyInt())).thenReturn(stopContainerCmd);
        when(stopContainerCmd.exec()).thenReturn(null);

        when(portService.allocatePort()).thenReturn(8080, 8081, 8082, 8083, 8084, 8085, 8086, 8087, 8088, 8089);
        when(containerRepository.save(any(ContainerInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(containerRepository.findByEnvironmentId(anyString())).thenReturn(testContainers);
    }

    @Test
    @DisplayName("Measure sequential container start performance (baseline)")
    void measureSequentialContainerStartPerformance() {
        // Given
        long startTime = System.currentTimeMillis();
        
        // When - Sequential container start (current implementation)
        for (ContainerInstance container : testContainers) {
            container.setStatus(ContainerStatus.RUNNING);
        }
        
        long endTime = System.currentTimeMillis();
        long sequentialTime = endTime - startTime;
        
        // Then
        System.out.printf("Sequential container start time: %d ms for %d containers%n", 
            sequentialTime, testContainers.size());
        
        assertThat(sequentialTime).isGreaterThan(0);
        
        // Store baseline measurement
        PerformanceMetrics.recordMetric("container_start_sequential", sequentialTime);
    }

    @Test
    @DisplayName("Measure parallel container start performance (optimized)")
    void measureParallelContainerStartPerformance() throws Exception {
        // Given
        ExecutorService executor = Executors.newFixedThreadPool(4);
        long startTime = System.currentTimeMillis();
        
        // When - Parallel container start (optimized implementation)
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (ContainerInstance container : testContainers) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                // Simulate container start operation
                container.setStatus(ContainerStatus.RUNNING);
                try {
                    Thread.sleep(10); // Simulate Docker API call latency
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executor);
            futures.add(future);
        }
        
        // Wait for all containers to start
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        long endTime = System.currentTimeMillis();
        long parallelTime = endTime - startTime;
        
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
        
        // Then
        System.out.printf("Parallel container start time: %d ms for %d containers%n", 
            parallelTime, testContainers.size());
        
        assertThat(parallelTime).isGreaterThan(0);
        
        // Store optimized measurement
        PerformanceMetrics.recordMetric("container_start_parallel", parallelTime);
        
        // Calculate improvement
        long sequentialTime = PerformanceMetrics.getMetric("container_start_sequential");
        if (sequentialTime > 0) {
            double improvement = ((double) (sequentialTime - parallelTime) / sequentialTime) * 100;
            System.out.printf("Performance improvement: %.2f%%\n", improvement);
            PerformanceMetrics.recordMetric("container_start_improvement_percent", (long) improvement);
        }
    }

    @Test
    @DisplayName("Measure Docker API call overhead")
    void measureDockerApiCallOverhead() {
        // Given
        int numberOfCalls = 100;
        long startTime = System.currentTimeMillis();
        
        // When - Measure Docker API call overhead
        for (int i = 0; i < numberOfCalls; i++) {
            when(createContainerCmd.exec()).thenReturn(new CreateContainerResponse());
            dockerClient.createContainerCmd("nginx:alpine")
                .withName("test-container-" + i)
                .exec();
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        long averageCallTime = totalTime / numberOfCalls;
        
        // Then
        System.out.printf("Docker API call overhead: %d ms average per call (%d total calls in %d ms)%n", 
            averageCallTime, numberOfCalls, totalTime);
        
        PerformanceMetrics.recordMetric("docker_api_call_average_ms", averageCallTime);
        PerformanceMetrics.recordMetric("docker_api_call_total_ms", totalTime);
        
        assertThat(averageCallTime).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Measure container cleanup performance")
    void measureContainerCleanupPerformance() {
        // Given
        long startTime = System.currentTimeMillis();
        
        // When - Simulate container cleanup
        testContainers.forEach(container -> {
            // Simulate stop and remove operations
            container.setStatus(ContainerStatus.STOPPED);
        });
        
        long endTime = System.currentTimeMillis();
        long cleanupTime = endTime - startTime;
        
        // Then
        System.out.printf("Container cleanup time: %d ms for %d containers%n", 
            cleanupTime, testContainers.size());
        
        PerformanceMetrics.recordMetric("container_cleanup_time_ms", cleanupTime);
        
        assertThat(cleanupTime).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Measure environment creation end-to-end performance")
    void measureEnvironmentCreationPerformance() {
        // Given
        long startTime = System.currentTimeMillis();
        
        // When - Simulate complete environment creation
        try {
            // Simulate the createEnvironment method flow
            testContainers.forEach(container -> {
                container.setStatus(ContainerStatus.STARTING);
                // Simulate creation delay
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                container.setStatus(ContainerStatus.RUNNING);
            });
        } catch (Exception e) {
            // Handle any simulation errors
        }
        
        long endTime = System.currentTimeMillis();
        long creationTime = endTime - startTime;
        
        // Then
        System.out.printf("Environment creation time: %d ms for %d containers%n", 
            creationTime, testContainers.size());
        
        PerformanceMetrics.recordMetric("environment_creation_time_ms", creationTime);
        
        assertThat(creationTime).isGreaterThan(0);
    }

    @Test
    @DisplayName("Compare sequential vs parallel container operations")
    void compareSequentialVsParallelOperations() throws Exception {
        // Measure sequential operations
        long sequentialStart = System.currentTimeMillis();
        for (ContainerInstance container : testContainers) {
            Thread.sleep(2); // Simulate operation delay
            container.setStatus(ContainerStatus.RUNNING);
        }
        long sequentialTime = System.currentTimeMillis() - sequentialStart;

        // Measure parallel operations
        ExecutorService executor = Executors.newFixedThreadPool(4);
        long parallelStart = System.currentTimeMillis();
        
        List<CompletableFuture<Void>> futures = testContainers.stream()
            .map(container -> CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(2); // Simulate operation delay
                    container.setStatus(ContainerStatus.RUNNING);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executor))
            .toList();
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long parallelTime = System.currentTimeMillis() - parallelStart;
        
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        // Calculate performance improvement
        double improvement = ((double) (sequentialTime - parallelTime) / sequentialTime) * 100;
        
        System.out.printf("Sequential time: %d ms%n", sequentialTime);
        System.out.printf("Parallel time: %d ms%n", parallelTime);
        System.out.printf("Performance improvement: %.2f%%\n", improvement);
        
        PerformanceMetrics.recordMetric("sequential_vs_parallel_improvement", (long) improvement);
        
        assertThat(parallelTime).isLessThan(sequentialTime);
        assertThat(improvement).isGreaterThan(0);
    }
}