package com.devorchestrator.repository;

import com.devorchestrator.entity.ResourceMetric;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ResourceMetricRepository extends JpaRepository<ResourceMetric, Long> {
    
    /**
     * Find metrics for a project within a time range
     */
    Page<ResourceMetric> findByProjectIdAndRecordedAtBetweenOrderByRecordedAtDesc(
        String projectId, LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    /**
     * Find metrics by type for a project
     */
    List<ResourceMetric> findByProjectIdAndMetricTypeAndRecordedAtBetweenOrderByRecordedAtDesc(
        String projectId, ResourceMetric.MetricType metricType, LocalDateTime start, LocalDateTime end);
    
    /**
     * Find metrics for a specific container
     */
    List<ResourceMetric> findByContainerIdAndRecordedAtBetweenOrderByRecordedAtDesc(
        String containerId, LocalDateTime start, LocalDateTime end);
    
    /**
     * Delete old metrics
     */
    void deleteByRecordedAtBefore(LocalDateTime cutoff);
    
    /**
     * Get average metric value for a project
     */
    @Query("SELECT AVG(m.value) FROM ResourceMetric m WHERE m.project.id = :projectId " +
           "AND m.metricName = :metricName AND m.recordedAt BETWEEN :start AND :end")
    Double getAverageMetricValue(@Param("projectId") String projectId,
                                 @Param("metricName") String metricName,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);
    
    /**
     * Get max metric value for a project
     */
    @Query("SELECT MAX(m.value) FROM ResourceMetric m WHERE m.project.id = :projectId " +
           "AND m.metricName = :metricName AND m.recordedAt BETWEEN :start AND :end")
    Double getMaxMetricValue(@Param("projectId") String projectId,
                            @Param("metricName") String metricName,
                            @Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end);
    
    /**
     * Get latest metrics for a project
     */
    @Query(value = "SELECT DISTINCT ON (metric_name) * FROM resource_metrics " +
                   "WHERE project_id = :projectId " +
                   "ORDER BY metric_name, recorded_at DESC", 
           nativeQuery = true)
    List<ResourceMetric> getLatestMetricsForProject(@Param("projectId") String projectId);
    
    /**
     * Count metrics for a project
     */
    Long countByProjectIdAndRecordedAtBetween(String projectId, LocalDateTime start, LocalDateTime end);
}