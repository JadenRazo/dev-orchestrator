package com.devorchestrator.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "environment_templates")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnvironmentTemplate {

    @Id
    @Column(length = 100)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "docker_compose_content", columnDefinition = "TEXT", nullable = false)
    private String dockerComposeContent;

    @Column(name = "environment_variables", columnDefinition = "TEXT")
    private String environmentVariables;

    @ElementCollection
    @CollectionTable(name = "template_ports", joinColumns = @JoinColumn(name = "template_id"))
    @Column(name = "port")
    private Set<Integer> exposedPorts = new HashSet<>();

    @Column(name = "memory_limit_mb", nullable = false)
    private Integer memoryLimitMb = 512;

    @Column(name = "cpu_limit", nullable = false)
    private Double cpuLimit = 1.0;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "infrastructure_type", nullable = false)
    private InfrastructureProvider infrastructureType = InfrastructureProvider.DOCKER;

    @Column(name = "terraform_template", columnDefinition = "TEXT")
    private String terraformTemplate;

    @Column(name = "terraform_variables", columnDefinition = "TEXT")
    private String terraformVariables;

    @Column(name = "cloud_region", length = 50)
    private String cloudRegion;

    @Column(name = "created_by")
    private Long createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public EnvironmentTemplate(String id, String name, String dockerComposeContent) {
        this.id = id;
        this.name = name;
        this.dockerComposeContent = dockerComposeContent;
        this.exposedPorts = new HashSet<>();
        this.memoryLimitMb = 512;
        this.cpuLimit = 1.0;
        this.isPublic = true;
        this.infrastructureType = InfrastructureProvider.DOCKER;
    }

    public EnvironmentTemplate(String id, String name, String description, String dockerComposeContent, Long createdBy) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.dockerComposeContent = dockerComposeContent;
        this.createdBy = createdBy;
        this.exposedPorts = new HashSet<>();
        this.memoryLimitMb = 512;
        this.cpuLimit = 1.0;
        this.isPublic = true;
        this.infrastructureType = InfrastructureProvider.DOCKER;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setDockerComposeContent(String dockerComposeContent) {
        this.dockerComposeContent = dockerComposeContent;
    }

    public void setEnvironmentVariables(String environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    public void setExposedPorts(Set<Integer> exposedPorts) {
        this.exposedPorts = exposedPorts;
    }

    public void setMemoryLimitMb(Integer memoryLimitMb) {
        this.memoryLimitMb = memoryLimitMb;
    }

    public void setCpuLimit(Double cpuLimit) {
        this.cpuLimit = cpuLimit;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public void setInfrastructureType(InfrastructureProvider infrastructureType) {
        this.infrastructureType = infrastructureType;
    }

    public void setTerraformTemplate(String terraformTemplate) {
        this.terraformTemplate = terraformTemplate;
    }

    public void setTerraformVariables(String terraformVariables) {
        this.terraformVariables = terraformVariables;
    }

    public void setCloudRegion(String cloudRegion) {
        this.cloudRegion = cloudRegion;
    }

    public boolean isCloudBased() {
        return infrastructureType != InfrastructureProvider.DOCKER;
    }

    public boolean requiresTerraform() {
        return infrastructureType != InfrastructureProvider.DOCKER;
    }

    public boolean isCreatedByUser(Long userId) {
        return Objects.equals(createdBy, userId);
    }

    public boolean canBeAccessedByUser(Long userId) {
        return isPublic || isCreatedByUser(userId);
    }

    public void addExposedPort(Integer port) {
        this.exposedPorts.add(port);
    }

    public void removeExposedPort(Integer port) {
        this.exposedPorts.remove(port);
    }

    public Integer getMemoryLimit() {
        return memoryLimitMb;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnvironmentTemplate template = (EnvironmentTemplate) o;
        return Objects.equals(id, template.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EnvironmentTemplate{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", memoryLimitMb=" + memoryLimitMb +
                ", cpuLimit=" + cpuLimit +
                ", isPublic=" + isPublic +
                '}';
    }
}