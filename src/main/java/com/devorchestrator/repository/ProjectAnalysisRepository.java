package com.devorchestrator.repository;

import com.devorchestrator.entity.ProjectAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectAnalysisRepository extends JpaRepository<ProjectAnalysisEntity, String> {
    
    /**
     * Find analyses for a project
     */
    List<ProjectAnalysisEntity> findByProjectIdOrderByAnalyzedAtDesc(String projectId);
    
    /**
     * Find latest analysis for a project
     */
    Optional<ProjectAnalysisEntity> findTopByProjectIdOrderByAnalyzedAtDesc(String projectId);
    
    /**
     * Find analyses by status
     */
    List<ProjectAnalysisEntity> findByStatus(ProjectAnalysisEntity.AnalysisStatus status);
    
    /**
     * Find analyses with high confidence
     */
    @Query("SELECT a FROM ProjectAnalysisEntity a WHERE a.overallConfidence >= :threshold")
    List<ProjectAnalysisEntity> findHighConfidenceAnalyses(@Param("threshold") Double threshold);
    
    /**
     * Count analyses by project and time range
     */
    Long countByProjectIdAndAnalyzedAtBetween(String projectId, 
                                             LocalDateTime start, 
                                             LocalDateTime end);
    
    /**
     * Delete old analyses
     */
    void deleteByAnalyzedAtBefore(LocalDateTime cutoff);
}