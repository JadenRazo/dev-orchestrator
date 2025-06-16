-- Create environment templates table with proper constraints and indexes
CREATE TABLE environment_templates (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    docker_compose_content TEXT NOT NULL,
    environment_variables TEXT,
    memory_limit_mb INTEGER NOT NULL DEFAULT 512,
    cpu_limit DECIMAL(3,2) NOT NULL DEFAULT 1.0,
    is_public BOOLEAN NOT NULL DEFAULT true,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Template exposed ports junction table
CREATE TABLE template_ports (
    template_id VARCHAR(100) REFERENCES environment_templates(id) ON DELETE CASCADE,
    port INTEGER NOT NULL,
    PRIMARY KEY (template_id, port)
);

-- Indexes for performance
CREATE INDEX idx_templates_public ON environment_templates(is_public);
CREATE INDEX idx_templates_created_by ON environment_templates(created_by);
CREATE INDEX idx_templates_name ON environment_templates(name);

-- Check constraints
ALTER TABLE environment_templates ADD CONSTRAINT chk_templates_memory_limit 
    CHECK (memory_limit_mb >= 128 AND memory_limit_mb <= 8192);
    
ALTER TABLE environment_templates ADD CONSTRAINT chk_templates_cpu_limit 
    CHECK (cpu_limit > 0 AND cpu_limit <= 8.0);
    
ALTER TABLE template_ports ADD CONSTRAINT chk_template_ports_range 
    CHECK (port >= 1024 AND port <= 65535);