# Development Environment Orchestrator

> **Automatically analyze projects and provision development environments locally or in the cloud with intelligent technology detection**

A comprehensive Spring Boot platform that analyzes your codebase, detects technologies, and orchestrates development environments across Docker and cloud providers (AWS, Azure, GCP). Features automatic stack detection, infrastructure provisioning, and real-time monitoring.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Quick Start](#quick-start)
- [API Reference](#api-reference)
- [Available Templates](#available-templates)
- [Development Guide](#development-guide)
- [Architecture](#architecture)
- [Contributing](#contributing)
- [License](#license)

## Overview

The Development Environment Orchestrator solves the common problem of complex development setup. Instead of manually configuring databases, services, and dependencies, developers can:

1. **Choose a template** (Node.js + React, Spring Boot, Python Flask, etc.)
2. **Create an environment** with a single API call
3. **Access their stack** through allocated ports
4. **Monitor in real-time** via WebSocket connections
5. **Clean up automatically** when done

### Problem It Solves

- ❌ **Before**: "It works on my machine" - hours spent configuring local environments
- ✅ **After**: Standardized, reproducible environments deployed in seconds

## Features

### 🚀 Core Capabilities
- **Intelligent Project Analysis** - Automatically detect languages, frameworks, databases, and dependencies
- **Multi-Infrastructure Support** - Deploy to Docker locally or provision in AWS, Azure, GCP via Terraform
- **Auto-Generated Templates** - Create Docker Compose, Kubernetes, and Terraform configs from project analysis
- **Real-Time Monitoring** - Live metrics, status updates, and resource usage tracking
- **Cost Estimation** - Predict cloud infrastructure costs before deployment
- **Project Registry** - Register and manage multiple projects with VCS integration

### 🛡️ Enterprise Ready
- **JWT Authentication** - Secure API access with role-based permissions
- **Resource Management** - CPU/memory limits and port allocation
- **Async Operations** - Non-blocking API with WebSocket notifications
- **Health Monitoring** - Built-in health checks and metrics

### 🔧 Developer Experience
- **REST API** - Comprehensive endpoints for environments, projects, and infrastructure
- **WebSocket Updates** - Real-time metrics, logs, and status notifications
- **Swagger Documentation** - Interactive API explorer
- **Technology Detection** - Support for 30+ languages and 40+ frameworks
- **Template Generation** - Auto-create deployment configs from analysis

## Tech Stack

| Component | Technology |
|-----------|------------|
| **Backend** | Spring Boot 3.3, Java 21 |
| **Database** | PostgreSQL 16 with Flyway migrations |
| **Caching** | Redis 7.2 for session management |
| **Security** | JWT authentication, OAuth2 resource server |
| **Containers** | Docker Java API with async orchestration |
| **Cloud** | Terraform integration for AWS, Azure, GCP |
| **Analysis** | Pattern-based technology detection engine |
| **Real-time** | WebSocket for metrics and status updates |
| **Quality** | 80% test coverage, Testcontainers, SpotBugs |

## Quick Start

### Prerequisites

- **Java 21+** - [Download OpenJDK](https://openjdk.java.net/)
- **Docker & Docker Compose** - [Install Docker Desktop](https://www.docker.com/products/docker-desktop/)
- **Maven 3.6+** - [Download Maven](https://maven.apache.org/download.cgi)

### Installation

```bash
# 1. Clone the repository
git clone <repository-url>
cd dev-environment-orchestrator

# 2. Start supporting services
docker-compose -f docker/dev/docker-compose.yml up -d

# 3. Build and run the application
mvn clean compile
mvn flyway:migrate -Pdev
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 4. Verify it's running
curl http://localhost:8080/actuator/health
```

### First API Call

```bash
# Create your first environment
curl -X POST http://localhost:8080/api/v1/environments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-jwt-token>" \
  -d '{
    "name": "my-dev-env",
    "templateId": "nodejs-react-dev"
  }'

# Response: {"id": "env-123", "status": "CREATING", ...}
```

## API Reference

### Environment Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/environments` | List user environments |
| `POST` | `/api/v1/environments` | Create new environment (async) |
| `POST` | `/api/v1/environments/infrastructure` | Create cloud infrastructure environment |
| `GET` | `/api/v1/environments/{id}` | Get environment details |
| `GET` | `/api/v1/environments/{id}/details` | Get detailed infrastructure info |
| `POST` | `/api/v1/environments/{id}/start` | Start stopped environment |
| `POST` | `/api/v1/environments/{id}/stop` | Stop running environment |
| `DELETE` | `/api/v1/environments/{id}` | Destroy environment |
| `GET` | `/api/v1/environments/{id}/resources` | List cloud resources |
| `GET` | `/api/v1/environments/{id}/terraform/plan` | Get Terraform plan |
| `GET` | `/api/v1/environments/{id}/terraform/outputs` | Get Terraform outputs |

### Template Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/templates` | List available templates |
| `GET` | `/api/v1/templates/{id}` | Get template details |

### Project Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/projects/register` | Register a new project |
| `POST` | `/api/v1/projects/{id}/analyze` | Analyze project technologies |
| `GET` | `/api/v1/projects/{id}` | Get project details |
| `POST` | `/api/v1/projects/{id}/start` | Start project environment |
| `POST` | `/api/v1/projects/{id}/stop` | Stop project environment |
| `GET` | `/api/v1/projects/{id}/metrics` | Get project metrics |
| `POST` | `/api/v1/projects/{id}/generate-template` | Generate deployment templates |
| `GET` | `/api/v1/projects/{id}/cost-estimate` | Estimate cloud costs |

### Real-Time Updates

| WebSocket | Purpose |
|-----------|---------|
| `/ws/environment/{id}/status` | Environment status changes |
| `/ws/environment/{id}/logs` | Container log streaming |
| `/ws/metrics` | Real-time resource metrics |

### Example Usage

```javascript
// Create environment (returns immediately)
const response = await fetch('/api/v1/environments', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + token
  },
  body: JSON.stringify({
    name: 'my-app-dev',
    templateId: 'spring-boot-api'
  })
});

const env = await response.json();

// Connect to WebSocket for real-time updates
const ws = new WebSocket(`ws://localhost:8080/ws/environment/${env.id}/status`);
ws.onmessage = (event) => {
  const status = JSON.parse(event.data);
  console.log(`Environment status: ${status.state}`);
  // CREATING → RUNNING → ready to use!
};
```

**📖 Full API Documentation**: http://localhost:8080/swagger-ui.html

## Available Templates

| Template | Stack | Use Case |
|----------|-------|----------|
| `nodejs-react-dev` | Node.js + React + PostgreSQL | Full-stack JavaScript development |
| `spring-boot-api` | Spring Boot + PostgreSQL + Redis | Java REST API development |
| `python-flask-dev` | Python Flask + PostgreSQL | Python web application development |
| `java-microservice` | Spring Boot + Database | Microservice development |

### Template Structure Example

```yaml
# Example: nodejs-react-dev template
services:
  backend:
    image: node:18-alpine
    ports: ["3000:3000"]
    environment:
      DATABASE_URL: postgresql://postgres:password@db:5432/app
  
  frontend:
    image: node:18-alpine
    ports: ["3001:3000"]
    
  database:
    image: postgres:16
    environment:
      POSTGRES_DB: app
      POSTGRES_PASSWORD: password
```

## Development Guide

### Project Structure

```
src/main/java/com/devorchestrator/
├── analyzer/           # Project analysis and technology detection
│   ├── detector/      # Language, framework, database detectors
│   └── model/         # Detection models and patterns
├── controller/         # REST API endpoints
├── service/           # Business logic (async orchestration)
├── repository/        # Data access layer
├── entity/           # JPA entities
├── dto/              # API request/response models
├── infrastructure/    # Cloud infrastructure management
│   ├── provider/     # Cloud provider implementations
│   ├── terraform/    # Terraform integration
│   └── state/        # Infrastructure state management
├── config/           # Spring configuration
├── security/         # JWT authentication
├── websocket/        # Real-time communication
└── exception/        # Error handling
```

### Running Tests

```bash
# Unit tests
mvn test

# Integration tests (requires Docker)
mvn verify

# Generate coverage report
mvn jacoco:report
# View at: target/site/jacoco/index.html

# All quality checks
mvn clean verify spotbugs:check
```

### Development Profiles

| Profile | Usage | Description |
|---------|-------|-------------|
| `dev` | `mvn spring-boot:run -Pdev` | Docker Compose databases, debug logging |
| `test` | `mvn test` | H2 in-memory database, fast startup |
| `local` | `mvn spring-boot:run -Plocal` | Local PostgreSQL/Redis installation |

### IDE Setup

**IntelliJ IDEA:**
1. Import as Maven project
2. Set Java 21 SDK
3. Enable Lombok annotation processing
4. Install Lombok plugin

**VS Code:**
1. Install Java Extension Pack
2. Install Spring Boot Extension Pack
3. Install Lombok support

### Common Issues & Solutions

| Problem | Solution |
|---------|----------|
| **Port 8080 already in use** | `lsof -ti:8080 \| xargs kill -9` |
| **Database connection failed** | `docker-compose -f docker/dev/docker-compose.yml up -d` |
| **Docker daemon not running** | Start Docker Desktop |
| **Build fails** | `mvn clean compile -U` |

## Architecture

### Design Principles

The application follows a **non-blocking, async architecture** to handle long-running Docker operations:

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   REST API      │    │  Async Service   │    │   Docker API    │
│                 │    │                  │    │                 │
│ POST /envs      │───▶│ orchestrator     │───▶│ container       │
│ Returns immediately │    │ (dedicated pool) │    │ operations      │
│                 │    │                  │    │                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                       │
         │              ┌──────────────────┐
         │              │   WebSocket      │
         └──────────────│   Notifications  │
                        │                  │
                        └──────────────────┘
```

### Key Decisions

- **Async Operations**: Docker operations run on dedicated thread pool to prevent blocking web threads
- **Immediate API Responses**: REST endpoints return immediately, status updates come via WebSocket
- **Security**: Direct Docker Java API usage eliminates shell injection vulnerabilities
- **Scalability**: Thread pool isolation allows handling many concurrent environment operations

### Database Schema

```sql
-- Core entities
users (id, username, email, role)
environment_templates (id, name, docker_compose_content, terraform_template)
environments (id, name, status, owner_id, template_id, project_id, infrastructure_provider)
container_instances (id, environment_id, docker_container_id, status, host_port)

-- Project management
project_registrations (id, name, repository_url, detected_technologies)
project_analyses (id, project_id, analysis_results, recommendations)

-- Cloud infrastructure
terraform_states (id, environment_id, state_data)
environment_cloud_resources (id, environment_id, resource_type, cloud_resource_id)

-- Monitoring
resource_metrics (id, environment_id, cpu_usage, memory_usage, timestamp)
usage_reports (id, user_id, total_cpu_hours, total_memory_gb_hours, cost_estimate)
```

## Contributing

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Make** your changes
4. **Test** thoroughly (`mvn verify`)
5. **Commit** your changes (`git commit -m 'Add amazing feature'`)
6. **Push** to the branch (`git push origin feature/amazing-feature`)
7. **Open** a Pull Request

### Development Guidelines

- Maintain **80%+ test coverage**
- Follow **Spring Boot best practices**
- Use **async patterns** for long-running operations
- Add **comprehensive error handling**
- Update **API documentation**

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Support

- 📚 **Documentation**: [API Docs](http://localhost:8080/swagger-ui.html)
- 🐛 **Issues**: [GitHub Issues](../../issues)
- 💬 **Discussions**: [GitHub Discussions](../../discussions)

**Built with ❤️ using Spring Boot and Docker**