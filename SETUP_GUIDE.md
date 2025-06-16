# Development Environment Orchestrator - Setup Guide

A comprehensive guide to setting up and running the Development Environment Orchestrator locally.

## Prerequisites

Before you begin, ensure you have the following installed on your system:

### Required Software

#### Cross-Platform Installation
- **Java 21+** - [Download OpenJDK 21](https://adoptium.net/temurin/releases/?version=21)
  - Windows: Use installer or Chocolatey: `choco install openjdk21`
  - macOS: Use installer or Homebrew: `brew install openjdk@21`
  - Ubuntu: `sudo apt update && sudo apt install openjdk-21-jdk`
- **Maven 3.6+** - [Download Maven](https://maven.apache.org/download.cgi)
  - Windows: Use installer or Chocolatey: `choco install maven`
  - macOS: Use installer or Homebrew: `brew install maven`
  - Ubuntu: `sudo apt install maven`
- **Docker 20.10+** - [Install Docker](https://docs.docker.com/get-docker/)
  - Windows: Docker Desktop for Windows
  - macOS: Docker Desktop for Mac
  - Ubuntu: `sudo apt install docker.io docker-compose`
- **Docker Compose v2** - Usually included with Docker Desktop
- **PostgreSQL 16+** - [Install PostgreSQL](https://www.postgresql.org/download/) (Optional - Docker recommended)
- **Redis 7.2+** - [Install Redis](https://redis.io/download) (Optional - Docker recommended)

### Development Tools (Recommended)
- **IDE**: IntelliJ IDEA, Eclipse, or VS Code with Java extensions
- **Git** for version control
- **Postman** or similar for API testing

## Quick Start

### 1. Clone the Repository
```bash
git clone <repository-url>
cd dev-environment-orchestrator
```

### 2. Database Setup

#### Option A: Using Docker (Recommended - All Platforms)
```bash
# Start PostgreSQL and Redis with Docker
# Works on Windows, macOS, and Linux
docker run -d \
  --name dev-postgres \
  -e POSTGRES_DB=dev_orchestrator \
  -e POSTGRES_USER=dev_user \
  -e POSTGRES_PASSWORD=dev_password \
  -p 5432:5432 \
  postgres:16-alpine

docker run -d \
  --name dev-redis \
  -p 6379:6379 \
  redis:7.2-alpine
```

**Platform-Specific Notes:**
- **Windows**: Ensure Docker Desktop is running and WSL2 backend is enabled
- **macOS**: Ensure Docker Desktop is running 
- **Ubuntu**: Ensure Docker daemon is running: `sudo systemctl start docker`

#### Option B: Local Installation
1. **PostgreSQL Setup**:
   ```sql
   CREATE DATABASE dev_orchestrator;
   CREATE USER dev_user WITH PASSWORD 'dev_password';
   GRANT ALL PRIVILEGES ON DATABASE dev_orchestrator TO dev_user;
   ```

2. **Redis Setup**:
   ```bash
   # Start Redis server (varies by OS)
   redis-server
   ```

### 3. Environment Configuration

Create a `.env` file in the project root (optional - defaults are provided):
```bash
# Database Configuration
DB_HOST=localhost
DB_PORT=5432
DB_NAME=dev_orchestrator
DB_USERNAME=dev_user
DB_PASSWORD=dev_password

# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Docker Configuration
DOCKER_HOST=unix:///var/run/docker.sock

# Security Configuration
JWT_SECRET=your-secret-key-change-in-production
JWT_ISSUER_URI=http://localhost:8080/api/auth

# CORS Configuration
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8080
```

### 4. Build and Run

```bash
# Install dependencies and compile (All platforms)
mvn clean compile

# Run database migrations (All platforms)
mvn flyway:migrate

# Start the application (All platforms)
mvn spring-boot:run

# Or with specific profile (All platforms)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Platform-Specific Notes:**
- **Windows**: Use `mvn.cmd` if `mvn` command is not recognized, or run commands in Command Prompt/PowerShell as Administrator
- **macOS/Linux**: Ensure Maven is in your PATH, or use `./mvnw` if Maven wrapper is available

The application will start on `http://localhost:8080/api`

## Development Setup

### IDE Configuration

#### IntelliJ IDEA
1. Import as Maven project
2. Set Project SDK to Java 21
3. Enable annotation processing:
   - Settings → Build → Compiler → Annotation Processors
   - Check "Enable annotation processing"
4. Install Lombok plugin if not already installed

#### VS Code
1. Install Java Extension Pack
2. Install Spring Boot Extension Pack
3. Configure Java version to 21 in settings

### Hot Reload Setup
For development with automatic restart:
```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=dev"
```

## Testing

### Run Tests
```bash
# Unit tests only
mvn test

# Integration tests
mvn verify

# All tests with coverage report
mvn clean verify jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

### Test Requirements
- **Minimum 80% code coverage** enforced by JaCoCo
- **Zero critical security issues** validated by SpotBugs
- Tests use Testcontainers for database integration

## Docker Development

### Build Docker Image
```bash
# Build application JAR
mvn clean package

# Build Docker image
mvn spring-boot:build-image

# Or using Docker directly
docker build -t dev-orchestrator:latest .
```

### Run with Docker Compose
If you have a docker-compose.yml:
```bash
docker-compose up -d
```

## Health Checks & Monitoring

### Application Health
- Health endpoint: `http://localhost:8080/api/actuator/health`
- Metrics: `http://localhost:8080/api/actuator/metrics`
- Prometheus metrics: `http://localhost:8080/api/actuator/prometheus`

### API Documentation
- Swagger UI: `http://localhost:8080/api/swagger-ui.html`
- OpenAPI spec: `http://localhost:8080/api/v3/api-docs`

## Security Setup

### JWT Configuration
1. Generate a secure JWT secret:
   ```bash
   openssl rand -base64 64
   ```
2. Update the `JWT_SECRET` environment variable

### OAuth2 Setup (Optional)
For external OAuth2 providers, configure:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://your-oauth-provider.com
```

## Troubleshooting

### Common Issues

#### Port Already in Use
**Windows:**
```cmd
# Find process using port 8080
netstat -ano | findstr :8080

# Kill the process (replace PID with actual process ID)
taskkill /PID <PID> /F
```

**macOS/Linux:**
```bash
# Find process using port 8080
lsof -i :8080

# Kill the process
kill -9 <PID>
```

**Alternative (All platforms):**
```yaml
# Change the port in application.yml
server:
  port: 8081
```

#### Database Connection Failed
**All platforms:**
1. Verify PostgreSQL container is running:
   ```bash
   docker ps | grep dev-postgres
   ```
2. Check database credentials in configuration
3. Test connection:
   ```bash
   docker exec -it dev-postgres psql -U dev_user -d dev_orchestrator -c "SELECT 1;"
   ```

#### Docker Connection Issues
**Windows:**
- Ensure Docker Desktop is running
- Check Windows Services for Docker Engine status
- Restart Docker Desktop if needed

**macOS:**
- Ensure Docker Desktop is running
- Check Activity Monitor for Docker processes
- Restart Docker Desktop if needed

**Linux/Ubuntu:**
```bash
# Check Docker daemon status
sudo systemctl status docker

# Start Docker if stopped
sudo systemctl start docker

# Add user to docker group (requires logout/login)
sudo usermod -aG docker $USER
```

#### OutOfMemoryError
Increase JVM heap size:
```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx2048m"
```

### Logging
- Application logs: Console output
- Change log levels in `application.yml`
- Enable SQL logging for debugging:
  ```yaml
  logging:
    level:
      org.hibernate.SQL: DEBUG
  ```

## Production Deployment

### Build for Production
```bash
# Build with production profile
mvn clean package -Pprod

# Create production Docker image
mvn spring-boot:build-image -Pprod
```

### Environment Variables
Set these environment variables in production:
```bash
SPRING_PROFILES_ACTIVE=prod
DB_HOST=production-db-host
DB_USERNAME=prod_user
DB_PASSWORD=secure-password
JWT_SECRET=production-secret-key
REDIS_HOST=production-redis-host
```

### Performance Tuning
- Set appropriate JVM heap size: `-Xms512m -Xmx2048m`
- Configure connection pool sizes in `application.yml`
- Enable production logging configuration

## Development Workflow

### Getting Started
1. Create feature branch: `git checkout -b feature/your-feature`
2. Make changes and write tests
3. Run full test suite: `mvn verify`
4. Check code coverage: `mvn jacoco:report`
5. Submit pull request

### Adding New Features
- Follow the established package structure in `/src/main/java/com/devorchestrator/`
- Add corresponding tests in `/src/test/java/`
- Update API documentation
- Follow project coding standards and conventions

### API Usage Examples
```bash
# Create environment
curl -X POST http://localhost:8080/api/v1/environments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"templateId": "nodejs-react-dev", "name": "My Dev Environment"}'

# List environments
curl -X GET http://localhost:8080/api/v1/environments \
  -H "Authorization: Bearer <token>"
```

## Support

- **Documentation**: Check the project README.md for detailed architecture information
- **Issues**: Report bugs and feature requests through GitHub Issues
- **Contributing**: Follow established project conventions and coding standards