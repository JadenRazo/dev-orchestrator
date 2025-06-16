-- Create environments table with proper constraints and indexes
CREATE TABLE environments (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    template_id VARCHAR(100) NOT NULL REFERENCES environment_templates(id),
    owner_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'CREATING',
    docker_compose_override TEXT,
    last_accessed_at TIMESTAMP,
    auto_stop_after_hours INTEGER NOT NULL DEFAULT 8,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- Environment port mappings junction table
CREATE TABLE environment_port_mappings (
    environment_id VARCHAR(36) REFERENCES environments(id) ON DELETE CASCADE,
    container_port INTEGER NOT NULL,
    host_port INTEGER NOT NULL,
    PRIMARY KEY (environment_id, container_port)
);

-- Indexes for performance
CREATE INDEX idx_environments_owner ON environments(owner_id);
CREATE INDEX idx_environments_status ON environments(status);
CREATE INDEX idx_environments_template ON environments(template_id);
CREATE INDEX idx_environments_last_accessed ON environments(last_accessed_at);
CREATE INDEX idx_environments_created_at ON environments(created_at);

-- Check constraints
ALTER TABLE environments ADD CONSTRAINT chk_environments_status 
    CHECK (status IN ('CREATING', 'RUNNING', 'STOPPED', 'ERROR', 'DESTROYED'));
    
ALTER TABLE environments ADD CONSTRAINT chk_environments_auto_stop 
    CHECK (auto_stop_after_hours > 0 AND auto_stop_after_hours <= 168);
    
ALTER TABLE environment_port_mappings ADD CONSTRAINT chk_env_port_mappings_container_port 
    CHECK (container_port >= 1 AND container_port <= 65535);
    
ALTER TABLE environment_port_mappings ADD CONSTRAINT chk_env_port_mappings_host_port 
    CHECK (host_port >= 8000 AND host_port <= 9999);