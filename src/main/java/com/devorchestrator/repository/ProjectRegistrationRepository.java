package com.devorchestrator.repository;

import com.devorchestrator.entity.ProjectRegistration;
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
public interface ProjectRegistrationRepository extends JpaRepository<ProjectRegistration, String> {
    
    /**
     * Find project by path and user
     */
    Optional<ProjectRegistration> findByPathAndUserId(String path, Long userId);
    
    /**
     * Find all projects for a user
     */
    Page<ProjectRegistration> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);
    
    /**
     * Find projects by status for a user
     */
    List<ProjectRegistration> findByUserIdAndStatus(Long userId, ProjectRegistration.ProjectStatus status);
    
    /**
     * Find all projects with a specific status
     */
    List<ProjectRegistration> findByStatus(ProjectRegistration.ProjectStatus status);
    
    /**
     * Find projects that need auto-start
     */
    List<ProjectRegistration> findByAutoStartTrueAndStatus(ProjectRegistration.ProjectStatus status);
    
    /**
     * Find projects with monitoring enabled
     */
    List<ProjectRegistration> findByMonitoringEnabledTrueAndStatus(ProjectRegistration.ProjectStatus status);
    
    /**
     * Count active projects for a user
     */
    Long countByUserIdAndStatus(Long userId, ProjectRegistration.ProjectStatus status);
    
    /**
     * Find projects not analyzed recently
     */
    @Query("SELECT p FROM ProjectRegistration p WHERE p.lastAnalyzedAt IS NULL " +
           "OR p.lastAnalyzedAt < :cutoff")
    List<ProjectRegistration> findProjectsNeedingAnalysis(@Param("cutoff") LocalDateTime cutoff);
    
    /**
     * Find projects by health status
     */
    List<ProjectRegistration> findByHealthStatus(ProjectRegistration.HealthStatus healthStatus);
    
    /**
     * Search projects by name or path
     */
    @Query("SELECT p FROM ProjectRegistration p WHERE p.user.id = :userId " +
           "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(p.path) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<ProjectRegistration> searchProjects(@Param("userId") Long userId, 
                                           @Param("query") String query, 
                                           Pageable pageable);
}