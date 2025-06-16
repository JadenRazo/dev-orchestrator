-- Create container instances table with proper constraints and indexes
CREATE TABLE container_instances (
    id VARCHAR(64) PRIMARY KEY,
    environment_id VARCHAR(36) NOT NULL REFERENCES environments(id) ON DELETE CASCADE,
    container_name VARCHAR(255) NOT NULL,
    docker_container_id VARCHAR(64),
    service_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'STARTING',
    host_port INTEGER,
    container_port INTEGER,
    health_check_url VARCHAR(500),
    last_health_check TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_containers_environment ON container_instances(environment_id);
CREATE INDEX idx_containers_status ON container_instances(status);
CREATE INDEX idx_containers_docker_id ON container_instances(docker_container_id);
CREATE INDEX idx_containers_service_name ON container_instances(service_name);
CREATE INDEX idx_containers_last_health_check ON container_instances(last_health_check);

-- Check constraints
ALTER TABLE container_instances ADD CONSTRAINT chk_containers_status 
    CHECK (status IN ('STARTING', 'RUNNING', 'STOPPED', 'ERROR', 'DESTROYED'));
    
ALTER TABLE container_instances ADD CONSTRAINT chk_containers_host_port 
    CHECK (host_port IS NULL OR (host_port >= 8000 AND host_port <= 9999));
    
ALTER TABLE container_instances ADD CONSTRAINT chk_containers_container_port 
    CHECK (container_port IS NULL OR (container_port >= 1 AND container_port <= 65535));