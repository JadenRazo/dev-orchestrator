package com.devorchestrator.repository;

import com.devorchestrator.entity.ContainerInstance;
import com.devorchestrator.entity.ContainerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContainerInstanceRepository extends JpaRepository<ContainerInstance, String> {

    List<ContainerInstance> findByEnvironmentId(String environmentId);

    List<ContainerInstance> findByEnvironmentIdAndStatus(String environmentId, ContainerStatus status);

    Optional<ContainerInstance> findByDockerContainerId(String dockerContainerId);

    List<ContainerInstance> findByDockerContainerIdIsNotNull();

    List<ContainerInstance> findByStatus(ContainerStatus status);

    @Query("SELECT c FROM ContainerInstance c WHERE c.environment.id = :environmentId AND c.serviceName = :serviceName")
    Optional<ContainerInstance> findByEnvironmentIdAndServiceName(@Param("environmentId") String environmentId, 
                                                                 @Param("serviceName") String serviceName);

    @Query("SELECT c FROM ContainerInstance c WHERE c.hostPort = :hostPort")
    Optional<ContainerInstance> findByHostPort(@Param("hostPort") Integer hostPort);

    @Query("SELECT c FROM ContainerInstance c WHERE c.lastHealthCheck IS NULL OR c.lastHealthCheck < :cutoff")
    List<ContainerInstance> findContainersNeedingHealthCheck(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT c FROM ContainerInstance c WHERE c.status = 'RUNNING' AND " +
           "(c.lastHealthCheck IS NULL OR c.lastHealthCheck < :cutoff)")
    List<ContainerInstance> findRunningContainersNeedingHealthCheck(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("UPDATE ContainerInstance c SET c.status = :status WHERE c.id = :id")
    int updateContainerStatus(@Param("id") String id, @Param("status") ContainerStatus status);

    @Modifying
    @Query("UPDATE ContainerInstance c SET c.dockerContainerId = :dockerContainerId WHERE c.id = :id")
    int updateDockerContainerId(@Param("id") String id, @Param("dockerContainerId") String dockerContainerId);

    @Modifying
    @Query("UPDATE ContainerInstance c SET c.lastHealthCheck = CURRENT_TIMESTAMP WHERE c.id = :id")
    int updateLastHealthCheck(@Param("id") String id);

    @Query("SELECT COUNT(c) FROM ContainerInstance c WHERE c.status = :status")
    long countByStatus(@Param("status") ContainerStatus status);

    @Query("SELECT COUNT(c) FROM ContainerInstance c WHERE c.environment.id = :environmentId AND c.status = 'RUNNING'")
    int countRunningContainersByEnvironmentId(@Param("environmentId") String environmentId);

    @Query("SELECT COUNT(c) FROM ContainerInstance c WHERE c.status IN ('STARTING', 'RUNNING')")
    long countActiveContainers();

    @Query("SELECT c FROM ContainerInstance c WHERE c.environment.owner.id = :ownerId")
    List<ContainerInstance> findByEnvironmentOwnerId(@Param("ownerId") Long ownerId);

    @Query("SELECT DISTINCT c.hostPort FROM ContainerInstance c WHERE c.hostPort IS NOT NULL")
    List<Integer> findAllAllocatedPorts();

    @Query("SELECT c FROM ContainerInstance c WHERE c.environment.template.id = :templateId")
    List<ContainerInstance> findByTemplateId(@Param("templateId") String templateId);

    @Query("SELECT c FROM ContainerInstance c WHERE c.lastHealthCheck < :cutoff AND c.status = :status")
    List<ContainerInstance> findByLastHealthCheckBeforeAndStatus(@Param("cutoff") LocalDateTime cutoff, 
                                                                @Param("status") ContainerStatus status);

    @Query("SELECT c FROM ContainerInstance c WHERE c.environment.id IN :environmentIds")
    List<ContainerInstance> findByEnvironmentIdIn(@Param("environmentIds") List<String> environmentIds);
}