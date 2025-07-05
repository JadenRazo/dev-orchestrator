package com.devorchestrator.repository;

import com.devorchestrator.entity.CloudMigrationPlan;
import com.devorchestrator.entity.InfrastructureProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CloudMigrationPlanRepository extends JpaRepository<CloudMigrationPlan, String> {
    
    /**
     * Find migration plans for a project
     */
    List<CloudMigrationPlan> findByProjectIdOrderByCreatedAtDesc(String projectId);
    
    /**
     * Find active migration plan for a project
     */
    Optional<CloudMigrationPlan> findByProjectIdAndStatus(
        String projectId, CloudMigrationPlan.MigrationStatus status);
    
    /**
     * Find migration plans by target provider
     */
    List<CloudMigrationPlan> findByTargetProvider(InfrastructureProvider provider);
    
    /**
     * Find in-progress migrations
     */
    @Query("SELECT m FROM CloudMigrationPlan m WHERE m.status = 'IN_PROGRESS'")
    List<CloudMigrationPlan> findInProgressMigrations();
    
    /**
     * Count completed migrations by provider
     */
    @Query("SELECT m.targetProvider, COUNT(m) FROM CloudMigrationPlan m " +
           "WHERE m.status = 'COMPLETED' GROUP BY m.targetProvider")
    List<Object[]> countCompletedMigrationsByProvider();
    
    /**
     * Get average migration cost by provider
     */
    @Query("SELECT m.targetProvider, AVG(m.estimatedMonthlyCost) FROM CloudMigrationPlan m " +
           "WHERE m.estimatedMonthlyCost IS NOT NULL GROUP BY m.targetProvider")
    List<Object[]> getAverageMigrationCostByProvider();
    
    /**
     * Check if project has active migration
     */
    boolean existsByProjectIdAndStatusIn(String projectId, 
        List<CloudMigrationPlan.MigrationStatus> statuses);
}