package com.devorchestrator.repository;

import com.devorchestrator.entity.ScalingPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScalingPolicyRepository extends JpaRepository<ScalingPolicy, String> {
    
    /**
     * Find active policies for a project
     */
    List<ScalingPolicy> findByProjectIdAndIsActive(String projectId, Boolean isActive);
    
    /**
     * Find policies for an environment
     */
    List<ScalingPolicy> findByEnvironmentId(String environmentId);
    
    /**
     * Find policies by type
     */
    List<ScalingPolicy> findByPolicyTypeAndIsActive(
        ScalingPolicy.PolicyType policyType, Boolean isActive);
    
    /**
     * Find policies that need evaluation
     */
    @Query("SELECT s FROM ScalingPolicy s WHERE s.isActive = true " +
           "AND (s.lastScaleAt IS NULL OR s.lastScaleAt < :cooldownCutoff)")
    List<ScalingPolicy> findPoliciesReadyForEvaluation(
        @Param("cooldownCutoff") LocalDateTime cooldownCutoff);
    
    /**
     * Get scaling activity summary
     */
    @Query("SELECT s.project.id, COUNT(s), AVG(s.currentInstances) " +
           "FROM ScalingPolicy s WHERE s.isActive = true " +
           "GROUP BY s.project.id")
    List<Object[]> getScalingActivitySummary();
    
    /**
     * Find policies with recent scaling events
     */
    List<ScalingPolicy> findByLastScaleAtAfterOrderByLastScaleAtDesc(
        LocalDateTime since);
    
    /**
     * Check if project has active scaling policies
     */
    boolean existsByProjectIdAndIsActive(String projectId, Boolean isActive);
}