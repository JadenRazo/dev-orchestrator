package com.devorchestrator.performance;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Development Environment Orchestrator - Performance Test Suite")
class PerformanceTestSuite {

    private static LocalDateTime testStartTime;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @BeforeAll
    static void setUp() {
        testStartTime = LocalDateTime.now();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    PERFORMANCE TEST SUITE STARTED                           ║");
        System.out.println("║                                                                              ║");
        System.out.printf("║  Start Time: %-63s ║%n", testStartTime.format(FORMATTER));
        System.out.println("║  Project: Development Environment Orchestrator                              ║");
        System.out.println("║  Purpose: Measure current performance and identify optimization opportunities║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        
        PerformanceMetrics.clearMetrics();
    }

    @Test
    @Order(1)
    @DisplayName("Container Orchestration Performance Tests")
    void runContainerOrchestrationTests() {
        System.out.println("┌─ Container Orchestration Performance Tests ─────────────────────────────────┐");
        
        ContainerOrchestrationPerformanceTest containerTests = new ContainerOrchestrationPerformanceTest();
        try {
            containerTests.setUp();
            
            System.out.println("│ Running sequential container start performance test...");
            containerTests.measureSequentialContainerStartPerformance();
            
            System.out.println("│ Running parallel container start performance test...");
            containerTests.measureParallelContainerStartPerformance();
            
            System.out.println("│ Running Docker API call overhead test...");
            containerTests.measureDockerApiCallOverhead();
            
            System.out.println("│ Running container cleanup performance test...");
            containerTests.measureContainerCleanupPerformance();
            
            System.out.println("│ Running environment creation performance test...");
            containerTests.measureEnvironmentCreationPerformance();
            
            System.out.println("│ Running sequential vs parallel comparison test...");
            containerTests.compareSequentialVsParallelOperations();
            
        } catch (Exception e) {
            System.out.printf("│ Error in container orchestration tests: %s%n", e.getMessage());
        }
        
        System.out.println("└──────────────────────────────────────────────────────────────────────────────┘");
        System.out.println();
    }

    @Test
    @Order(2)
    @DisplayName("Resource Monitoring Performance Tests")
    void runResourceMonitoringTests() {
        System.out.println("┌─ Resource Monitoring Performance Tests ─────────────────────────────────────┐");
        
        ResourceMonitoringPerformanceTest resourceTests = new ResourceMonitoringPerformanceTest();
        try {
            resourceTests.setUp();
            
            System.out.println("│ Running CPU usage monitoring overhead test...");
            resourceTests.measureCpuUsageMonitoringOverhead();
            
            System.out.println("│ Running memory usage monitoring overhead test...");
            resourceTests.measureMemoryUsageMonitoringOverhead();
            
            System.out.println("│ Running resource availability check performance test...");
            resourceTests.measureResourceAvailabilityCheckPerformance();
            
            System.out.println("│ Running concurrent resource monitoring test...");
            resourceTests.measureConcurrentResourceMonitoringPerformance();
            
            System.out.println("│ Running resource stats caching benefit test...");
            resourceTests.measureResourceStatsCachingBenefit();
            
            System.out.println("│ Running resource allocation performance test...");
            resourceTests.measureResourceAllocationPerformance();
            
            System.out.println("│ Running blocking vs non-blocking comparison test...");
            resourceTests.compareBlockingVsNonBlockingResourceMonitoring();
            
        } catch (Exception e) {
            System.out.printf("│ Error in resource monitoring tests: %s%n", e.getMessage());
        }
        
        System.out.println("└──────────────────────────────────────────────────────────────────────────────┘");
        System.out.println();
    }

    @Test
    @Order(3)
    @DisplayName("Environment Service Performance Tests")
    void runEnvironmentServiceTests() {
        System.out.println("┌─ Environment Service Performance Tests ──────────────────────────────────────┐");
        
        EnvironmentServicePerformanceTest serviceTests = new EnvironmentServicePerformanceTest();
        try {
            serviceTests.setUp();
            
            System.out.println("│ Running single environment creation performance test...");
            serviceTests.measureSingleEnvironmentCreationPerformance();
            
            System.out.println("│ Running bulk environment creation performance test...");
            serviceTests.measureBulkEnvironmentCreationPerformance();
            
            System.out.println("│ Running concurrent environment creation performance test...");
            serviceTests.measureConcurrentEnvironmentCreationPerformance();
            
            System.out.println("│ Running environment start/stop performance test...");
            serviceTests.measureEnvironmentStartStopPerformance();
            
            System.out.println("│ Running environment deletion performance test...");
            serviceTests.measureEnvironmentDeletionPerformance();
            
            System.out.println("│ Running user environments retrieval performance test...");
            serviceTests.measureUserEnvironmentsRetrievalPerformance();
            
            System.out.println("│ Running stale environments cleanup performance test...");
            serviceTests.measureStaleEnvironmentsCleanupPerformance();
            
            System.out.println("│ Running cache hit vs miss performance test...");
            serviceTests.measureCacheHitVsMissPerformance();
            
            System.out.println("│ Running WebSocket notification overhead test...");
            serviceTests.measureWebSocketNotificationOverhead();
            
        } catch (Exception e) {
            System.out.printf("│ Error in environment service tests: %s%n", e.getMessage());
        }
        
        System.out.println("└──────────────────────────────────────────────────────────────────────────────┘");
        System.out.println();
    }

    @Test
    @Order(4)
    @DisplayName("Database Performance Tests")
    void runDatabaseTests() {
        System.out.println("┌─ Database Performance Tests ─────────────────────────────────────────────────┐");
        
        DatabasePerformanceTest dbTests = new DatabasePerformanceTest();
        try {
            dbTests.setUp();
            
            System.out.println("│ Running single vs bulk query performance test...");
            dbTests.measureSingleVsBulkQueryPerformance();
            
            System.out.println("│ Running pagination performance test...");
            dbTests.measurePaginationPerformance();
            
            System.out.println("│ Running complex query performance test...");
            dbTests.measureComplexQueryPerformance();
            
            System.out.println("│ Running container queries performance test...");
            dbTests.measureContainerQueriesPerformance();
            
            System.out.println("│ Running batch update performance test...");
            dbTests.measureBatchUpdatePerformance();
            
            System.out.println("│ Running fetch strategies performance test...");
            dbTests.measureFetchStrategiesPerformance();
            
        } catch (Exception e) {
            System.out.printf("│ Error in database tests: %s%n", e.getMessage());
        }
        
        System.out.println("└──────────────────────────────────────────────────────────────────────────────┘");
        System.out.println();
    }

    @AfterAll
    static void tearDown() {
        LocalDateTime testEndTime = LocalDateTime.now();
        long totalTestTime = java.time.Duration.between(testStartTime, testEndTime).toMillis();
        
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                           PERFORMANCE TEST RESULTS                          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        
        generatePerformanceReport();
        
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                           OPTIMIZATION RECOMMENDATIONS                      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
        
        generateOptimizationRecommendations();
        
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                              TEST SUITE COMPLETED                           ║");
        System.out.println("║                                                                              ║");
        System.out.printf("║  End Time: %-65s ║%n", testEndTime.format(FORMATTER));
        System.out.printf("║  Total Test Duration: %-54d ms ║%n", totalTestTime);
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
    }

    private static void generatePerformanceReport() {
        Map<String, Object> metrics = PerformanceMetrics.getAllMetrics();
        
        System.out.println("┌─ Container Orchestration Metrics ───────────────────────────────────────────┐");
        printMetric("Sequential Container Start", "container_start_sequential", "ms");
        printMetric("Parallel Container Start", "container_start_parallel", "ms");
        printMetric("Container Start Improvement", "container_start_improvement_percent", "%");
        printMetric("Docker API Call Average", "docker_api_call_average_ms", "ms");
        printMetric("Environment Creation Time", "environment_creation_time_ms", "ms");
        printMetric("Sequential vs Parallel Improvement", "sequential_vs_parallel_improvement", "%");
        System.out.println("└──────────────────────────────────────────────────────────────────────────────┘");
        System.out.println();
        
        System.out.println("┌─ Resource Monitoring Metrics ───────────────────────────────────────────────┐");
        printMetric("CPU Monitoring Average", "cpu_monitoring_average_ms", "ms");
        printMetric("Memory Monitoring Average", "memory_monitoring_average_ms", "ms");
        printMetric("Resource Check Average", "resource_check_average_ms", "ms");
        printMetric("Resource Caching Improvement", "resource_caching_improvement_percent", "%");
        printMetric("Non-blocking Improvement", "nonblocking_improvement_percent", "%");
        System.out.println("└──────────────────────────────────────────────────────────────────────────────┘");
        System.out.println();
        
        System.out.println("┌─ Environment Service Metrics ───────────────────────────────────────────────┐");
        printMetric("Single Environment Creation", "single_environment_creation_ms", "ms");
        printMetric("Bulk Environment Creation Average", "bulk_environment_creation_average_ms", "ms");
        printMetric("Concurrent Creation Average", "concurrent_environment_creation_average_ms", "ms");
        printMetric("Concurrent Creation Improvement", "concurrent_creation_improvement_percent", "%");
        printMetric("Environment Start Time", "environment_start_time_ms", "ms");
        printMetric("Environment Stop Time", "environment_stop_time_ms", "ms");
        printMetric("Cache Improvement", "cache_improvement_percent", "%");
        System.out.println("└──────────────────────────────────────────────────────────────────────────────┘");
        System.out.println();
        
        System.out.println("┌─ Database Performance Metrics ──────────────────────────────────────────────┐");
        printMetric("Bulk Query Improvement", "bulk_query_improvement_percent", "%");
        printMetric("Batch Update Improvement", "batch_update_improvement_percent", "%");
        printMetric("Eager Loading Improvement", "eager_loading_improvement_percent", "%");
        printMetric("Stale Query Time", "stale_query_time_ms", "ms");
        printMetric("Search Query Time", "search_query_time_ms", "ms");
        System.out.println("└──────────────────────────────────────────────────────────────────────────────┘");
    }

    private static void printMetric(String name, String key, String unit) {
        Object value = PerformanceMetrics.getAllMetrics().get(key);
        if (value != null) {
            if (value instanceof Double) {
                System.out.printf("│ %-40s: %8.2f %-5s │%n", name, (Double) value, unit);
            } else {
                System.out.printf("│ %-40s: %8d %-5s │%n", name, ((Number) value).longValue(), unit);
            }
        } else {
            System.out.printf("│ %-40s: %8s %-5s │%n", name, "N/A", unit);
        }
    }

    private static void generateOptimizationRecommendations() {
        System.out.println();
        System.out.println("┌─ High Impact Optimizations ─────────────────────────────────────────────────┐");
        
        long parallelImprovement = PerformanceMetrics.getMetric("container_start_improvement_percent");
        if (parallelImprovement > 50) {
            System.out.println("│ 🚀 HIGH PRIORITY: Implement parallel container operations                   │");
            System.out.printf("│    Expected improvement: %d%% faster container start times                │%n", parallelImprovement);
        }
        
        long cachingImprovement = PerformanceMetrics.getMetric("resource_caching_improvement_percent");
        if (cachingImprovement > 30) {
            System.out.println("│ 🚀 HIGH PRIORITY: Implement resource monitoring caching                    │");
            System.out.printf("│    Expected improvement: %d%% faster resource checks                      │%n", cachingImprovement);
        }
        
        long bulkQueryImprovement = PerformanceMetrics.getMetric("bulk_query_improvement_percent");
        if (bulkQueryImprovement > 40) {
            System.out.println("│ 🚀 HIGH PRIORITY: Replace N+1 queries with bulk operations                 │");
            System.out.printf("│    Expected improvement: %d%% faster database operations                 │%n", bulkQueryImprovement);
        }
        
        System.out.println("└──────────────────────────────────────────────────────────────────────────────┘");
        System.out.println();
        
        System.out.println("┌─ Medium Impact Optimizations ───────────────────────────────────────────────┐");
        
        long concurrentImprovement = PerformanceMetrics.getMetric("concurrent_creation_improvement_percent");
        if (concurrentImprovement > 20) {
            System.out.println("│ 📈 MEDIUM PRIORITY: Implement concurrent environment creation               │");
            System.out.printf("│    Expected improvement: %d%% faster bulk operations                      │%n", concurrentImprovement);
        }
        
        long eagerLoadingImprovement = PerformanceMetrics.getMetric("eager_loading_improvement_percent");
        if (eagerLoadingImprovement > 25) {
            System.out.println("│ 📈 MEDIUM PRIORITY: Use @EntityGraph for eager loading                     │");
            System.out.printf("│    Expected improvement: %d%% faster query performance                    │%n", eagerLoadingImprovement);
        }
        
        System.out.println("└──────────────────────────────────────────────────────────────────────────────┘");
        System.out.println();
        
        System.out.println("┌─ Implementation Plan ────────────────────────────────────────────────────────┐");
        System.out.println("│ Phase 1: Parallel container operations (ContainerOrchestrationService)     │");
        System.out.println("│ Phase 2: Resource monitoring caching (ResourceMonitoringService)           │");
        System.out.println("│ Phase 3: Database query optimization (Repository layer)                    │");
        System.out.println("│ Phase 4: Async WebSocket notifications (@Async annotations)                │");
        System.out.println("│ Phase 5: Custom thread pool configuration                                   │");
        System.out.println("└──────────────────────────────────────────────────────────────────────────────┘");
    }
}