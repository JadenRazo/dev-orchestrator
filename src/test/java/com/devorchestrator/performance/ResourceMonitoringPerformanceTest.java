package com.devorchestrator.performance;

import com.devorchestrator.service.ResourceMonitoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ResourceMonitoringPerformanceTest {

    private ResourceMonitoringService resourceService;
    private OperatingSystemMXBean osBean;
    private MemoryMXBean memoryBean;

    @BeforeEach
    void setUp() {
        resourceService = new ResourceMonitoringService();
        osBean = ManagementFactory.getOperatingSystemMXBean();
        memoryBean = ManagementFactory.getMemoryMXBean();
        
        // Set test configuration values
        ReflectionTestUtils.setField(resourceService, "maxCpuPercent", 80);
        ReflectionTestUtils.setField(resourceService, "maxMemoryPercent", 80);
        ReflectionTestUtils.setField(resourceService, "maxDiskPercent", 85);
    }

    @Test
    @DisplayName("Measure CPU usage monitoring overhead")
    void measureCpuUsageMonitoringOverhead() {
        // Given
        int numberOfMeasurements = 1000;
        long startTime = System.currentTimeMillis();
        
        // When - Measure CPU monitoring overhead
        for (int i = 0; i < numberOfMeasurements; i++) {
            double cpuUsage = resourceService.getCurrentCpuUsage();
            assertThat(cpuUsage).isGreaterThanOrEqualTo(0);
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double averageTime = (double) totalTime / numberOfMeasurements;
        
        // Then
        System.out.printf("CPU monitoring overhead: %.2f ms average per call (%d calls in %d ms)%n", 
            averageTime, numberOfMeasurements, totalTime);
        
        PerformanceMetrics.recordMetric("cpu_monitoring_total_ms", totalTime);
        PerformanceMetrics.recordMetric("cpu_monitoring_average_ms", (long) averageTime);
        PerformanceMetrics.recordMetric("cpu_monitoring_calls", numberOfMeasurements);
        
        assertThat(averageTime).isLessThan(10.0); // Should be less than 10ms per call
    }

    @Test
    @DisplayName("Measure memory usage monitoring overhead")
    void measureMemoryUsageMonitoringOverhead() {
        // Given
        int numberOfMeasurements = 1000;
        long startTime = System.currentTimeMillis();
        
        // When - Measure memory monitoring overhead
        for (int i = 0; i < numberOfMeasurements; i++) {
            long memoryUsage = resourceService.getCurrentMemoryUsageMb();
            long totalMemory = resourceService.getTotalSystemMemoryMb();
            long availableMemory = resourceService.getAvailableMemoryMb();
            
            assertThat(memoryUsage).isGreaterThan(0);
            assertThat(totalMemory).isGreaterThan(0);
            assertThat(availableMemory).isGreaterThanOrEqualTo(0);
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double averageTime = (double) totalTime / numberOfMeasurements;
        
        // Then
        System.out.printf("Memory monitoring overhead: %.2f ms average per call (%d calls in %d ms)%n", 
            averageTime, numberOfMeasurements, totalTime);
        
        PerformanceMetrics.recordMetric("memory_monitoring_total_ms", totalTime);
        PerformanceMetrics.recordMetric("memory_monitoring_average_ms", (long) averageTime);
        PerformanceMetrics.recordMetric("memory_monitoring_calls", numberOfMeasurements);
        
        assertThat(averageTime).isLessThan(5.0); // Should be less than 5ms per call
    }

    @Test
    @DisplayName("Measure resource availability check performance")
    void measureResourceAvailabilityCheckPerformance() {
        // Given
        int numberOfChecks = 1000;
        int cpuMb = 500;
        long memoryMb = 1024;
        long startTime = System.currentTimeMillis();
        
        // When - Measure resource availability check overhead
        for (int i = 0; i < numberOfChecks; i++) {
            boolean available = resourceService.hasAvailableResources(cpuMb, memoryMb);
            // Resource availability result doesn't matter for performance test
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double averageTime = (double) totalTime / numberOfChecks;
        
        // Then
        System.out.printf("Resource availability check overhead: %.2f ms average per call (%d calls in %d ms)%n", 
            averageTime, numberOfChecks, totalTime);
        
        PerformanceMetrics.recordMetric("resource_check_total_ms", totalTime);
        PerformanceMetrics.recordMetric("resource_check_average_ms", (long) averageTime);
        PerformanceMetrics.recordMetric("resource_check_calls", numberOfChecks);
        
        assertThat(averageTime).isLessThan(2.0); // Should be less than 2ms per call
    }

    @Test
    @DisplayName("Measure concurrent resource monitoring performance")
    void measureConcurrentResourceMonitoringPerformance() throws Exception {
        // Given
        int numberOfThreads = 10;
        int callsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        long startTime = System.currentTimeMillis();
        
        // When - Measure concurrent resource monitoring
        CompletableFuture<Void>[] futures = IntStream.range(0, numberOfThreads)
            .mapToObj(threadIndex -> CompletableFuture.runAsync(() -> {
                for (int i = 0; i < callsPerThread; i++) {
                    double cpuUsage = resourceService.getCurrentCpuUsage();
                    long memoryUsage = resourceService.getCurrentMemoryUsageMb();
                    boolean available = resourceService.hasAvailableResources(100, 256);
                }
            }, executor))
            .toArray(CompletableFuture[]::new);
        
        CompletableFuture.allOf(futures).join();
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        int totalCalls = numberOfThreads * callsPerThread;
        double averageTime = (double) totalTime / totalCalls;
        
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
        
        // Then
        System.out.printf("Concurrent resource monitoring: %.2f ms average per call (%d threads, %d calls each, %d ms total)%n", 
            averageTime, numberOfThreads, callsPerThread, totalTime);
        
        PerformanceMetrics.recordMetric("concurrent_resource_monitoring_total_ms", totalTime);
        PerformanceMetrics.recordMetric("concurrent_resource_monitoring_average_ms", (long) averageTime);
        PerformanceMetrics.recordMetric("concurrent_resource_monitoring_threads", numberOfThreads);
        PerformanceMetrics.recordMetric("concurrent_resource_monitoring_total_calls", totalCalls);
        
        assertThat(averageTime).isLessThan(5.0); // Should handle concurrent access efficiently
    }

    @Test
    @DisplayName("Measure resource stats caching benefit")
    void measureResourceStatsCachingBenefit() {
        // Given
        int numberOfCalls = 100;
        
        // Measure without caching (direct calls)
        long startTimeNoCaching = System.currentTimeMillis();
        for (int i = 0; i < numberOfCalls; i++) {
            resourceService.getCurrentCpuUsage();
            resourceService.getCurrentMemoryUsageMb();
            resourceService.getTotalSystemMemoryMb();
            resourceService.getAvailableMemoryMb();
        }
        long noCachingTime = System.currentTimeMillis() - startTimeNoCaching;
        
        // Measure with caching simulation (getCurrentResourceStats)
        long startTimeWithCaching = System.currentTimeMillis();
        for (int i = 0; i < numberOfCalls; i++) {
            resourceService.getCurrentResourceStats();
        }
        long cachingTime = System.currentTimeMillis() - startTimeWithCaching;
        
        // Calculate performance improvement
        double improvement = ((double) (noCachingTime - cachingTime) / noCachingTime) * 100;
        
        // Then
        System.out.printf("Resource monitoring without caching: %d ms%n", noCachingTime);
        System.out.printf("Resource monitoring with caching: %d ms%n", cachingTime);
        System.out.printf("Caching performance improvement: %.2f%%\n", improvement);
        
        PerformanceMetrics.recordMetric("resource_monitoring_no_cache_ms", noCachingTime);
        PerformanceMetrics.recordMetric("resource_monitoring_with_cache_ms", cachingTime);
        PerformanceMetrics.recordMetric("resource_caching_improvement_percent", (long) improvement);
        
        assertThat(cachingTime).isLessThanOrEqualTo(noCachingTime);
    }

    @Test
    @DisplayName("Measure resource allocation and release performance")
    void measureResourceAllocationPerformance() {
        // Given
        int numberOfOperations = 1000;
        int cpuMb = 100;
        long memoryMb = 256;
        long startTime = System.currentTimeMillis();
        
        // When - Measure allocation/release performance
        for (int i = 0; i < numberOfOperations; i++) {
            try {
                resourceService.allocateResources(cpuMb, memoryMb);
                resourceService.releaseResources(cpuMb, memoryMb);
            } catch (Exception e) {
                // Ignore allocation failures for performance test
            }
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double averageTime = (double) totalTime / numberOfOperations;
        
        // Then
        System.out.printf("Resource allocation/release overhead: %.2f ms average per operation (%d operations in %d ms)%n", 
            averageTime, numberOfOperations, totalTime);
        
        PerformanceMetrics.recordMetric("resource_allocation_total_ms", totalTime);
        PerformanceMetrics.recordMetric("resource_allocation_average_ms", (long) averageTime);
        PerformanceMetrics.recordMetric("resource_allocation_operations", numberOfOperations);
        
        assertThat(averageTime).isLessThan(1.0); // Should be very fast for atomic operations
    }

    @Test
    @DisplayName("Compare blocking vs non-blocking resource monitoring")
    void compareBlockingVsNonBlockingResourceMonitoring() throws Exception {
        // Given
        int numberOfMeasurements = 500;
        
        // Measure blocking (synchronous) resource monitoring
        long blockingStart = System.currentTimeMillis();
        for (int i = 0; i < numberOfMeasurements; i++) {
            resourceService.getCurrentCpuUsage();
            resourceService.getCurrentMemoryUsageMb();
        }
        long blockingTime = System.currentTimeMillis() - blockingStart;
        
        // Measure non-blocking (asynchronous) resource monitoring
        ExecutorService executor = Executors.newFixedThreadPool(4);
        long nonBlockingStart = System.currentTimeMillis();
        
        CompletableFuture<Void>[] futures = IntStream.range(0, numberOfMeasurements)
            .mapToObj(i -> CompletableFuture.runAsync(() -> {
                resourceService.getCurrentCpuUsage();
                resourceService.getCurrentMemoryUsageMb();
            }, executor))
            .toArray(CompletableFuture[]::new);
        
        CompletableFuture.allOf(futures).join();
        long nonBlockingTime = System.currentTimeMillis() - nonBlockingStart;
        
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
        
        // Calculate improvement
        double improvement = ((double) (blockingTime - nonBlockingTime) / blockingTime) * 100;
        
        // Then
        System.out.printf("Blocking resource monitoring: %d ms%n", blockingTime);
        System.out.printf("Non-blocking resource monitoring: %d ms%n", nonBlockingTime);
        System.out.printf("Non-blocking improvement: %.2f%%\n", improvement);
        
        PerformanceMetrics.recordMetric("blocking_resource_monitoring_ms", blockingTime);
        PerformanceMetrics.recordMetric("nonblocking_resource_monitoring_ms", nonBlockingTime);
        PerformanceMetrics.recordMetric("nonblocking_improvement_percent", (long) improvement);
        
        assertThat(improvement).isGreaterThanOrEqualTo(0);
    }
}