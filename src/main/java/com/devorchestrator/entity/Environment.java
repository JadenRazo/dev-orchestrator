package com.devorchestrator.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "environments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Environment {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private EnvironmentTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnvironmentStatus status = EnvironmentStatus.CREATING;

    @Column(name = "docker_compose_override", columnDefinition = "TEXT")
    private String dockerComposeOverride;
    
    @Column(name = "project_id", length = 36)
    private String projectId;

    @ElementCollection
    @CollectionTable(name = "environment_port_mappings", joinColumns = @JoinColumn(name = "environment_id"))
    @MapKeyColumn(name = "container_port")
    @Column(name = "host_port")
    private Map<Integer, Integer> portMappings = new HashMap<>();

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @Column(name = "auto_stop_after_hours", nullable = false)
    private Integer autoStopAfterHours = 8;

    @Enumerated(EnumType.STRING)
    @Column(name = "infrastructure_provider", nullable = false)
    private InfrastructureProvider infrastructureProvider = InfrastructureProvider.DOCKER;

    @Column(name = "terraform_state_id", length = 100)
    private String terraformStateId;

    @ElementCollection
    @CollectionTable(name = "environment_cloud_resources", joinColumns = @JoinColumn(name = "environment_id"))
    @MapKeyColumn(name = "resource_type")
    @Column(name = "resource_id")
    private Map<String, String> cloudResourceIds = new HashMap<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "environment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ContainerInstance> containers = new ArrayList<>();

    public Environment(String name, EnvironmentTemplate template, User owner) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.template = template;
        this.owner = owner;
        this.status = EnvironmentStatus.CREATING;
        this.portMappings = new HashMap<>();
        this.autoStopAfterHours = 8;
        this.containers = new ArrayList<>();
        this.lastAccessedAt = LocalDateTime.now();
        this.infrastructureProvider = InfrastructureProvider.DOCKER;
        this.cloudResourceIds = new HashMap<>();
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTemplate(EnvironmentTemplate template) {
        this.template = template;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public void setStatus(EnvironmentStatus status) {
        this.status = status;
    }

    public void setDockerComposeOverride(String dockerComposeOverride) {
        this.dockerComposeOverride = dockerComposeOverride;
    }

    public void setPortMappings(Map<Integer, Integer> portMappings) {
        this.portMappings = portMappings;
    }

    public void setLastAccessedAt(LocalDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public void setAutoStopAfterHours(Integer autoStopAfterHours) {
        this.autoStopAfterHours = autoStopAfterHours;
    }

    public void setContainers(List<ContainerInstance> containers) {
        this.containers = containers;
    }

    public void setInfrastructureProvider(InfrastructureProvider infrastructureProvider) {
        this.infrastructureProvider = infrastructureProvider;
    }

    public void setTerraformStateId(String terraformStateId) {
        this.terraformStateId = terraformStateId;
    }

    public void setCloudResourceIds(Map<String, String> cloudResourceIds) {
        this.cloudResourceIds = cloudResourceIds;
    }

    public void addCloudResource(String resourceType, String resourceId) {
        this.cloudResourceIds.put(resourceType, resourceId);
    }

    public void removeCloudResource(String resourceType) {
        this.cloudResourceIds.remove(resourceType);
    }
    
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public void addPortMapping(Integer containerPort, Integer hostPort) {
        this.portMappings.put(containerPort, hostPort);
    }

    public void removePortMapping(Integer containerPort) {
        this.portMappings.remove(containerPort);
    }

    public void updateLastAccessed() {
        this.lastAccessedAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(Long userId) {
        return Objects.equals(owner.getId(), userId);
    }

    public boolean isRunning() {
        return status == EnvironmentStatus.RUNNING;
    }

    public boolean isStopped() {
        return status == EnvironmentStatus.STOPPED;
    }

    public boolean canBeStarted() {
        return status == EnvironmentStatus.STOPPED || status == EnvironmentStatus.ERROR;
    }

    public boolean canBeStopped() {
        return status == EnvironmentStatus.RUNNING || status == EnvironmentStatus.CREATING;
    }

    public boolean isIdle() {
        if (lastAccessedAt == null) {
            return false;
        }
        return lastAccessedAt.isBefore(LocalDateTime.now().minusHours(autoStopAfterHours));
    }

    public boolean isStale() {
        return createdAt.isBefore(LocalDateTime.now().minusHours(24));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Environment that = (Environment) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Environment{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", owner=" + (owner != null ? owner.getUsername() : null) +
                ", template=" + (template != null ? template.getId() : null) +
                '}';
    }
}