package com.devorchestrator.repository;

import com.devorchestrator.entity.UsageReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsageReportRepository extends JpaRepository<UsageReport, String> {
    
    /**
     * Find reports for a project within a time range
     */
    Page<UsageReport> findByProjectIdAndPeriodStartBetweenOrderByPeriodStartDesc(
        String projectId, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);
    
    /**
     * Find reports by type for a project
     */
    List<UsageReport> findByProjectIdAndReportTypeOrderByPeriodStartDesc(
        String projectId, UsageReport.ReportType reportType);
    
    /**
     * Find the latest report for a project
     */
    Optional<UsageReport> findTopByProjectIdOrderByPeriodEndDesc(String projectId);
    
    /**
     * Find reports with high resource usage
     */
    @Query("SELECT u FROM UsageReport u WHERE u.project.id = :projectId " +
           "AND (u.peakCpuPercent > :cpuThreshold OR u.peakMemoryMb > :memoryThreshold) " +
           "ORDER BY u.periodStart DESC")
    List<UsageReport> findHighUsageReports(@Param("projectId") String projectId,
                                          @Param("cpuThreshold") Double cpuThreshold,
                                          @Param("memoryThreshold") Integer memoryThreshold);
    
    /**
     * Get total cost for a project within a period
     */
    @Query("SELECT SUM(u.estimatedCost) FROM UsageReport u " +
           "WHERE u.project.id = :projectId " +
           "AND u.periodStart >= :startDate AND u.periodEnd <= :endDate")
    Double getTotalCostForPeriod(@Param("projectId") String projectId,
                                @Param("startDate") LocalDateTime startDate,
                                @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get average resource usage for a project
     */
    @Query("SELECT AVG(u.totalCpuHours) as avgCpu, " +
           "AVG(u.totalMemoryGbHours) as avgMemory, " +
           "AVG(u.totalNetworkGb) as avgNetwork " +
           "FROM UsageReport u WHERE u.project.id = :projectId " +
           "AND u.reportType = :reportType")
    Object[] getAverageResourceUsage(@Param("projectId") String projectId,
                                    @Param("reportType") UsageReport.ReportType reportType);
    
    /**
     * Check if report exists for a period
     */
    boolean existsByProjectIdAndPeriodStartAndPeriodEndAndReportType(
        String projectId, LocalDateTime periodStart, LocalDateTime periodEnd, 
        UsageReport.ReportType reportType);
    
    /**
     * Delete old reports
     */
    void deleteByGeneratedAtBefore(LocalDateTime cutoff);
}