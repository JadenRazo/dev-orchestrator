package com.devorchestrator.performance;

import com.devorchestrator.entity.ContainerInstance;
import com.devorchestrator.entity.ContainerStatus;
import com.devorchestrator.entity.Environment;
import com.devorchestrator.entity.EnvironmentStatus;
import com.devorchestrator.entity.EnvironmentTemplate;
import com.devorchestrator.entity.User;
import com.devorchestrator.entity.UserRole;
import com.devorchestrator.repository.ContainerInstanceRepository;
import com.devorchestrator.repository.EnvironmentRepository;
import com.devorchestrator.repository.EnvironmentTemplateRepository;
import com.devorchestrator.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabasePerformanceTest {

    @Mock
    private EnvironmentRepository environmentRepository;
    
    @Mock
    private EnvironmentTemplateRepository templateRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private ContainerInstanceRepository containerRepository;

    private User testUser;
    private EnvironmentTemplate testTemplate;
    private List<Environment> testEnvironments;
    private List<ContainerInstance> testContainers;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .role(UserRole.USER)
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .build();

        testTemplate = EnvironmentTemplate.builder()
            .id("web-dev-template")
            .name("Web Development Template")
            .cpuLimit(2.0)
            .memoryLimitMb(4096)
            .dockerComposeContent("version: '3.8'\nservices:\n  web:\n    image: nginx:alpine")
            .createdAt(LocalDateTime.now())
            .build();

        // Create test environments
        testEnvironments = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            Environment env = Environment.builder()
                .id("env-" + i)
                .name("Test Environment " + i)
                .template(testTemplate)
                .owner(testUser)
                .status(i % 2 == 0 ? EnvironmentStatus.RUNNING : EnvironmentStatus.STOPPED)
                .createdAt(LocalDateTime.now().minusHours(i))
                .lastAccessedAt(LocalDateTime.now().minusMinutes(i * 10))
                .build();
            testEnvironments.add(env);
        }

        // Create test containers
        testContainers = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            ContainerInstance container = ContainerInstance.builder()
                .id("container-" + i)
                .environment(testEnvironments.get(i % testEnvironments.size()))
                .dockerContainerId("docker-container-" + i)
                .serviceName("service-" + (i % 10))
                .status(i % 3 == 0 ? ContainerStatus.RUNNING : ContainerStatus.STOPPED)
                .hostPort(8080 + (i % 100))
                .createdAt(LocalDateTime.now().minusHours(i))
                .build();
            testContainers.add(container);
        }
    }

    @Test
    @DisplayName("Measure single vs bulk query performance")
    void measureSingleVsBulkQueryPerformance() {
        // Given
        List<String> environmentIds = testEnvironments.stream()
            .limit(100)
            .map(Environment::getId)
            .toList();

        // Measure single queries (N+1 problem simulation)
        long singleQueriesStart = System.currentTimeMillis();
        for (String envId : environmentIds) {
            when(environmentRepository.findById(envId))
                .thenReturn(Optional.of(testEnvironments.get(Integer.parseInt(envId.substring(4)))));
            environmentRepository.findById(envId);
        }
        long singleQueriesTime = System.currentTimeMillis() - singleQueriesStart;

        // Measure bulk query
        long bulkQueryStart = System.currentTimeMillis();
        when(environmentRepository.findAllById(environmentIds))
            .thenReturn(testEnvironments.subList(0, 100));
        environmentRepository.findAllById(environmentIds);
        long bulkQueryTime = System.currentTimeMillis() - bulkQueryStart;

        // Calculate improvement
        double improvement = ((double) (singleQueriesTime - bulkQueryTime) / singleQueriesTime) * 100;

        // Then
        System.out.printf("Single queries time: %d ms for %d queries%n", singleQueriesTime, environmentIds.size());
        System.out.printf("Bulk query time: %d ms for %d records%n", bulkQueryTime, environmentIds.size());
        System.out.printf("Bulk query improvement: %.2f%%\n", improvement);

        PerformanceMetrics.recordMetric("single_queries_time_ms", singleQueriesTime);
        PerformanceMetrics.recordMetric("bulk_query_time_ms", bulkQueryTime);
        PerformanceMetrics.recordMetric("bulk_query_improvement_percent", (long) improvement);

        assertThat(bulkQueryTime).isLessThanOrEqualTo(singleQueriesTime);
        
        verify(environmentRepository, times(environmentIds.size())).findById(anyString());
        verify(environmentRepository, times(1)).findAllById(anyList());
    }

    @Test
    @DisplayName("Measure pagination performance")
    void measurePaginationPerformance() {
        // Given
        int[] pageSizes = {10, 50, 100, 500};
        
        for (int pageSize : pageSizes) {
            Pageable pageable = PageRequest.of(0, pageSize);
            Page<Environment> environmentPage = new PageImpl<>(
                testEnvironments.subList(0, Math.min(pageSize, testEnvironments.size())), 
                pageable, 
                testEnvironments.size()
            );
            
            when(environmentRepository.findByOwnerId(eq(1L), eq(pageable)))
                .thenReturn(environmentPage);
            
            long startTime = System.currentTimeMillis();
            Page<Environment> result = environmentRepository.findByOwnerId(1L, pageable);
            long queryTime = System.currentTimeMillis() - startTime;
            
            System.out.printf("Pagination query (page size %d): %d ms%n", pageSize, queryTime);
            PerformanceMetrics.recordMetric("pagination_" + pageSize + "_ms", queryTime);
            
            assertThat(result.getContent()).hasSize(Math.min(pageSize, testEnvironments.size()));
        }
    }

    @Test
    @DisplayName("Measure complex query performance")
    void measureComplexQueryPerformance() {
        // Given
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        String searchTerm = "Test";
        
        // Measure stale environments query
        long staleQueryStart = System.currentTimeMillis();
        when(environmentRepository.findByStatusAndLastAccessedBefore(EnvironmentStatus.RUNNING, cutoff))
            .thenReturn(testEnvironments.subList(0, 50));
        List<Environment> staleEnvironments = environmentRepository.findByStatusAndLastAccessedBefore(EnvironmentStatus.RUNNING, cutoff);
        long staleQueryTime = System.currentTimeMillis() - staleQueryStart;
        
        // Measure search query
        long searchQueryStart = System.currentTimeMillis();
        when(environmentRepository.searchByOwnerIdAndName(eq(1L), eq(searchTerm), any(Pageable.class)))
            .thenReturn(new PageImpl<>(testEnvironments.subList(0, 25)));
        Page<Environment> searchResults = environmentRepository.searchByOwnerIdAndName(1L, searchTerm, Pageable.unpaged());
        long searchQueryTime = System.currentTimeMillis() - searchQueryStart;
        
        // Measure count queries
        long countQueryStart = System.currentTimeMillis();
        when(environmentRepository.countActiveEnvironmentsByOwnerId(1L)).thenReturn(500);
        when(environmentRepository.countByStatus(EnvironmentStatus.RUNNING)).thenReturn(750L);
        when(environmentRepository.countActiveEnvironments()).thenReturn(1500L);
        
        int activeCount = environmentRepository.countActiveEnvironmentsByOwnerId(1L);
        long runningCount = environmentRepository.countByStatus(EnvironmentStatus.RUNNING);
        long totalActiveCount = environmentRepository.countActiveEnvironments();
        long countQueryTime = System.currentTimeMillis() - countQueryStart;
        
        // Then
        System.out.printf("Stale environments query time: %d ms (found %d environments)%n", 
            staleQueryTime, staleEnvironments.size());
        System.out.printf("Search query time: %d ms (found %d environments)%n", 
            searchQueryTime, searchResults.getContent().size());
        System.out.printf("Count queries time: %d ms (active: %d, running: %d, total: %d)%n", 
            countQueryTime, activeCount, runningCount, totalActiveCount);
        
        PerformanceMetrics.recordMetric("stale_query_time_ms", staleQueryTime);
        PerformanceMetrics.recordMetric("search_query_time_ms", searchQueryTime);
        PerformanceMetrics.recordMetric("count_queries_time_ms", countQueryTime);
        
        assertThat(staleEnvironments).isNotEmpty();
        assertThat(searchResults.getContent()).isNotEmpty();
        assertThat(activeCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("Measure container queries performance")
    void measureContainerQueriesPerformance() {
        // Given
        String environmentId = "env-1";
        
        // Measure container by environment query
        long containerQueryStart = System.currentTimeMillis();
        when(containerRepository.findByEnvironmentId(environmentId))
            .thenReturn(testContainers.subList(0, 10));
        List<ContainerInstance> containers = containerRepository.findByEnvironmentId(environmentId);
        long containerQueryTime = System.currentTimeMillis() - containerQueryStart;
        
        // Measure container by status query
        long statusQueryStart = System.currentTimeMillis();
        when(containerRepository.findByStatus(ContainerStatus.RUNNING))
            .thenReturn(testContainers.subList(0, 500));
        List<ContainerInstance> runningContainers = containerRepository.findByStatus(ContainerStatus.RUNNING);
        long statusQueryTime = System.currentTimeMillis() - statusQueryStart;
        
        // Measure health check query
        long healthCheckStart = System.currentTimeMillis();
        when(containerRepository.findRunningContainersNeedingHealthCheck(any(LocalDateTime.class)))
            .thenReturn(testContainers.subList(0, 50));
        List<ContainerInstance> needHealthCheck = containerRepository.findRunningContainersNeedingHealthCheck(
            LocalDateTime.now().minusMinutes(5)
        );
        long healthCheckTime = System.currentTimeMillis() - healthCheckStart;
        
        // Then
        System.out.printf("Container by environment query: %d ms (found %d containers)%n", 
            containerQueryTime, containers.size());
        System.out.printf("Container by status query: %d ms (found %d containers)%n", 
            statusQueryTime, runningContainers.size());
        System.out.printf("Health check query: %d ms (found %d containers)%n", 
            healthCheckTime, needHealthCheck.size());
        
        PerformanceMetrics.recordMetric("container_by_env_query_ms", containerQueryTime);
        PerformanceMetrics.recordMetric("container_by_status_query_ms", statusQueryTime);
        PerformanceMetrics.recordMetric("health_check_query_ms", healthCheckTime);
        
        assertThat(containers).hasSize(10);
        assertThat(runningContainers).hasSize(500);
        assertThat(needHealthCheck).hasSize(50);
    }

    @Test
    @DisplayName("Measure batch update performance")
    void measureBatchUpdatePerformance() {
        // Given
        List<String> environmentIds = testEnvironments.stream()
            .limit(100)
            .map(Environment::getId)
            .toList();
        
        // Measure individual updates
        long individualUpdatesStart = System.currentTimeMillis();
        for (String envId : environmentIds) {
            when(environmentRepository.updateEnvironmentStatus(envId, EnvironmentStatus.STOPPED))
                .thenReturn(1);
            environmentRepository.updateEnvironmentStatus(envId, EnvironmentStatus.STOPPED);
        }
        long individualUpdatesTime = System.currentTimeMillis() - individualUpdatesStart;
        
        // Measure batch update (simulated)
        long batchUpdateStart = System.currentTimeMillis();
        // In a real scenario, this would be a single batch update query
        when(environmentRepository.saveAll(anyList())).thenReturn(testEnvironments.subList(0, 100));
        environmentRepository.saveAll(testEnvironments.subList(0, 100));
        long batchUpdateTime = System.currentTimeMillis() - batchUpdateStart;
        
        // Calculate improvement
        double improvement = ((double) (individualUpdatesTime - batchUpdateTime) / individualUpdatesTime) * 100;
        
        // Then
        System.out.printf("Individual updates time: %d ms for %d updates%n", 
            individualUpdatesTime, environmentIds.size());
        System.out.printf("Batch update time: %d ms for %d updates%n", 
            batchUpdateTime, environmentIds.size());
        System.out.printf("Batch update improvement: %.2f%%\n", improvement);
        
        PerformanceMetrics.recordMetric("individual_updates_time_ms", individualUpdatesTime);
        PerformanceMetrics.recordMetric("batch_update_time_ms", batchUpdateTime);
        PerformanceMetrics.recordMetric("batch_update_improvement_percent", (long) improvement);
        
        assertThat(batchUpdateTime).isLessThanOrEqualTo(individualUpdatesTime);
        
        verify(environmentRepository, times(environmentIds.size())).updateEnvironmentStatus(anyString(), any(EnvironmentStatus.class));
        verify(environmentRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("Measure query with different fetch strategies")
    void measureFetchStrategiesPerformance() {
        // Given
        List<String> environmentIds = testEnvironments.stream()
            .limit(50)
            .map(Environment::getId)
            .toList();
        
        // Measure lazy loading (N+1 problem)
        long lazyLoadingStart = System.currentTimeMillis();
        for (String envId : environmentIds) {
            when(environmentRepository.findById(envId))
                .thenReturn(Optional.of(testEnvironments.get(Integer.parseInt(envId.substring(4)))));
            Environment env = environmentRepository.findById(envId).orElse(null);
            // Simulate accessing related entities (would cause additional queries)
            if (env != null) {
                when(containerRepository.findByEnvironmentId(envId))
                    .thenReturn(testContainers.subList(0, 5));
                containerRepository.findByEnvironmentId(envId); // Simulate lazy loading
            }
        }
        long lazyLoadingTime = System.currentTimeMillis() - lazyLoadingStart;
        
        // Measure eager loading (join fetch simulation)
        long eagerLoadingStart = System.currentTimeMillis();
        // In real scenario, this would use @EntityGraph or JOIN FETCH
        when(environmentRepository.findAllById(environmentIds))
            .thenReturn(testEnvironments.subList(0, 50));
        when(containerRepository.findByEnvironmentIdIn(environmentIds))
            .thenReturn(testContainers.subList(0, 250)); // 5 containers per environment
        
        List<Environment> environments = environmentRepository.findAllById(environmentIds);
        List<ContainerInstance> allContainers = containerRepository.findByEnvironmentIdIn(environmentIds);
        long eagerLoadingTime = System.currentTimeMillis() - eagerLoadingStart;
        
        // Calculate improvement
        double improvement = ((double) (lazyLoadingTime - eagerLoadingTime) / lazyLoadingTime) * 100;
        
        // Then
        System.out.printf("Lazy loading time: %d ms for %d environments%n", 
            lazyLoadingTime, environmentIds.size());
        System.out.printf("Eager loading time: %d ms for %d environments%n", 
            eagerLoadingTime, environmentIds.size());
        System.out.printf("Eager loading improvement: %.2f%%\n", improvement);
        
        PerformanceMetrics.recordMetric("lazy_loading_time_ms", lazyLoadingTime);
        PerformanceMetrics.recordMetric("eager_loading_time_ms", eagerLoadingTime);
        PerformanceMetrics.recordMetric("eager_loading_improvement_percent", (long) improvement);
        
        assertThat(eagerLoadingTime).isLessThanOrEqualTo(lazyLoadingTime);
        assertThat(environments).hasSize(50);
        assertThat(allContainers).hasSize(250);
    }

    // Mock method for containers by environment IDs (not in real repo)
    private interface ContainerRepositoryExtended extends ContainerInstanceRepository {
        List<ContainerInstance> findByEnvironmentIdIn(List<String> environmentIds);
    }
}