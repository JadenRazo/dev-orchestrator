# Development Environment Orchestrator

> **Instantly create and manage containerized development environments with a simple REST API**

A Spring Boot application that orchestrates Docker containers to provide complete, isolated development stacks. Perfect for teams who want to standardize development environments and eliminate "works on my machine" problems.

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
- **One-Click Environment Creation** - Deploy complete dev stacks in seconds
- **Template System** - Pre-configured stacks for popular frameworks
- **Real-Time Monitoring** - Live status updates and container logs
- **Multi-User Support** - User isolation and resource quotas
- **Automatic Cleanup** - Scheduled cleanup of idle environments

### 🛡️ Enterprise Ready
- **JWT Authentication** - Secure API access with role-based permissions
- **Resource Management** - CPU/memory limits and port allocation
- **Async Operations** - Non-blocking API with WebSocket notifications
- **Health Monitoring** - Built-in health checks and metrics

### 🔧 Developer Experience
- **REST API** - Simple HTTP endpoints for all operations
- **WebSocket Updates** - Real-time progress notifications
- **Swagger Documentation** - Interactive API explorer
- **Docker Integration** - Secure Docker API usage (no shell injection)

## Tech Stack

| Component | Technology |
|-----------|------------|
| **Backend** | Spring Boot 3.3, Java 21 |
| **Database** | PostgreSQL 16 with Flyway migrations |
| **Caching** | Redis 7.2 for session management |
| **Security** | JWT authentication, OAuth2 resource server |
| **Containers** | Docker Java API with async orchestration |
| **Real-time** | WebSocket connections |
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
| `GET` | `/api/v1/environments/{id}` | Get environment details |
| `POST` | `/api/v1/environments/{id}/start` | Start stopped environment |
| `POST` | `/api/v1/environments/{id}/stop` | Stop running environment |
| `DELETE` | `/api/v1/environments/{id}` | Destroy environment |

### Template Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/templates` | List available templates |
| `GET` | `/api/v1/templates/{id}` | Get template details |

### Real-Time Updates

| WebSocket | Purpose |
|-----------|---------|
| `/ws/environment/{id}/status` | Environment status changes |
| `/ws/environment/{id}/logs` | Container log streaming |

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
├── controller/          # REST API endpoints
├── service/            # Business logic (async orchestration)
├── repository/         # Data access layer
├── entity/            # JPA entities
├── dto/               # API request/response models
├── config/            # Spring configuration
├── security/          # JWT authentication
├── websocket/         # Real-time communication
└── exception/         # Error handling
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
environment_templates (id, name, docker_compose_content)
environments (id, name, status, owner_id, template_id, created_at)
container_instances (id, environment_id, docker_container_id, status, host_port)
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