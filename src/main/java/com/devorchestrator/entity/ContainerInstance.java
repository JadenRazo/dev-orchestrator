package com.devorchestrator.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "container_instances")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContainerInstance {

    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false)
    private Environment environment;

    @Column(name = "container_name", nullable = false)
    private String containerName;

    @Column(name = "docker_container_id", length = 64)
    private String dockerContainerId;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContainerStatus status = ContainerStatus.STARTING;

    @Column(name = "host_port")
    private Integer hostPort;

    @Column(name = "container_port")
    private Integer containerPort;

    @Column(name = "health_check_url", length = 500)
    private String healthCheckUrl;

    @Column(name = "last_health_check")
    private LocalDateTime lastHealthCheck;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ContainerInstance(String id, Environment environment, String serviceName, String containerName) {
        this.id = id;
        this.environment = environment;
        this.serviceName = serviceName;
        this.containerName = containerName;
        this.status = ContainerStatus.STARTING;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public void setDockerContainerId(String dockerContainerId) {
        this.dockerContainerId = dockerContainerId;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setStatus(ContainerStatus status) {
        this.status = status;
    }

    public void setHostPort(Integer hostPort) {
        this.hostPort = hostPort;
    }

    public void setContainerPort(Integer containerPort) {
        this.containerPort = containerPort;
    }

    public void setHealthCheckUrl(String healthCheckUrl) {
        this.healthCheckUrl = healthCheckUrl;
    }

    public void setLastHealthCheck(LocalDateTime lastHealthCheck) {
        this.lastHealthCheck = lastHealthCheck;
    }

    public boolean isRunning() {
        return status == ContainerStatus.RUNNING;
    }

    public boolean isStopped() {
        return status == ContainerStatus.STOPPED;
    }

    public boolean hasError() {
        return status == ContainerStatus.ERROR;
    }

    public boolean hasPortMapping() {
        return hostPort != null && containerPort != null;
    }

    public boolean hasHealthCheck() {
        return healthCheckUrl != null && !healthCheckUrl.trim().isEmpty();
    }

    public void updateHealthCheck() {
        this.lastHealthCheck = LocalDateTime.now();
    }

    public boolean isHealthCheckOverdue() {
        if (lastHealthCheck == null) {
            return true;
        }
        return lastHealthCheck.isBefore(LocalDateTime.now().minusMinutes(5));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerInstance that = (ContainerInstance) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ContainerInstance{" +
                "id='" + id + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", containerName='" + containerName + '\'' +
                ", status=" + status +
                ", hostPort=" + hostPort +
                ", containerPort=" + containerPort +
                '}';
    }
}