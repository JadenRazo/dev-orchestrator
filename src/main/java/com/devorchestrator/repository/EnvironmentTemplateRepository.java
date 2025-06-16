package com.devorchestrator.repository;

import com.devorchestrator.entity.EnvironmentTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnvironmentTemplateRepository extends JpaRepository<EnvironmentTemplate, String> {

    List<EnvironmentTemplate> findByIsPublicTrue();

    Page<EnvironmentTemplate> findByIsPublicTrue(Pageable pageable);

    List<EnvironmentTemplate> findByCreatedBy(Long createdBy);

    Page<EnvironmentTemplate> findByCreatedBy(Long createdBy, Pageable pageable);

    @Query("SELECT t FROM EnvironmentTemplate t WHERE t.isPublic = true OR t.createdBy = :userId")
    List<EnvironmentTemplate> findAccessibleTemplates(@Param("userId") Long userId);

    @Query("SELECT t FROM EnvironmentTemplate t WHERE t.isPublic = true OR t.createdBy = :userId")
    Page<EnvironmentTemplate> findAccessibleTemplates(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT t FROM EnvironmentTemplate t WHERE " +
           "(t.isPublic = true OR t.createdBy = :userId) AND " +
           "(LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<EnvironmentTemplate> searchAccessibleTemplates(@Param("userId") Long userId, 
                                                       @Param("search") String search, 
                                                       Pageable pageable);

    @Query("SELECT COUNT(e) FROM Environment e WHERE e.template.id = :templateId")
    long countEnvironmentsByTemplateId(@Param("templateId") String templateId);

    @Query("SELECT t FROM EnvironmentTemplate t WHERE t.memoryLimitMb <= :maxMemory AND t.cpuLimit <= :maxCpu")
    List<EnvironmentTemplate> findByResourceLimits(@Param("maxMemory") Integer maxMemory, 
                                                  @Param("maxCpu") Double maxCpu);

    Optional<EnvironmentTemplate> findByIdAndIsPublicTrueOrCreatedBy(String id, Long createdBy);
}