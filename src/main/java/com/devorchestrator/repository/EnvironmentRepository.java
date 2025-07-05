package com.devorchestrator.repository;

import com.devorchestrator.entity.Environment;
import com.devorchestrator.entity.EnvironmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, String> {

    List<Environment> findByOwnerId(Long ownerId);

    Page<Environment> findByOwnerId(Long ownerId, Pageable pageable);

    List<Environment> findByOwnerIdAndStatus(Long ownerId, EnvironmentStatus status);

    Page<Environment> findByOwnerIdAndStatus(Long ownerId, EnvironmentStatus status, Pageable pageable);

    Optional<Environment> findByIdAndOwnerId(String id, Long ownerId);

    List<Environment> findByStatus(EnvironmentStatus status);

    @Query("SELECT e FROM Environment e WHERE e.status = :status AND e.lastAccessedAt < :cutoff")
    List<Environment> findByStatusAndLastAccessedBefore(@Param("status") EnvironmentStatus status, 
                                                       @Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT e FROM Environment e WHERE e.createdAt < :cutoff AND e.status != 'DESTROYED'")
    List<Environment> findStaleEnvironments(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT e FROM Environment e WHERE e.status = 'RUNNING' AND " +
           "e.lastAccessedAt < :cutoff")
    List<Environment> findIdleRunningEnvironments(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT COUNT(e) FROM Environment e WHERE e.owner.id = :ownerId AND e.status != 'DESTROYED'")
    int countActiveEnvironmentsByOwnerId(@Param("ownerId") Long ownerId);

    @Query("SELECT COUNT(e) FROM Environment e WHERE e.status = :status")
    long countByStatus(@Param("status") EnvironmentStatus status);

    @Query("SELECT COUNT(e) FROM Environment e WHERE e.status IN ('CREATING', 'RUNNING', 'STOPPED')")
    long countActiveEnvironments();

    @Modifying
    @Query("UPDATE Environment e SET e.status = :status WHERE e.id = :id")
    int updateEnvironmentStatus(@Param("id") String id, @Param("status") EnvironmentStatus status);

    @Modifying
    @Query("UPDATE Environment e SET e.lastAccessedAt = CURRENT_TIMESTAMP WHERE e.id = :id")
    int updateLastAccessedTime(@Param("id") String id);

    @Query("SELECT e FROM Environment e WHERE e.template.id = :templateId")
    List<Environment> findByTemplateId(@Param("templateId") String templateId);

    @Query("SELECT e FROM Environment e WHERE e.template.id = :templateId")
    Page<Environment> findByTemplateId(@Param("templateId") String templateId, Pageable pageable);

    @Query("SELECT e FROM Environment e WHERE " +
           "e.owner.id = :ownerId AND " +
           "(LOWER(e.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(e.template.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Environment> searchByOwnerIdAndName(@Param("ownerId") Long ownerId, 
                                           @Param("search") String search, 
                                           Pageable pageable);

    @Query("SELECT AVG(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - e.createdAt))) FROM Environment e WHERE e.status = 'RUNNING'")
    Double getAverageEnvironmentUptime();

    @Query("SELECT COUNT(e) FROM Environment e WHERE e.createdAt >= CURRENT_DATE")
    long countEnvironmentsCreatedToday();
    
    Optional<Environment> findByProjectIdAndStatus(String projectId, EnvironmentStatus status);
    
    List<Environment> findByProjectIdAndStatusIn(String projectId, List<EnvironmentStatus> statuses);
}