package com.devorchestrator.repository;

import com.devorchestrator.entity.CloudCostTracking;
import com.devorchestrator.entity.InfrastructureProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CloudCostTrackingRepository extends JpaRepository<CloudCostTracking, Long> {
    
    /**
     * Find costs for a project within a time range
     */
    Page<CloudCostTracking> findByProjectIdAndPeriodStartBetweenOrderByPeriodStartDesc(
        String projectId, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);
    
    /**
     * Find costs by provider for a project
     */
    List<CloudCostTracking> findByProjectIdAndProviderOrderByPeriodStartDesc(
        String projectId, InfrastructureProvider provider);
    
    /**
     * Find costs by service type
     */
    List<CloudCostTracking> findByProjectIdAndServiceTypeOrderByPeriodStartDesc(
        String projectId, CloudCostTracking.ServiceType serviceType);
    
    /**
     * Get total cost for a project and period
     */
    @Query("SELECT SUM(c.costAmount) FROM CloudCostTracking c " +
           "WHERE c.project.id = :projectId " +
           "AND c.periodStart >= :startDate AND c.periodEnd <= :endDate")
    BigDecimal getTotalCostForPeriod(@Param("projectId") String projectId,
                                    @Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get cost breakdown by service type
     */
    @Query("SELECT c.serviceType, SUM(c.costAmount) FROM CloudCostTracking c " +
           "WHERE c.project.id = :projectId " +
           "AND c.periodStart >= :startDate AND c.periodEnd <= :endDate " +
           "GROUP BY c.serviceType ORDER BY SUM(c.costAmount) DESC")
    List<Object[]> getCostBreakdownByServiceType(@Param("projectId") String projectId,
                                                @Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get cost breakdown by provider
     */
    @Query("SELECT c.provider, SUM(c.costAmount) FROM CloudCostTracking c " +
           "WHERE c.project.id = :projectId " +
           "AND c.periodStart >= :startDate AND c.periodEnd <= :endDate " +
           "GROUP BY c.provider ORDER BY SUM(c.costAmount) DESC")
    List<Object[]> getCostBreakdownByProvider(@Param("projectId") String projectId,
                                             @Param("startDate") LocalDateTime startDate,
                                             @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find high-cost resources
     */
    @Query("SELECT c FROM CloudCostTracking c " +
           "WHERE c.project.id = :projectId AND c.costAmount > :threshold " +
           "ORDER BY c.costAmount DESC")
    List<CloudCostTracking> findHighCostResources(@Param("projectId") String projectId,
                                                 @Param("threshold") BigDecimal threshold);
    
    /**
     * Get monthly cost trend
     */
    @Query("SELECT YEAR(c.periodStart), MONTH(c.periodStart), SUM(c.costAmount) " +
           "FROM CloudCostTracking c WHERE c.project.id = :projectId " +
           "GROUP BY YEAR(c.periodStart), MONTH(c.periodStart) " +
           "ORDER BY YEAR(c.periodStart) DESC, MONTH(c.periodStart) DESC")
    List<Object[]> getMonthlyCostTrend(@Param("projectId") String projectId);
    
    /**
     * Delete old cost records
     */
    void deleteByRecordedAtBefore(LocalDateTime cutoff);
}