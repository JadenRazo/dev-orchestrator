-- Development Environment Orchestrator Database Schema
-- This file contains the complete database structure

-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =====================================================
-- USERS AND AUTHENTICATION
-- =====================================================

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,
    environment_limit INTEGER DEFAULT 5,
    CONSTRAINT role_check CHECK (role IN ('USER', 'ADMIN'))
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_is_active ON users(is_active);

-- =====================================================
-- ENVIRONMENT TEMPLATES
-- =====================================================

CREATE TABLE IF NOT EXISTS environment_templates (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    docker_compose_content TEXT,
    terraform_template TEXT,
    terraform_variables JSONB,
    infrastructure_type VARCHAR(50) DEFAULT 'DOCKER',
    cloud_region VARCHAR(50),
    min_memory_mb INTEGER NOT NULL DEFAULT 512,
    min_cpu_cores DECIMAL(3,1) NOT NULL DEFAULT 0.5,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT fk_template_creator FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT infrastructure_type_check CHECK (infrastructure_type IN ('DOCKER', 'AWS', 'AZURE', 'GCP', 'KUBERNETES', 'DIGITAL_OCEAN'))
);

CREATE INDEX idx_templates_category ON environment_templates(category);
CREATE INDEX idx_templates_is_active ON environment_templates(is_active);
CREATE INDEX idx_templates_created_by ON environment_templates(created_by);

CREATE TABLE IF NOT EXISTS template_ports (
    id BIGSERIAL PRIMARY KEY,
    template_id VARCHAR(50) NOT NULL,
    service_name VARCHAR(50) NOT NULL,
    internal_port INTEGER NOT NULL,
    description VARCHAR(255),
    CONSTRAINT fk_template FOREIGN KEY (template_id) REFERENCES environment_templates(id) ON DELETE CASCADE,
    CONSTRAINT port_range_check CHECK (internal_port > 0 AND internal_port <= 65535),
    UNIQUE(template_id, service_name, internal_port)
);

CREATE INDEX idx_template_ports_template_id ON template_ports(template_id);

-- =====================================================
-- ENVIRONMENTS
-- =====================================================

CREATE TABLE IF NOT EXISTS environments (
    id VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATING',
    owner_id BIGINT NOT NULL,
    template_id VARCHAR(50) NOT NULL,
    project_id BIGINT,
    infrastructure_provider VARCHAR(50) DEFAULT 'DOCKER',
    terraform_workspace_id VARCHAR(100),
    terraform_state_id BIGINT,
    cloud_resource_ids JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at TIMESTAMP,
    auto_stop_minutes INTEGER DEFAULT 240,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT fk_template_env FOREIGN KEY (template_id) REFERENCES environment_templates(id),
    CONSTRAINT status_check CHECK (status IN ('CREATING', 'STARTING', 'RUNNING', 'STOPPING', 'STOPPED', 'FAILED', 'DESTROYING')),
    CONSTRAINT infrastructure_provider_check CHECK (infrastructure_provider IN ('DOCKER', 'AWS', 'AZURE', 'GCP', 'KUBERNETES', 'DIGITAL_OCEAN'))
);

CREATE INDEX idx_environments_owner_id ON environments(owner_id);
CREATE INDEX idx_environments_status ON environments(status);
CREATE INDEX idx_environments_template_id ON environments(template_id);
CREATE INDEX idx_environments_last_accessed ON environments(last_accessed_at);
CREATE INDEX idx_environments_project_id ON environments(project_id);

CREATE TABLE IF NOT EXISTS environment_port_mappings (
    id BIGSERIAL PRIMARY KEY,
    environment_id VARCHAR(36) NOT NULL,
    service_name VARCHAR(50) NOT NULL,
    internal_port INTEGER NOT NULL,
    host_port INTEGER NOT NULL,
    CONSTRAINT fk_environment_port FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE,
    CONSTRAINT host_port_range CHECK (host_port >= 1024 AND host_port <= 65535),
    CONSTRAINT internal_port_range CHECK (internal_port > 0 AND internal_port <= 65535),
    UNIQUE(environment_id, service_name, internal_port),
    UNIQUE(host_port)
);

CREATE INDEX idx_port_mappings_env_id ON environment_port_mappings(environment_id);
CREATE INDEX idx_port_mappings_host_port ON environment_port_mappings(host_port);

CREATE TABLE IF NOT EXISTS environment_cloud_resources (
    id BIGSERIAL PRIMARY KEY,
    environment_id VARCHAR(36) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    cloud_resource_id VARCHAR(255) NOT NULL,
    cloud_provider VARCHAR(50) NOT NULL,
    region VARCHAR(50),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_env_cloud_resource FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE
);

CREATE INDEX idx_cloud_resources_env_id ON environment_cloud_resources(environment_id);
CREATE INDEX idx_cloud_resources_type ON environment_cloud_resources(resource_type);

-- =====================================================
-- CONTAINER INSTANCES
-- =====================================================

CREATE TABLE IF NOT EXISTS container_instances (
    id BIGSERIAL PRIMARY KEY,
    environment_id VARCHAR(36) NOT NULL,
    service_name VARCHAR(50) NOT NULL,
    docker_container_id VARCHAR(64),
    docker_image VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATING',
    health_status VARCHAR(20),
    cpu_limit DECIMAL(3,1),
    memory_limit_mb INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    stopped_at TIMESTAMP,
    CONSTRAINT fk_container_env FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE,
    CONSTRAINT container_status_check CHECK (status IN ('CREATING', 'RUNNING', 'STOPPED', 'FAILED', 'REMOVING')),
    CONSTRAINT health_status_check CHECK (health_status IN ('HEALTHY', 'UNHEALTHY', 'STARTING', 'NONE'))
);

CREATE INDEX idx_containers_env_id ON container_instances(environment_id);
CREATE INDEX idx_containers_status ON container_instances(status);
CREATE INDEX idx_containers_docker_id ON container_instances(docker_container_id);

-- =====================================================
-- PROJECT MANAGEMENT
-- =====================================================

CREATE TABLE IF NOT EXISTS project_registrations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    local_path VARCHAR(500),
    repository_url VARCHAR(500),
    repository_branch VARCHAR(100) DEFAULT 'main',
    owner_id BIGINT NOT NULL,
    environment_id VARCHAR(36),
    detected_languages JSONB,
    detected_frameworks JSONB,
    detected_databases JSONB,
    detected_services JSONB,
    dependency_files JSONB,
    recommended_template_id VARCHAR(50),
    resource_requirements JSONB,
    deployment_recommendations JSONB,
    analysis_status VARCHAR(50) DEFAULT 'PENDING',
    last_analysis_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT fk_project_env FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE SET NULL,
    CONSTRAINT fk_project_template FOREIGN KEY (recommended_template_id) REFERENCES environment_templates(id) ON DELETE SET NULL,
    CONSTRAINT analysis_status_check CHECK (analysis_status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_projects_owner_id ON project_registrations(owner_id);
CREATE INDEX idx_projects_env_id ON project_registrations(environment_id);
CREATE INDEX idx_projects_status ON project_registrations(analysis_status);

CREATE TABLE IF NOT EXISTS project_analyses (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    analysis_version INTEGER NOT NULL DEFAULT 1,
    detected_languages JSONB,
    detected_frameworks JSONB,
    detected_databases JSONB,
    detected_services JSONB,
    dependency_analysis JSONB,
    security_analysis JSONB,
    performance_recommendations JSONB,
    architecture_insights JSONB,
    estimated_container_count INTEGER,
    estimated_memory_mb INTEGER,
    estimated_cpu_cores DECIMAL(3,1),
    confidence_score DECIMAL(3,2),
    analysis_duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_analysis_project FOREIGN KEY (project_id) REFERENCES project_registrations(id) ON DELETE CASCADE
);

CREATE INDEX idx_analyses_project_id ON project_analyses(project_id);
CREATE INDEX idx_analyses_created_at ON project_analyses(created_at);

-- =====================================================
-- TERRAFORM AND INFRASTRUCTURE STATE
-- =====================================================

CREATE TABLE IF NOT EXISTS terraform_states (
    id BIGSERIAL PRIMARY KEY,
    environment_id VARCHAR(36) NOT NULL,
    state_data TEXT NOT NULL,
    state_version INTEGER NOT NULL DEFAULT 1,
    terraform_version VARCHAR(20),
    locked BOOLEAN DEFAULT false,
    locked_by VARCHAR(100),
    locked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_terraform_env FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE
);

CREATE INDEX idx_terraform_states_env_id ON terraform_states(environment_id);
CREATE INDEX idx_terraform_states_locked ON terraform_states(locked);

CREATE TABLE IF NOT EXISTS terraform_operations (
    id BIGSERIAL PRIMARY KEY,
    environment_id VARCHAR(36) NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    initiated_by BIGINT NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    output TEXT,
    error_message TEXT,
    terraform_plan TEXT,
    applied_changes JSONB,
    CONSTRAINT fk_terraform_op_env FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE,
    CONSTRAINT fk_terraform_op_user FOREIGN KEY (initiated_by) REFERENCES users(id),
    CONSTRAINT operation_type_check CHECK (operation_type IN ('PLAN', 'APPLY', 'DESTROY', 'REFRESH')),
    CONSTRAINT operation_status_check CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_terraform_ops_env_id ON terraform_operations(environment_id);
CREATE INDEX idx_terraform_ops_status ON terraform_operations(status);

-- =====================================================
-- MONITORING AND METRICS
-- =====================================================

CREATE TABLE IF NOT EXISTS resource_metrics (
    id BIGSERIAL PRIMARY KEY,
    environment_id VARCHAR(36),
    project_id BIGINT,
    container_id BIGINT,
    metric_type VARCHAR(50) NOT NULL,
    cpu_usage_percent DECIMAL(5,2),
    memory_usage_mb INTEGER,
    memory_limit_mb INTEGER,
    disk_usage_mb INTEGER,
    network_rx_bytes BIGINT,
    network_tx_bytes BIGINT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_metrics_env FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE,
    CONSTRAINT fk_metrics_project FOREIGN KEY (project_id) REFERENCES project_registrations(id) ON DELETE CASCADE,
    CONSTRAINT fk_metrics_container FOREIGN KEY (container_id) REFERENCES container_instances(id) ON DELETE CASCADE
);

CREATE INDEX idx_metrics_env_id ON resource_metrics(environment_id);
CREATE INDEX idx_metrics_project_id ON resource_metrics(project_id);
CREATE INDEX idx_metrics_timestamp ON resource_metrics(timestamp);
CREATE INDEX idx_metrics_type_timestamp ON resource_metrics(metric_type, timestamp);

-- Partitioning for time-series data (optional, for production use)
-- CREATE TABLE resource_metrics_2024_01 PARTITION OF resource_metrics
-- FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

CREATE TABLE IF NOT EXISTS resource_metrics_aggregated (
    id BIGSERIAL PRIMARY KEY,
    environment_id VARCHAR(36),
    project_id BIGINT,
    aggregation_period VARCHAR(20) NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    avg_cpu_percent DECIMAL(5,2),
    max_cpu_percent DECIMAL(5,2),
    avg_memory_mb INTEGER,
    max_memory_mb INTEGER,
    total_network_rx_gb DECIMAL(10,3),
    total_network_tx_gb DECIMAL(10,3),
    data_points INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_metrics_agg_env FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE,
    CONSTRAINT fk_metrics_agg_project FOREIGN KEY (project_id) REFERENCES project_registrations(id) ON DELETE CASCADE,
    CONSTRAINT aggregation_period_check CHECK (aggregation_period IN ('MINUTE', 'HOUR', 'DAY', 'WEEK', 'MONTH'))
);

CREATE INDEX idx_metrics_agg_env_id ON resource_metrics_aggregated(environment_id);
CREATE INDEX idx_metrics_agg_project_id ON resource_metrics_aggregated(project_id);
CREATE INDEX idx_metrics_agg_period ON resource_metrics_aggregated(aggregation_period, period_start);

-- =====================================================
-- USAGE REPORTS AND ANALYTICS
-- =====================================================

CREATE TABLE IF NOT EXISTS usage_reports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    project_id BIGINT,
    report_period VARCHAR(20) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_environments_created INTEGER DEFAULT 0,
    total_cpu_hours DECIMAL(10,2) DEFAULT 0,
    total_memory_gb_hours DECIMAL(10,2) DEFAULT 0,
    total_storage_gb_days DECIMAL(10,2) DEFAULT 0,
    total_network_transfer_gb DECIMAL(10,2) DEFAULT 0,
    estimated_cost_usd DECIMAL(10,2),
    environment_availability_percent DECIMAL(5,2),
    average_startup_time_seconds INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_usage_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_usage_project FOREIGN KEY (project_id) REFERENCES project_registrations(id) ON DELETE CASCADE,
    CONSTRAINT report_period_check CHECK (report_period IN ('DAILY', 'WEEKLY', 'MONTHLY'))
);

CREATE INDEX idx_usage_reports_user_id ON usage_reports(user_id);
CREATE INDEX idx_usage_reports_project_id ON usage_reports(project_id);
CREATE INDEX idx_usage_reports_period ON usage_reports(report_period, period_start);

CREATE TABLE IF NOT EXISTS usage_alerts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    project_id BIGINT,
    alert_type VARCHAR(50) NOT NULL,
    threshold_value DECIMAL(10,2) NOT NULL,
    current_value DECIMAL(10,2),
    is_active BOOLEAN DEFAULT true,
    last_triggered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alert_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_alert_project FOREIGN KEY (project_id) REFERENCES project_registrations(id) ON DELETE CASCADE,
    CONSTRAINT alert_type_check CHECK (alert_type IN ('CPU_USAGE', 'MEMORY_USAGE', 'COST_LIMIT', 'ENVIRONMENT_COUNT'))
);

CREATE INDEX idx_usage_alerts_user_id ON usage_alerts(user_id);
CREATE INDEX idx_usage_alerts_active ON usage_alerts(is_active);

-- =====================================================
-- CLOUD MIGRATION AND COST TRACKING
-- =====================================================

CREATE TABLE IF NOT EXISTS cloud_migration_plans (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    source_environment_id VARCHAR(36),
    target_cloud_provider VARCHAR(50) NOT NULL,
    target_region VARCHAR(50) NOT NULL,
    migration_strategy VARCHAR(50) NOT NULL,
    estimated_monthly_cost_usd DECIMAL(10,2),
    resource_mapping JSONB,
    migration_steps JSONB,
    risk_assessment JSONB,
    status VARCHAR(50) DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL,
    approved_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP,
    executed_at TIMESTAMP,
    CONSTRAINT fk_migration_project FOREIGN KEY (project_id) REFERENCES project_registrations(id),
    CONSTRAINT fk_migration_env FOREIGN KEY (source_environment_id) REFERENCES environments(id),
    CONSTRAINT fk_migration_creator FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_migration_approver FOREIGN KEY (approved_by) REFERENCES users(id),
    CONSTRAINT cloud_provider_check CHECK (target_cloud_provider IN ('AWS', 'AZURE', 'GCP', 'DIGITAL_OCEAN')),
    CONSTRAINT migration_strategy_check CHECK (migration_strategy IN ('LIFT_AND_SHIFT', 'REPLATFORM', 'REFACTOR')),
    CONSTRAINT migration_status_check CHECK (status IN ('DRAFT', 'APPROVED', 'IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_migration_plans_project_id ON cloud_migration_plans(project_id);
CREATE INDEX idx_migration_plans_status ON cloud_migration_plans(status);

CREATE TABLE IF NOT EXISTS scaling_policies (
    id BIGSERIAL PRIMARY KEY,
    environment_id VARCHAR(36) NOT NULL,
    policy_name VARCHAR(100) NOT NULL,
    metric_type VARCHAR(50) NOT NULL,
    scale_up_threshold DECIMAL(5,2) NOT NULL,
    scale_down_threshold DECIMAL(5,2) NOT NULL,
    scale_up_action JSONB NOT NULL,
    scale_down_action JSONB NOT NULL,
    cooldown_minutes INTEGER DEFAULT 5,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scaling_env FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE,
    CONSTRAINT metric_type_check CHECK (metric_type IN ('CPU', 'MEMORY', 'CUSTOM'))
);

CREATE INDEX idx_scaling_policies_env_id ON scaling_policies(environment_id);
CREATE INDEX idx_scaling_policies_active ON scaling_policies(is_active);

CREATE TABLE IF NOT EXISTS cloud_cost_tracking (
    id BIGSERIAL PRIMARY KEY,
    environment_id VARCHAR(36),
    project_id BIGINT,
    cloud_provider VARCHAR(50) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(255),
    cost_date DATE NOT NULL,
    usage_hours DECIMAL(10,2),
    usage_units VARCHAR(50),
    unit_cost DECIMAL(10,6),
    total_cost_usd DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    tags JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cost_env FOREIGN KEY (environment_id) REFERENCES environments(id) ON DELETE CASCADE,
    CONSTRAINT fk_cost_project FOREIGN KEY (project_id) REFERENCES project_registrations(id) ON DELETE CASCADE
);

CREATE INDEX idx_cost_tracking_env_id ON cloud_cost_tracking(environment_id);
CREATE INDEX idx_cost_tracking_project_id ON cloud_cost_tracking(project_id);
CREATE INDEX idx_cost_tracking_date ON cloud_cost_tracking(cost_date);
CREATE INDEX idx_cost_tracking_provider ON cloud_cost_tracking(cloud_provider);

-- =====================================================
-- DEFAULT DATA
-- =====================================================

-- Insert default templates
INSERT INTO environment_templates (id, name, description, category, docker_compose_content, min_memory_mb, min_cpu_cores, created_by) VALUES
('nodejs-react-dev', 'Node.js + React Development', 'Full-stack JavaScript development environment with PostgreSQL', 'web-development', 
'version: ''3.8''
services:
  backend:
    image: node:18-alpine
    working_dir: /app
    volumes:
      - ./backend:/app
      - /app/node_modules
    ports:
      - "3000:3000"
    environment:
      - NODE_ENV=development
      - DATABASE_URL=postgresql://postgres:${POSTGRES_PASSWORD}@db:5432/app_dev
    command: sh -c "npm install && npm run dev"
    depends_on:
      - db

  frontend:
    image: node:18-alpine
    working_dir: /app
    volumes:
      - ./frontend:/app
      - /app/node_modules
    ports:
      - "3001:3000"
    environment:
      - NODE_ENV=development
      - REACT_APP_API_URL=http://localhost:3000
    command: sh -c "npm install && npm start"

  db:
    image: postgres:16-alpine
    environment:
      - POSTGRES_DB=app_dev
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:', 2048, 2.0, 1),

('spring-boot-api', 'Spring Boot API', 'Java REST API development with PostgreSQL and Redis', 'backend-development',
'version: ''3.8''
services:
  app:
    image: openjdk:17-alpine
    working_dir: /app
    volumes:
      - ./:/app
      - ~/.m2:/root/.m2
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/api_dev
      - SPRING_DATASOURCE_USERNAME=postgres
      - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
      - SPRING_REDIS_HOST=redis
      - SPRING_REDIS_PORT=6379
    command: sh -c "./mvnw spring-boot:run"
    depends_on:
      - db
      - redis

  db:
    image: postgres:16-alpine
    environment:
      - POSTGRES_DB=api_dev
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data

volumes:
  postgres_data:
  redis_data:', 3072, 2.0, 1),

('python-flask-dev', 'Python Flask Development', 'Python web application with PostgreSQL', 'web-development',
'version: ''3.8''
services:
  web:
    image: python:3.11-alpine
    working_dir: /app
    volumes:
      - ./:/app
    ports:
      - "5000:5000"
    environment:
      - FLASK_APP=app.py
      - FLASK_ENV=development
      - DATABASE_URL=postgresql://postgres:${POSTGRES_PASSWORD}@db:5432/flask_dev
    command: sh -c "pip install -r requirements.txt && flask run --host=0.0.0.0"
    depends_on:
      - db

  db:
    image: postgres:16-alpine
    environment:
      - POSTGRES_DB=flask_dev
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:', 1024, 1.0, 1),

('java-microservice', 'Java Microservice', 'Spring Boot microservice with embedded database', 'microservice',
'version: ''3.8''
services:
  service:
    image: openjdk:17-alpine
    working_dir: /app
    volumes:
      - ./:/app
      - ~/.m2:/root/.m2
    ports:
      - "8080:8080"
      - "8081:8081"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - MANAGEMENT_SERVER_PORT=8081
    command: sh -c "./mvnw spring-boot:run"', 2048, 1.5, 1);

-- Insert template ports
INSERT INTO template_ports (template_id, service_name, internal_port, description) VALUES
('nodejs-react-dev', 'backend', 3000, 'Node.js backend API'),
('nodejs-react-dev', 'frontend', 3000, 'React development server'),
('nodejs-react-dev', 'db', 5432, 'PostgreSQL database'),
('spring-boot-api', 'app', 8080, 'Spring Boot application'),
('spring-boot-api', 'db', 5432, 'PostgreSQL database'),
('spring-boot-api', 'redis', 6379, 'Redis cache'),
('python-flask-dev', 'web', 5000, 'Flask application'),
('python-flask-dev', 'db', 5432, 'PostgreSQL database'),
('java-microservice', 'service', 8080, 'Main service port'),
('java-microservice', 'service', 8081, 'Management/metrics port');

-- Create default admin user (password must be set via environment variable)
-- Example: Set ADMIN_PASSWORD_HASH environment variable with bcrypt hash of desired password
-- Generate hash: htpasswd -bnBC 10 "" yourpassword | tr -d ':\n' | sed 's/$2y/$2a/'
INSERT INTO users (username, email, password_hash, role, environment_limit) 
SELECT 'admin', 'admin@devorchestrator.local', '${ADMIN_PASSWORD_HASH}', 'ADMIN', 100
WHERE '${ADMIN_PASSWORD_HASH}' != ''
ON CONFLICT (username) DO NOTHING;