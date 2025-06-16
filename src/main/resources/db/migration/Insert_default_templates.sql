-- Insert pre-built environment templates
INSERT INTO environment_templates (id, name, description, docker_compose_content, memory_limit_mb, cpu_limit, is_public, created_at) VALUES
('nodejs-react-dev', 'Node.js + React Development', 'Full-stack JavaScript development environment with Node.js backend, React frontend, and PostgreSQL database', 
'version: ''3.8''
services:
  backend:
    image: node:18-alpine
    working_dir: /app
    volumes:
      - ./backend:/app
    command: npm run dev
    ports:
      - "3001:3001"
    environment:
      - NODE_ENV=development
      - DATABASE_URL=postgresql://postgres:password@db:5432/devdb
    depends_on:
      - db
  frontend:
    image: node:18-alpine
    working_dir: /app
    volumes:
      - ./frontend:/app
    command: npm start
    ports:
      - "3000:3000"
    environment:
      - REACT_APP_API_URL=http://localhost:3001
  db:
    image: postgres:15-alpine
    environment:
      - POSTGRES_DB=devdb
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
volumes:
  postgres_data:', 1024, 2.0, true, CURRENT_TIMESTAMP),

('spring-boot-api', 'Spring Boot API Development', 'Java Spring Boot REST API development with PostgreSQL and Redis', 
'version: ''3.8''
services:
  app:
    image: openjdk:17-jdk-alpine
    working_dir: /app
    volumes:
      - ./:/app
    command: ./mvnw spring-boot:run
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - DATABASE_URL=jdbc:postgresql://db:5432/springdb
      - REDIS_URL=redis://redis:6379
    depends_on:
      - db
      - redis
  db:
    image: postgres:15-alpine
    environment:
      - POSTGRES_DB=springdb
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=password
    ports:
      - "5433:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
volumes:
  postgres_data:', 1024, 2.0, true, CURRENT_TIMESTAMP),

('python-flask-dev', 'Python Flask Development', 'Python Flask web application development with PostgreSQL database',
'version: ''3.8''
services:
  app:
    image: python:3.11-alpine
    working_dir: /app
    volumes:
      - ./:/app
    command: flask run --host=0.0.0.0 --port=5000
    ports:
      - "5000:5000"
    environment:
      - FLASK_ENV=development
      - DATABASE_URL=postgresql://postgres:password@db:5432/flaskdb
    depends_on:
      - db
  db:
    image: postgres:15-alpine
    environment:
      - POSTGRES_DB=flaskdb
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=password
    ports:
      - "5434:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
volumes:
  postgres_data:', 768, 1.5, true, CURRENT_TIMESTAMP),

('java-microservice', 'Java Microservice', 'Spring Boot microservice template with database connectivity and monitoring',
'version: ''3.8''
services:
  microservice:
    image: openjdk:17-jdk-alpine
    working_dir: /app
    volumes:
      - ./:/app
    command: ./mvnw spring-boot:run
    ports:
      - "8081:8081"
    environment:
      - SERVER_PORT=8081
      - SPRING_PROFILES_ACTIVE=dev
      - DATABASE_URL=jdbc:postgresql://db:5432/microdb
    depends_on:
      - db
  db:
    image: postgres:15-alpine
    environment:
      - POSTGRES_DB=microdb
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=password
    ports:
      - "5435:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
volumes:
  postgres_data:', 512, 1.0, true, CURRENT_TIMESTAMP);

-- Insert corresponding exposed ports for each template
INSERT INTO template_ports (template_id, port) VALUES
('nodejs-react-dev', 3000),
('nodejs-react-dev', 3001),
('nodejs-react-dev', 5432),
('spring-boot-api', 8080),
('spring-boot-api', 5433),
('spring-boot-api', 6379),
('python-flask-dev', 5000),
('python-flask-dev', 5434),
('java-microservice', 8081),
('java-microservice', 5435);