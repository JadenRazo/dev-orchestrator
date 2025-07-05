package com.devorchestrator.analyzer.detector;

import com.devorchestrator.analyzer.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
@Slf4j
public class DatabaseDetectorService implements TechnologyDetector {
    
    private static final Map<String, DatabasePattern> DATABASE_PATTERNS = new HashMap<>();
    
    static {
        initializeDatabasePatterns();
    }
    
    private static void initializeDatabasePatterns() {
        // PostgreSQL
        DATABASE_PATTERNS.put("postgresql", new DatabasePattern(
            "PostgreSQL",
            Arrays.asList(
                "postgres://", "postgresql://", "psql://",
                "jdbc:postgresql://", "postgres:", "POSTGRES_"
            ),
            Arrays.asList("psycopg2", "asyncpg", "pg", "node-postgres", "pq", "gorm.io/driver/postgres"),
            Arrays.asList("*.sql", "schema.sql", "migrations/*.sql"),
            5432,
            "postgres:15-alpine"
        ));
        
        // MySQL/MariaDB
        DATABASE_PATTERNS.put("mysql", new DatabasePattern(
            "MySQL",
            Arrays.asList(
                "mysql://", "mysqli://", "jdbc:mysql://",
                "mysql:", "MYSQL_", "mariadb://", "MariaDB"
            ),
            Arrays.asList("mysql2", "mysqlclient", "pymysql", "aiomysql", "mysql-connector", "gorm.io/driver/mysql"),
            Arrays.asList("*.sql", "schema.sql"),
            3306,
            "mysql:8-alpine"
        ));
        
        // MongoDB
        DATABASE_PATTERNS.put("mongodb", new DatabasePattern(
            "MongoDB",
            Arrays.asList(
                "mongodb://", "mongodb+srv://", "mongo:",
                "MONGO_", "MONGODB_"
            ),
            Arrays.asList("mongodb", "mongoose", "pymongo", "motor", "mongo-driver"),
            Arrays.asList("*.js", "schema.js"),
            27017,
            "mongo:6-alpine"
        ));
        
        // Redis
        DATABASE_PATTERNS.put("redis", new DatabasePattern(
            "Redis",
            Arrays.asList(
                "redis://", "rediss://", "redis:",
                "REDIS_", "CACHE_"
            ),
            Arrays.asList("redis", "ioredis", "redis-py", "go-redis", "jedis", "lettuce"),
            null,
            6379,
            "redis:7-alpine"
        ));
        
        // SQLite
        DATABASE_PATTERNS.put("sqlite", new DatabasePattern(
            "SQLite",
            Arrays.asList(
                "sqlite://", "sqlite3://", ".db", ".sqlite",
                ".sqlite3", "file:.*\\.db"
            ),
            Arrays.asList("sqlite3", "better-sqlite3", "sqlite", "gorm.io/driver/sqlite"),
            Arrays.asList("*.db", "*.sqlite", "*.sqlite3"),
            null,
            null
        ));
        
        // Elasticsearch
        DATABASE_PATTERNS.put("elasticsearch", new DatabasePattern(
            "Elasticsearch",
            Arrays.asList(
                "elasticsearch://", "elastic:", "ES_",
                "ELASTICSEARCH_", "localhost:9200"
            ),
            Arrays.asList("elasticsearch", "@elastic/elasticsearch", "elasticsearch-py", "elastic/go-elasticsearch"),
            null,
            9200,
            "elasticsearch:8.11.3"
        ));
        
        // Cassandra
        DATABASE_PATTERNS.put("cassandra", new DatabasePattern(
            "Cassandra",
            Arrays.asList(
                "cassandra://", "cassandra:", "CASSANDRA_",
                "ContactPoints", "cassandra-driver"
            ),
            Arrays.asList("cassandra-driver", "cassandra-driver-core", "gocql"),
            Arrays.asList("*.cql"),
            9042,
            "cassandra:4.1"
        ));
        
        // DynamoDB
        DATABASE_PATTERNS.put("dynamodb", new DatabasePattern(
            "DynamoDB",
            Arrays.asList(
                "dynamodb://", "amazonaws.com/dynamodb",
                "DYNAMO_", "AWS_DYNAMODB_"
            ),
            Arrays.asList("aws-sdk", "@aws-sdk/client-dynamodb", "boto3", "aws-sdk-go"),
            null,
            8000,
            "amazon/dynamodb-local:latest"
        ));
        
        // RabbitMQ (Message Queue)
        DATABASE_PATTERNS.put("rabbitmq", new DatabasePattern(
            "RabbitMQ",
            Arrays.asList(
                "amqp://", "amqps://", "rabbitmq:",
                "RABBITMQ_", "AMQP_"
            ),
            Arrays.asList("amqplib", "pika", "kombu", "amqp"),
            null,
            5672,
            "rabbitmq:3.12-alpine"
        ));
        
        // Kafka (Message Queue)
        DATABASE_PATTERNS.put("kafka", new DatabasePattern(
            "Kafka",
            Arrays.asList(
                "kafka://", "kafka:", "KAFKA_",
                "bootstrap.servers", "kafka-clients"
            ),
            Arrays.asList("kafkajs", "kafka-python", "confluent-kafka", "sarama"),
            null,
            9092,
            "confluentinc/cp-kafka:7.5.0"
        ));
        
        // Memcached
        DATABASE_PATTERNS.put("memcached", new DatabasePattern(
            "Memcached",
            Arrays.asList(
                "memcached://", "memcache:", "MEMCACHED_",
                "MEMCACHE_"
            ),
            Arrays.asList("memcached", "pymemcache", "node-memcached"),
            null,
            11211,
            "memcached:1.6-alpine"
        ));
    }
    
    @Override
    public void detect(Path projectPath, ProjectAnalysis analysis) {
        log.info("Starting database detection for project: {}", projectPath);
        
        Map<String, DatabaseInfo> detectedDatabases = new HashMap<>();
        
        // Scan configuration files
        scanConfigurationFiles(projectPath, detectedDatabases);
        
        // Scan environment files
        scanEnvironmentFiles(projectPath, detectedDatabases, analysis);
        
        // Scan source code for connection strings
        scanSourceCode(projectPath, detectedDatabases);
        
        // Check for database migration files
        checkMigrationFiles(projectPath, detectedDatabases);
        
        // Check docker-compose files
        scanDockerCompose(projectPath, detectedDatabases);
        
        // Check dependency files
        scanDependencies(projectPath, detectedDatabases);
        
        // Convert to DetectedDatabase objects
        detectedDatabases.values().stream()
            .map(this::createDetectedDatabase)
            .forEach(analysis::addDatabase);
        
        log.info("Detected {} databases in project", detectedDatabases.size());
    }
    
    private void scanConfigurationFiles(Path projectPath, Map<String, DatabaseInfo> detected) {
        List<String> configFiles = Arrays.asList(
            "application.properties", "application.yml", "application.yaml",
            "config.json", "config.yml", "settings.py", "config.py",
            "database.yml", "database.json", ".env", ".env.local"
        );
        
        for (String configFile : configFiles) {
            Path configPath = projectPath.resolve(configFile);
            if (Files.exists(configPath)) {
                try {
                    String content = Files.readString(configPath);
                    detectDatabaseConnections(content, detected);
                } catch (IOException e) {
                    log.debug("Failed to read config file: {}", configFile, e);
                }
            }
        }
    }
    
    private void scanEnvironmentFiles(Path projectPath, Map<String, DatabaseInfo> detected, ProjectAnalysis analysis) {
        Path envFile = projectPath.resolve(".env");
        Path envExample = projectPath.resolve(".env.example");
        
        List<Path> envFiles = Arrays.asList(envFile, envExample, 
            projectPath.resolve(".env.local"), 
            projectPath.resolve(".env.development"));
        
        for (Path env : envFiles) {
            if (Files.exists(env)) {
                try {
                    List<String> lines = Files.readAllLines(env);
                    Map<String, String> envVars = new HashMap<>();
                    
                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        
                        int equals = line.indexOf('=');
                        if (equals > 0) {
                            String key = line.substring(0, equals).trim();
                            String value = line.substring(equals + 1).trim();
                            
                            // Remove quotes if present
                            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                                (value.startsWith("'") && value.endsWith("'"))) {
                                value = value.substring(1, value.length() - 1);
                            }
                            
                            envVars.put(key, value);
                            
                            // Detect database from environment variable names and values
                            detectFromEnvironmentVariable(key, value, detected);
                        }
                    }
                    
                    // Add environment variables to analysis
                    analysis.getEnvironmentVariables().putAll(envVars);
                    
                } catch (IOException e) {
                    log.debug("Failed to read env file: {}", env, e);
                }
            }
        }
    }
    
    private void detectFromEnvironmentVariable(String key, String value, Map<String, DatabaseInfo> detected) {
        String upperKey = key.toUpperCase();
        
        for (Map.Entry<String, DatabasePattern> entry : DATABASE_PATTERNS.entrySet()) {
            DatabasePattern pattern = entry.getValue();
            
            // Check if key matches database patterns
            for (String connPattern : pattern.connectionPatterns) {
                if (upperKey.contains(connPattern.toUpperCase().replace(":", "").replace("_", ""))) {
                    addDetection(detected, entry.getKey(), pattern, 0.7, value);
                    break;
                }
            }
            
            // Check if value contains connection string
            for (String connPattern : pattern.connectionPatterns) {
                if (value.toLowerCase().contains(connPattern.toLowerCase())) {
                    addDetection(detected, entry.getKey(), pattern, 0.9, value);
                    
                    // Try to extract connection details
                    extractConnectionDetails(value, detected.get(entry.getKey()));
                    break;
                }
            }
        }
    }
    
    private void extractConnectionDetails(String connectionString, DatabaseInfo dbInfo) {
        if (dbInfo == null) return;
        
        // Generic connection string pattern
        Pattern connPattern = Pattern.compile(
            "(?:([a-zA-Z0-9]+)://)?(?:([^:@]+)(?::([^@]+))?@)?([^:/]+)(?::(\\d+))?(?:/([^?]+))?"
        );
        
        Matcher matcher = connPattern.matcher(connectionString);
        if (matcher.find()) {
            String protocol = matcher.group(1);
            String username = matcher.group(2);
            String password = matcher.group(3);
            String host = matcher.group(4);
            String port = matcher.group(5);
            String database = matcher.group(6);
            
            if (host != null) dbInfo.host = host;
            if (port != null) dbInfo.port = Integer.parseInt(port);
            if (database != null) dbInfo.databaseName = database;
            if (username != null) dbInfo.username = username;
            
            // Increase confidence if we found connection details
            dbInfo.confidence = Math.min(1.0, dbInfo.confidence + 0.2);
        }
    }
    
    private void detectDatabaseConnections(String content, Map<String, DatabaseInfo> detected) {
        for (Map.Entry<String, DatabasePattern> entry : DATABASE_PATTERNS.entrySet()) {
            DatabasePattern pattern = entry.getValue();
            
            for (String connPattern : pattern.connectionPatterns) {
                if (content.toLowerCase().contains(connPattern.toLowerCase())) {
                    addDetection(detected, entry.getKey(), pattern, 0.8, null);
                    
                    // Try to find the actual connection string
                    Pattern regex = Pattern.compile(
                        connPattern + "[^\\s\"']*",
                        Pattern.CASE_INSENSITIVE
                    );
                    Matcher matcher = regex.matcher(content);
                    if (matcher.find()) {
                        String connStr = matcher.group();
                        extractConnectionDetails(connStr, detected.get(entry.getKey()));
                    }
                }
            }
        }
    }
    
    private void scanSourceCode(Path projectPath, Map<String, DatabaseInfo> detected) {
        try (Stream<Path> paths = Files.walk(projectPath)) {
            paths.filter(Files::isRegularFile)
                .filter(this::isSourceFile)
                .limit(50) // Limit for performance
                .forEach(file -> analyzeSourceFile(file, detected));
        } catch (IOException e) {
            log.debug("Error scanning source code for databases", e);
        }
    }
    
    private boolean isSourceFile(Path file) {
        String fileName = file.getFileName().toString();
        String pathStr = file.toString();
        
        // Skip common non-source directories
        if (pathStr.contains("node_modules") || pathStr.contains(".git") ||
            pathStr.contains("vendor") || pathStr.contains("build") ||
            pathStr.contains("dist") || pathStr.contains("target")) {
            return false;
        }
        
        return fileName.endsWith(".java") || fileName.endsWith(".py") ||
               fileName.endsWith(".js") || fileName.endsWith(".ts") ||
               fileName.endsWith(".go") || fileName.endsWith(".rb") ||
               fileName.endsWith(".php") || fileName.endsWith(".cs") ||
               fileName.endsWith(".yml") || fileName.endsWith(".yaml") ||
               fileName.endsWith(".properties") || fileName.endsWith(".json");
    }
    
    private void analyzeSourceFile(Path file, Map<String, DatabaseInfo> detected) {
        try {
            String content = Files.readString(file);
            detectDatabaseConnections(content, detected);
            
            // Also check for database imports/dependencies in code
            for (Map.Entry<String, DatabasePattern> entry : DATABASE_PATTERNS.entrySet()) {
                DatabasePattern pattern = entry.getValue();
                if (pattern.dependencies != null) {
                    for (String dep : pattern.dependencies) {
                        if (content.contains(dep)) {
                            addDetection(detected, entry.getKey(), pattern, 0.5, null);
                        }
                    }
                }
            }
        } catch (IOException e) {
            // Ignore individual file read errors
        }
    }
    
    private void checkMigrationFiles(Path projectPath, Map<String, DatabaseInfo> detected) {
        List<Path> migrationDirs = Arrays.asList(
            projectPath.resolve("migrations"),
            projectPath.resolve("db/migrations"),
            projectPath.resolve("database/migrations"),
            projectPath.resolve("src/main/resources/db/migration"),
            projectPath.resolve("db/migrate"),
            projectPath.resolve("alembic")
        );
        
        for (Path migrationDir : migrationDirs) {
            if (Files.exists(migrationDir) && Files.isDirectory(migrationDir)) {
                try (Stream<Path> paths = Files.list(migrationDir)) {
                    paths.filter(Files::isRegularFile)
                        .forEach(file -> {
                            String fileName = file.getFileName().toString().toLowerCase();
                            
                            // SQL files strongly indicate traditional SQL databases
                            if (fileName.endsWith(".sql")) {
                                // Check content for specific database syntax
                                try {
                                    String content = Files.readString(file);
                                    if (content.contains("CREATE EXTENSION") || 
                                        content.contains("pg_") ||
                                        content.contains("SERIAL PRIMARY KEY")) {
                                        addDetection(detected, "postgresql", 
                                            DATABASE_PATTERNS.get("postgresql"), 0.8, null);
                                    } else if (content.contains("AUTO_INCREMENT") ||
                                               content.contains("ENGINE=InnoDB")) {
                                        addDetection(detected, "mysql", 
                                            DATABASE_PATTERNS.get("mysql"), 0.8, null);
                                    }
                                } catch (IOException e) {
                                    // Default to PostgreSQL for SQL files
                                    addDetection(detected, "postgresql", 
                                        DATABASE_PATTERNS.get("postgresql"), 0.6, null);
                                }
                            }
                        });
                } catch (IOException e) {
                    log.debug("Error checking migration directory: {}", migrationDir, e);
                }
            }
        }
    }
    
    private void scanDockerCompose(Path projectPath, Map<String, DatabaseInfo> detected) {
        List<String> composeFiles = Arrays.asList(
            "docker-compose.yml", "docker-compose.yaml",
            "compose.yml", "compose.yaml",
            "docker-compose.prod.yml", "docker-compose.dev.yml"
        );
        
        for (String composeFile : composeFiles) {
            Path composePath = projectPath.resolve(composeFile);
            if (Files.exists(composePath)) {
                try {
                    String content = Files.readString(composePath);
                    
                    // Look for database service definitions
                    for (Map.Entry<String, DatabasePattern> entry : DATABASE_PATTERNS.entrySet()) {
                        DatabasePattern pattern = entry.getValue();
                        String dbName = entry.getKey();
                        
                        // Check for official images
                        if (pattern.dockerImage != null && 
                            content.contains(pattern.dockerImage.split(":")[0])) {
                            addDetection(detected, dbName, pattern, 0.9, null);
                            
                            // Try to extract port mapping
                            Pattern portPattern = Pattern.compile(
                                "ports:\\s*\\n\\s*-\\s*[\"']?(\\d+):(\\d+)",
                                Pattern.MULTILINE
                            );
                            Matcher matcher = portPattern.matcher(content);
                            if (matcher.find()) {
                                DatabaseInfo dbInfo = detected.get(dbName);
                                if (dbInfo != null) {
                                    dbInfo.port = Integer.parseInt(matcher.group(1));
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    log.debug("Failed to read docker-compose file: {}", composeFile, e);
                }
            }
        }
    }
    
    private void scanDependencies(Path projectPath, Map<String, DatabaseInfo> detected) {
        // Check package.json
        Path packageJson = projectPath.resolve("package.json");
        if (Files.exists(packageJson)) {
            checkDependencyFile(packageJson, detected);
        }
        
        // Check requirements.txt
        Path requirements = projectPath.resolve("requirements.txt");
        if (Files.exists(requirements)) {
            checkDependencyFile(requirements, detected);
        }
        
        // Check go.mod
        Path goMod = projectPath.resolve("go.mod");
        if (Files.exists(goMod)) {
            checkDependencyFile(goMod, detected);
        }
        
        // Check Gemfile
        Path gemfile = projectPath.resolve("Gemfile");
        if (Files.exists(gemfile)) {
            checkDependencyFile(gemfile, detected);
        }
    }
    
    private void checkDependencyFile(Path depFile, Map<String, DatabaseInfo> detected) {
        try {
            String content = Files.readString(depFile);
            
            for (Map.Entry<String, DatabasePattern> entry : DATABASE_PATTERNS.entrySet()) {
                DatabasePattern pattern = entry.getValue();
                if (pattern.dependencies != null) {
                    for (String dep : pattern.dependencies) {
                        if (content.contains(dep)) {
                            addDetection(detected, entry.getKey(), pattern, 0.6, null);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.debug("Failed to read dependency file: {}", depFile, e);
        }
    }
    
    private void addDetection(Map<String, DatabaseInfo> detected, String key,
                             DatabasePattern pattern, double confidence, String connectionString) {
        detected.compute(key, (k, existing) -> {
            if (existing == null) {
                DatabaseInfo info = new DatabaseInfo(pattern, confidence);
                if (connectionString != null) {
                    info.connectionString = connectionString;
                }
                return info;
            } else {
                existing.increaseConfidence(confidence);
                if (connectionString != null && existing.connectionString == null) {
                    existing.connectionString = connectionString;
                }
                return existing;
            }
        });
    }
    
    private DetectedDatabase createDetectedDatabase(DatabaseInfo info) {
        DatabasePattern pattern = info.pattern;
        
        DetectedDatabase.DatabaseType type = determineType(pattern.name);
        
        DetectedDatabase db = DetectedDatabase.builder()
            .name(pattern.name)
            .type(type)
            .confidence(Math.min(1.0, info.confidence))
            .source(DetectedTechnology.DetectionSource.CONFIG_FILE)
            .defaultPort(pattern.defaultPort)
            .dockerImage(pattern.dockerImage)
            .build();
        
        // Set connection details if available
        if (info.host != null) db.setHost(info.host);
        if (info.port != null) db.setPort(info.port);
        if (info.databaseName != null) db.setDatabaseName(info.databaseName);
        if (info.username != null) db.setUsername(info.username);
        if (info.connectionString != null) db.setConnectionString(info.connectionString);
        
        return db;
    }
    
    private DetectedDatabase.DatabaseType determineType(String name) {
        switch (name.toLowerCase()) {
            case "postgresql":
            case "mysql":
            case "sqlite":
                return DetectedDatabase.DatabaseType.RELATIONAL;
            case "mongodb":
            case "cassandra":
            case "dynamodb":
                return DetectedDatabase.DatabaseType.NOSQL;
            case "redis":
            case "memcached":
                return DetectedDatabase.DatabaseType.CACHE;
            case "elasticsearch":
                return DetectedDatabase.DatabaseType.SEARCH;
            case "rabbitmq":
            case "kafka":
                return DetectedDatabase.DatabaseType.MESSAGE_QUEUE;
            default:
                return DetectedDatabase.DatabaseType.OTHER;
        }
    }
    
    @Override
    public int getPriority() {
        return 85; // Run after framework detection
    }
    
    @Override
    public String getName() {
        return "Database Detector";
    }
    
    private static class DatabasePattern {
        final String name;
        final List<String> connectionPatterns;
        final List<String> dependencies;
        final List<String> schemaFiles;
        final Integer defaultPort;
        final String dockerImage;
        
        DatabasePattern(String name, List<String> connectionPatterns,
                       List<String> dependencies, List<String> schemaFiles,
                       Integer defaultPort, String dockerImage) {
            this.name = name;
            this.connectionPatterns = connectionPatterns;
            this.dependencies = dependencies;
            this.schemaFiles = schemaFiles;
            this.defaultPort = defaultPort;
            this.dockerImage = dockerImage;
        }
    }
    
    private static class DatabaseInfo {
        final DatabasePattern pattern;
        double confidence;
        String host;
        Integer port;
        String databaseName;
        String username;
        String connectionString;
        
        DatabaseInfo(DatabasePattern pattern, double confidence) {
            this.pattern = pattern;
            this.confidence = confidence;
        }
        
        void increaseConfidence(double amount) {
            confidence = Math.min(1.0, confidence + amount * 0.5);
        }
    }
}