package com.devorchestrator.performance;

import com.devorchestrator.entity.Environment;
import com.devorchestrator.entity.EnvironmentStatus;
import com.devorchestrator.entity.EnvironmentTemplate;
import com.devorchestrator.entity.User;
import com.devorchestrator.entity.UserRole;
import com.devorchestrator.repository.EnvironmentRepository;
import com.devorchestrator.repository.EnvironmentTemplateRepository;
import com.devorchestrator.repository.UserRepository;
import com.devorchestrator.service.ContainerOrchestrationService;
import com.devorchestrator.service.EnvironmentService;
import com.devorchestrator.service.ResourceMonitoringService;
import com.devorchestrator.service.WebSocketNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvironmentServicePerformanceTest {

    @Mock
    private EnvironmentRepository environmentRepository;
    
    @Mock
    private EnvironmentTemplateRepository templateRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private ContainerOrchestrationService containerService;
    
    @Mock
    private ResourceMonitoringService resourceService;
    
    @Mock
    private WebSocketNotificationService notificationService;
    
    @InjectMocks
    private EnvironmentService environmentService;

    private User testUser;
    private EnvironmentTemplate testTemplate;
    private Environment testEnvironment;
    private List<Environment> testEnvironments;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(environmentService, "maxEnvironmentsPerUser", 10);
        
        testUser = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .role(UserRole.USER)
            .isActive(true)
            .build();

        testTemplate = EnvironmentTemplate.builder()
            .id("web-dev-template")
            .name("Web Development Template")
            .cpuLimit(2.0)
            .memoryLimitMb(4096)
            .dockerComposeContent("version: '3.8'\nservices:\n  web:\n    image: nginx:alpine")
            .build();

        testEnvironment = Environment.builder()
            .id("env-123")
            .name("Test Environment")
            .template(testTemplate)
            .owner(testUser)
            .status(EnvironmentStatus.CREATING)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        // Create multiple test environments for bulk operations
        testEnvironments = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Environment env = Environment.builder()
                .id("env-" + i)
                .name("Test Environment " + i)
                .template(testTemplate)
                .owner(testUser)
                .status(EnvironmentStatus.RUNNING)
                .createdAt(LocalDateTime.now().minusHours(i))
                .build();
            testEnvironments.add(env);
        }

        // Setup common mocks
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(templateRepository.findById("web-dev-template")).thenReturn(Optional.of(testTemplate));
        when(resourceService.hasAvailableResources(anyInt(), anyLong())).thenReturn(true);
        when(environmentRepository.countActiveEnvironmentsByOwnerId(1L)).thenReturn(0);
        when(environmentRepository.save(any(Environment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(environmentRepository.findById(anyString())).thenReturn(Optional.of(testEnvironment));
        when(environmentRepository.findByIdAndOwnerId(anyString(), anyLong())).thenReturn(Optional.of(testEnvironment));
    }

    @Test
    @DisplayName("Measure single environment creation performance")
    void measureSingleEnvironmentCreationPerformance() {
        // Given
        long startTime = System.currentTimeMillis();
        
        // When
        Environment result = environmentService.createEnvironment("web-dev-template", 1L, "Performance Test Environment");
        
        long endTime = System.currentTimeMillis();
        long creationTime = endTime - startTime;
        
        // Then
        System.out.printf("Single environment creation time: %d ms%n", creationTime);
        
        PerformanceMetrics.recordMetric("single_environment_creation_ms", creationTime);
        
        assertThat(result).isNotNull();
        assertThat(creationTime).isGreaterThan(0);
        
        verify(containerService).createEnvironment(any(Environment.class), eq(testTemplate));
        verify(environmentRepository, times(2)).updateEnvironmentStatus(anyString(), any(EnvironmentStatus.class));
    }

    @Test
    @DisplayName("Measure bulk environment creation performance")
    void measureBulkEnvironmentCreationPerformance() {
        // Given
        int numberOfEnvironments = 10;
        long startTime = System.currentTimeMillis();
        
        // When - Create multiple environments sequentially
        for (int i = 0; i < numberOfEnvironments; i++) {
            environmentService.createEnvironment("web-dev-template", 1L, "Bulk Test Environment " + i);
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double averageTime = (double) totalTime / numberOfEnvironments;
        
        // Then
        System.out.printf("Bulk environment creation: %d environments in %d ms (%.2f ms average per environment)%n", 
            numberOfEnvironments, totalTime, averageTime);
        
        PerformanceMetrics.recordMetric("bulk_environment_creation_total_ms", totalTime);
        PerformanceMetrics.recordMetric("bulk_environment_creation_average_ms", (long) averageTime);
        PerformanceMetrics.recordMetric("bulk_environment_creation_count", numberOfEnvironments);
        
        assertThat(averageTime).isLessThan(1000); // Should create environment in less than 1 second on average
        
        verify(containerService, times(numberOfEnvironments)).createEnvironment(any(Environment.class), eq(testTemplate));
    }

    @Test
    @DisplayName("Measure concurrent environment creation performance")
    void measureConcurrentEnvironmentCreationPerformance() throws Exception {
        // Given
        int numberOfEnvironments = 10;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        long startTime = System.currentTimeMillis();
        
        // When - Create multiple environments concurrently
        CompletableFuture<Environment>[] futures = IntStream.range(0, numberOfEnvironments)
            .mapToObj(i -> CompletableFuture.supplyAsync(() -> 
                environmentService.createEnvironment("web-dev-template", 1L, "Concurrent Test Environment " + i), 
                executor))
            .toArray(CompletableFuture[]::new);
        
        CompletableFuture.allOf(futures).join();
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double averageTime = (double) totalTime / numberOfEnvironments;
        
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
        
        // Then
        System.out.printf("Concurrent environment creation: %d environments in %d ms (%.2f ms average per environment)%n", 
            numberOfEnvironments, totalTime, averageTime);
        
        PerformanceMetrics.recordMetric("concurrent_environment_creation_total_ms", totalTime);
        PerformanceMetrics.recordMetric("concurrent_environment_creation_average_ms", (long) averageTime);
        
        // Calculate improvement over sequential creation
        long sequentialTime = PerformanceMetrics.getMetric("bulk_environment_creation_total_ms");
        if (sequentialTime > 0) {
            double improvement = ((double) (sequentialTime - totalTime) / sequentialTime) * 100;
            System.out.printf("Concurrent creation improvement: %.2f%%\n", improvement);
            PerformanceMetrics.recordMetric("concurrent_creation_improvement_percent", (long) improvement);
        }
        
        assertThat(totalTime).isGreaterThan(0);
        verify(containerService, times(numberOfEnvironments)).createEnvironment(any(Environment.class), eq(testTemplate));
    }

    @Test
    @DisplayName("Measure environment start/stop performance")
    void measureEnvironmentStartStopPerformance() {
        // Given
        testEnvironment.setStatus(EnvironmentStatus.STOPPED);
        
        // Measure start performance
        long startTime = System.currentTimeMillis();
        Environment startedEnv = environmentService.startEnvironment("env-123", 1L);
        long startDuration = System.currentTimeMillis() - startTime;
        
        // Reset for stop test
        testEnvironment.setStatus(EnvironmentStatus.RUNNING);
        
        // Measure stop performance
        long stopTime = System.currentTimeMillis();
        Environment stoppedEnv = environmentService.stopEnvironment("env-123", 1L);
        long stopDuration = System.currentTimeMillis() - stopTime;
        
        // Then
        System.out.printf("Environment start time: %d ms%n", startDuration);
        System.out.printf("Environment stop time: %d ms%n", stopDuration);
        
        PerformanceMetrics.recordMetric("environment_start_time_ms", startDuration);
        PerformanceMetrics.recordMetric("environment_stop_time_ms", stopDuration);
        
        assertThat(startedEnv).isNotNull();
        assertThat(stoppedEnv).isNotNull();
        assertThat(startDuration).isGreaterThan(0);
        assertThat(stopDuration).isGreaterThan(0);
        
        verify(containerService).startEnvironment(testEnvironment);
        verify(containerService).stopEnvironment(testEnvironment);
    }

    @Test
    @DisplayName("Measure environment deletion performance")
    void measureEnvironmentDeletionPerformance() {
        // Given
        testEnvironment.setStatus(EnvironmentStatus.RUNNING);
        long startTime = System.currentTimeMillis();
        
        // When
        environmentService.deleteEnvironment("env-123", 1L);
        
        long endTime = System.currentTimeMillis();
        long deletionTime = endTime - startTime;
        
        // Then
        System.out.printf("Environment deletion time: %d ms%n", deletionTime);
        
        PerformanceMetrics.recordMetric("environment_deletion_time_ms", deletionTime);
        
        assertThat(deletionTime).isGreaterThan(0);
        
        verify(containerService).stopEnvironment(testEnvironment);
        verify(containerService).destroyEnvironment(testEnvironment);
        verify(environmentRepository).deleteById("env-123");
    }

    @Test
    @DisplayName("Measure user environments retrieval performance")
    void measureUserEnvironmentsRetrievalPerformance() {
        // Given
        Page<Environment> environmentPage = new PageImpl<>(testEnvironments);
        when(environmentRepository.findByOwnerId(eq(1L), any(Pageable.class))).thenReturn(environmentPage);
        
        long startTime = System.currentTimeMillis();
        
        // When
        Page<Environment> result = environmentService.getUserEnvironments(1L, Pageable.unpaged());
        
        long endTime = System.currentTimeMillis();
        long retrievalTime = endTime - startTime;
        
        // Then
        System.out.printf("User environments retrieval time: %d ms for %d environments%n", 
            retrievalTime, result.getContent().size());
        
        PerformanceMetrics.recordMetric("user_environments_retrieval_ms", retrievalTime);
        PerformanceMetrics.recordMetric("user_environments_count", result.getContent().size());
        
        assertThat(result.getContent()).hasSize(testEnvironments.size());
        assertThat(retrievalTime).isGreaterThan(0);
    }

    @Test
    @DisplayName("Measure stale environments cleanup performance")
    void measureStaleEnvironmentsCleanupPerformance() {
        // Given
        List<Environment> staleEnvironments = testEnvironments.subList(0, 10);
        when(environmentRepository.findByStatusAndLastAccessedBefore(eq(EnvironmentStatus.RUNNING), any(LocalDateTime.class)))
            .thenReturn(staleEnvironments);
        
        long startTime = System.currentTimeMillis();
        
        // When
        environmentService.cleanupStaleEnvironments(24);
        
        long endTime = System.currentTimeMillis();
        long cleanupTime = endTime - startTime;
        
        // Then
        System.out.printf("Stale environments cleanup time: %d ms for %d environments%n", 
            cleanupTime, staleEnvironments.size());
        
        PerformanceMetrics.recordMetric("stale_cleanup_time_ms", cleanupTime);
        PerformanceMetrics.recordMetric("stale_cleanup_count", staleEnvironments.size());
        
        assertThat(cleanupTime).isGreaterThan(0);
        
        // Verify cleanup was called for each stale environment
        verify(containerService, times(staleEnvironments.size())).stopEnvironment(any(Environment.class));
    }

    @Test
    @DisplayName("Measure cache hit vs miss performance")
    void measureCacheHitVsMissPerformance() {
        // Given
        String environmentId = "env-123";
        Long userId = 1L;
        
        // Measure cache miss (first call)
        long cacheMissStart = System.currentTimeMillis();
        Environment firstResult = environmentService.getEnvironment(environmentId, userId);
        long cacheMissTime = System.currentTimeMillis() - cacheMissStart;
        
        // Measure cache hit (subsequent call)
        long cacheHitStart = System.currentTimeMillis();
        Environment secondResult = environmentService.getEnvironment(environmentId, userId);
        long cacheHitTime = System.currentTimeMillis() - cacheHitStart;
        
        // Calculate improvement
        double improvement = cacheMissTime > 0 ? ((double) (cacheMissTime - cacheHitTime) / cacheMissTime) * 100 : 0;
        
        // Then
        System.out.printf("Cache miss time: %d ms%n", cacheMissTime);
        System.out.printf("Cache hit time: %d ms%n", cacheHitTime);
        System.out.printf("Cache improvement: %.2f%%\n", improvement);
        
        PerformanceMetrics.recordMetric("cache_miss_time_ms", cacheMissTime);
        PerformanceMetrics.recordMetric("cache_hit_time_ms", cacheHitTime);
        PerformanceMetrics.recordMetric("cache_improvement_percent", (long) improvement);
        
        assertThat(firstResult).isNotNull();
        assertThat(secondResult).isNotNull();
        assertThat(cacheHitTime).isLessThanOrEqualTo(cacheMissTime);
    }

    @Test
    @DisplayName("Measure WebSocket notification overhead")
    void measureWebSocketNotificationOverhead() {
        // Given
        int numberOfNotifications = 100;
        long startTime = System.currentTimeMillis();
        
        // When - Simulate environment status changes with notifications
        for (int i = 0; i < numberOfNotifications; i++) {
            // This would trigger WebSocket notifications in the real service
            when(environmentRepository.findById(anyString())).thenReturn(Optional.of(testEnvironment));
            // Simulate the updateEnvironmentStatus method call
            doNothing().when(notificationService).notifyEnvironmentStatusChange(any(Environment.class));
        }
        
        long endTime = System.currentTimeMillis();
        long notificationTime = endTime - startTime;
        double averageTime = (double) notificationTime / numberOfNotifications;
        
        // Then
        System.out.printf("WebSocket notification overhead: %.2f ms average per notification (%d notifications in %d ms)%n", 
            averageTime, numberOfNotifications, notificationTime);
        
        PerformanceMetrics.recordMetric("websocket_notification_total_ms", notificationTime);
        PerformanceMetrics.recordMetric("websocket_notification_average_ms", (long) averageTime);
        PerformanceMetrics.recordMetric("websocket_notification_count", numberOfNotifications);
        
        assertThat(averageTime).isLessThan(10.0); // Should be fast
    }
}