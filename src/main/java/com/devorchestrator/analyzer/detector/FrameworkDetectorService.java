package com.devorchestrator.analyzer.detector;

import com.devorchestrator.analyzer.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FrameworkDetectorService implements TechnologyDetector {
    
    private static final Map<String, FrameworkPattern> FRAMEWORK_PATTERNS = new HashMap<>();
    
    static {
        initializeWebFrameworks();
        initializeMobileFrameworks();
        initializeDesktopFrameworks();
        initializeBotFrameworks();
        initializeMicroserviceFrameworks();
    }
    
    private static void initializeWebFrameworks() {
        // React and React ecosystem
        FRAMEWORK_PATTERNS.put("react", new FrameworkPattern("React", "JavaScript", "web",
            Arrays.asList("package.json"),
            Arrays.asList("react", "react-dom"),
            Arrays.asList("import.*React.*from.*['\"]react['\"]", "from.*['\"]react['\"]"),
            "node:18-alpine"));
        
        FRAMEWORK_PATTERNS.put("nextjs", new FrameworkPattern("Next.js", "JavaScript", "web",
            Arrays.asList("next.config.js", "next.config.mjs", "pages", "app"),
            Arrays.asList("next", "react", "react-dom"),
            Arrays.asList("from.*['\"]next/.*['\"]"),
            "node:18-alpine"));
        
        FRAMEWORK_PATTERNS.put("gatsby", new FrameworkPattern("Gatsby", "JavaScript", "web",
            Arrays.asList("gatsby-config.js", "gatsby-node.js"),
            Arrays.asList("gatsby"),
            null,
            "node:18-alpine"));
        
        // Vue ecosystem
        FRAMEWORK_PATTERNS.put("vue", new FrameworkPattern("Vue.js", "JavaScript", "web",
            Arrays.asList("vue.config.js", "nuxt.config.js"),
            Arrays.asList("vue", "@vue/cli"),
            Arrays.asList("from.*['\"]vue['\"]", "import.*Vue.*from"),
            "node:18-alpine"));
        
        // Angular
        FRAMEWORK_PATTERNS.put("angular", new FrameworkPattern("Angular", "TypeScript", "web",
            Arrays.asList("angular.json", ".angular"),
            Arrays.asList("@angular/core", "@angular/cli"),
            Arrays.asList("from.*['\"]@angular"),
            "node:18-alpine"));
        
        // Python web frameworks
        FRAMEWORK_PATTERNS.put("django", new FrameworkPattern("Django", "Python", "web",
            Arrays.asList("manage.py", "settings.py", "wsgi.py", "asgi.py"),
            Arrays.asList("django"),
            Arrays.asList("from django", "import django", "django.core.wsgi"),
            "python:3.11-alpine"));
        
        FRAMEWORK_PATTERNS.put("flask", new FrameworkPattern("Flask", "Python", "web",
            Arrays.asList("app.py", "application.py"),
            Arrays.asList("flask"),
            Arrays.asList("from flask import", "import flask"),
            "python:3.11-alpine"));
        
        FRAMEWORK_PATTERNS.put("fastapi", new FrameworkPattern("FastAPI", "Python", "web",
            null,
            Arrays.asList("fastapi", "uvicorn"),
            Arrays.asList("from fastapi import", "import fastapi"),
            "python:3.11-alpine"));
        
        // Go web frameworks
        FRAMEWORK_PATTERNS.put("gin", new FrameworkPattern("Gin", "Go", "web",
            null,
            Arrays.asList("github.com/gin-gonic/gin"),
            Arrays.asList("gin\\.Default\\(\\)", "gin\\.New\\(\\)", "github.com/gin-gonic/gin"),
            "golang:1.21-alpine"));
        
        FRAMEWORK_PATTERNS.put("echo", new FrameworkPattern("Echo", "Go", "web",
            null,
            Arrays.asList("github.com/labstack/echo"),
            Arrays.asList("echo\\.New\\(\\)", "github.com/labstack/echo"),
            "golang:1.21-alpine"));
        
        FRAMEWORK_PATTERNS.put("fiber", new FrameworkPattern("Fiber", "Go", "web",
            null,
            Arrays.asList("github.com/gofiber/fiber"),
            Arrays.asList("fiber\\.New\\(\\)", "github.com/gofiber/fiber"),
            "golang:1.21-alpine"));
        
        FRAMEWORK_PATTERNS.put("chi", new FrameworkPattern("Chi", "Go", "web",
            null,
            Arrays.asList("github.com/go-chi/chi"),
            Arrays.asList("chi\\.NewRouter\\(\\)", "github.com/go-chi/chi"),
            "golang:1.21-alpine"));
        
        FRAMEWORK_PATTERNS.put("gorilla", new FrameworkPattern("Gorilla Mux", "Go", "web",
            null,
            Arrays.asList("github.com/gorilla/mux"),
            Arrays.asList("mux\\.NewRouter\\(\\)", "github.com/gorilla/mux"),
            "golang:1.21-alpine"));
        
        // Java web frameworks
        FRAMEWORK_PATTERNS.put("spring-boot", new FrameworkPattern("Spring Boot", "Java", "web",
            Arrays.asList("application.properties", "application.yml", "bootstrap.yml"),
            Arrays.asList("spring-boot-starter", "spring-boot-starter-web"),
            Arrays.asList("@SpringBootApplication", "@RestController", "import org.springframework"),
            "openjdk:17-alpine"));
        
        // Ruby frameworks
        FRAMEWORK_PATTERNS.put("rails", new FrameworkPattern("Ruby on Rails", "Ruby", "web",
            Arrays.asList("Gemfile", "config.ru", "Rakefile", "config/routes.rb"),
            Arrays.asList("rails"),
            Arrays.asList("Rails.application", "class.*<.*ActionController"),
            "ruby:3.2-alpine"));
        
        // PHP frameworks
        FRAMEWORK_PATTERNS.put("laravel", new FrameworkPattern("Laravel", "PHP", "web",
            Arrays.asList("artisan", "composer.json"),
            Arrays.asList("laravel/framework"),
            Arrays.asList("namespace App\\\\", "use Illuminate\\\\"),
            "php:8.2-fpm-alpine"));
        
        FRAMEWORK_PATTERNS.put("symfony", new FrameworkPattern("Symfony", "PHP", "web",
            Arrays.asList("symfony.lock", "bin/console"),
            Arrays.asList("symfony/framework-bundle"),
            Arrays.asList("use Symfony\\\\", "Symfony\\\\Component"),
            "php:8.2-fpm-alpine"));
        
        // .NET frameworks
        FRAMEWORK_PATTERNS.put("aspnet-core", new FrameworkPattern("ASP.NET Core", "C#", "web",
            Arrays.asList("appsettings.json", "Program.cs", "Startup.cs"),
            Arrays.asList("Microsoft.AspNetCore"),
            Arrays.asList("WebApplication.CreateBuilder", "IApplicationBuilder", "using Microsoft.AspNetCore"),
            "mcr.microsoft.com/dotnet/aspnet:7.0"));
    }
    
    private static void initializeMobileFrameworks() {
        // React Native
        FRAMEWORK_PATTERNS.put("react-native", new FrameworkPattern("React Native", "JavaScript", "mobile",
            Arrays.asList("metro.config.js", "app.json", "index.js"),
            Arrays.asList("react-native"),
            Arrays.asList("from.*['\"]react-native['\"]", "AppRegistry.registerComponent"),
            null));
        
        // Flutter
        FRAMEWORK_PATTERNS.put("flutter", new FrameworkPattern("Flutter", "Dart", "mobile",
            Arrays.asList("pubspec.yaml", "lib/main.dart", "android", "ios"),
            Arrays.asList("flutter"),
            Arrays.asList("import.*package:flutter", "MaterialApp", "StatelessWidget"),
            null));
        
        // Ionic
        FRAMEWORK_PATTERNS.put("ionic", new FrameworkPattern("Ionic", "TypeScript", "mobile",
            Arrays.asList("ionic.config.json", "capacitor.config.json"),
            Arrays.asList("@ionic/angular", "@ionic/react", "@ionic/vue"),
            Arrays.asList("from.*['\"]@ionic"),
            null));
    }
    
    private static void initializeDesktopFrameworks() {
        // Electron
        FRAMEWORK_PATTERNS.put("electron", new FrameworkPattern("Electron", "JavaScript", "desktop",
            Arrays.asList("electron-builder.json", "main.js", "electron.js"),
            Arrays.asList("electron"),
            Arrays.asList("const.*{.*app.*}.*=.*require\\(['\"]electron['\"]", "from.*['\"]electron['\"]"),
            null));
        
        // Tauri
        FRAMEWORK_PATTERNS.put("tauri", new FrameworkPattern("Tauri", "Rust", "desktop",
            Arrays.asList("tauri.conf.json", "src-tauri"),
            Arrays.asList("tauri"),
            null,
            null));
    }
    
    private static void initializeBotFrameworks() {
        // Discord.py
        FRAMEWORK_PATTERNS.put("discord.py", new FrameworkPattern("Discord.py", "Python", "bot",
            null,
            Arrays.asList("discord.py", "discord"),
            Arrays.asList("import discord", "from discord", "discord.Client", "discord.Bot", "@bot.command", "@client.command"),
            "python:3.11-alpine"));
        
        // Discord.js
        FRAMEWORK_PATTERNS.put("discord.js", new FrameworkPattern("Discord.js", "JavaScript", "bot",
            null,
            Arrays.asList("discord.js"),
            Arrays.asList("require\\(['\"]discord\\.js['\"]", "from.*['\"]discord\\.js['\"]", "new.*Discord\\.Client"),
            "node:18-alpine"));
        
        // Telegraf (Telegram)
        FRAMEWORK_PATTERNS.put("telegraf", new FrameworkPattern("Telegraf", "JavaScript", "bot",
            null,
            Arrays.asList("telegraf"),
            Arrays.asList("require\\(['\"]telegraf['\"]", "from.*['\"]telegraf['\"]", "new.*Telegraf"),
            "node:18-alpine"));
        
        // python-telegram-bot
        FRAMEWORK_PATTERNS.put("python-telegram-bot", new FrameworkPattern("Python Telegram Bot", "Python", "bot",
            null,
            Arrays.asList("python-telegram-bot"),
            Arrays.asList("from telegram", "import telegram", "telegram.Bot", "telegram.ext"),
            "python:3.11-alpine"));
    }
    
    private static void initializeMicroserviceFrameworks() {
        // gRPC
        FRAMEWORK_PATTERNS.put("grpc", new FrameworkPattern("gRPC", null, "microservice",
            Arrays.asList("*.proto", "protobuf"),
            Arrays.asList("grpc", "grpcio", "@grpc/grpc-js", "google.golang.org/grpc"),
            Arrays.asList("import.*grpc", "from.*grpc", "google.golang.org/grpc"),
            null));
        
        // GraphQL
        FRAMEWORK_PATTERNS.put("graphql", new FrameworkPattern("GraphQL", null, "api",
            Arrays.asList("schema.graphql", "*.graphql"),
            Arrays.asList("graphql", "apollo-server", "graphql-yoga", "gqlgen"),
            Arrays.asList("type.*Query.*{", "type.*Mutation.*{", "schema.*{"),
            null));
    }
    
    @Override
    public void detect(Path projectPath, ProjectAnalysis analysis) {
        log.info("Starting framework detection for project: {}", projectPath);
        
        Map<String, FrameworkInfo> detectedFrameworks = new HashMap<>();
        
        // Check package managers files first
        detectFromPackageFiles(projectPath, detectedFrameworks);
        
        // Then scan source code
        scanSourceCode(projectPath, detectedFrameworks);
        
        // Check for specific framework files/directories
        checkFrameworkMarkers(projectPath, detectedFrameworks);
        
        // Convert to DetectedFramework objects
        detectedFrameworks.values().stream()
            .map(this::createDetectedFramework)
            .forEach(analysis::addFramework);
        
        log.info("Detected {} frameworks in project", detectedFrameworks.size());
    }
    
    private void detectFromPackageFiles(Path projectPath, Map<String, FrameworkInfo> detected) {
        // Check package.json
        Path packageJson = projectPath.resolve("package.json");
        if (Files.exists(packageJson)) {
            try {
                String content = Files.readString(packageJson);
                detectFromPackageJson(content, detected);
            } catch (IOException e) {
                log.debug("Failed to read package.json", e);
            }
        }
        
        // Check requirements.txt
        Path requirements = projectPath.resolve("requirements.txt");
        if (Files.exists(requirements)) {
            try {
                List<String> lines = Files.readAllLines(requirements);
                detectFromRequirements(lines, detected);
            } catch (IOException e) {
                log.debug("Failed to read requirements.txt", e);
            }
        }
        
        // Check go.mod
        Path goMod = projectPath.resolve("go.mod");
        if (Files.exists(goMod)) {
            try {
                String content = Files.readString(goMod);
                detectFromGoMod(content, detected);
            } catch (IOException e) {
                log.debug("Failed to read go.mod", e);
            }
        }
        
        // Check pom.xml
        Path pomXml = projectPath.resolve("pom.xml");
        if (Files.exists(pomXml)) {
            try {
                String content = Files.readString(pomXml);
                detectFromPomXml(content, detected);
            } catch (IOException e) {
                log.debug("Failed to read pom.xml", e);
            }
        }
        
        // Check Gemfile
        Path gemfile = projectPath.resolve("Gemfile");
        if (Files.exists(gemfile)) {
            try {
                String content = Files.readString(gemfile);
                detectFromGemfile(content, detected);
            } catch (IOException e) {
                log.debug("Failed to read Gemfile", e);
            }
        }
    }
    
    private void detectFromPackageJson(String content, Map<String, FrameworkInfo> detected) {
        for (Map.Entry<String, FrameworkPattern> entry : FRAMEWORK_PATTERNS.entrySet()) {
            FrameworkPattern pattern = entry.getValue();
            if (pattern.dependencies != null) {
                for (String dep : pattern.dependencies) {
                    if (content.contains("\"" + dep + "\"")) {
                        addDetection(detected, entry.getKey(), pattern, 0.8);
                    }
                }
            }
        }
    }
    
    private void detectFromRequirements(List<String> lines, Map<String, FrameworkInfo> detected) {
        for (String line : lines) {
            line = line.trim().toLowerCase();
            if (line.isEmpty() || line.startsWith("#")) continue;
            
            for (Map.Entry<String, FrameworkPattern> entry : FRAMEWORK_PATTERNS.entrySet()) {
                FrameworkPattern pattern = entry.getValue();
                if (pattern.dependencies != null && "Python".equals(pattern.language)) {
                    for (String dep : pattern.dependencies) {
                        if (line.startsWith(dep.toLowerCase())) {
                            addDetection(detected, entry.getKey(), pattern, 0.8);
                        }
                    }
                }
            }
        }
    }
    
    private void detectFromGoMod(String content, Map<String, FrameworkInfo> detected) {
        for (Map.Entry<String, FrameworkPattern> entry : FRAMEWORK_PATTERNS.entrySet()) {
            FrameworkPattern pattern = entry.getValue();
            if (pattern.dependencies != null && "Go".equals(pattern.language)) {
                for (String dep : pattern.dependencies) {
                    if (content.contains(dep)) {
                        addDetection(detected, entry.getKey(), pattern, 0.8);
                    }
                }
            }
        }
    }
    
    private void detectFromPomXml(String content, Map<String, FrameworkInfo> detected) {
        if (content.contains("spring-boot-starter")) {
            FrameworkPattern pattern = FRAMEWORK_PATTERNS.get("spring-boot");
            if (pattern != null) {
                addDetection(detected, "spring-boot", pattern, 0.9);
            }
        }
    }
    
    private void detectFromGemfile(String content, Map<String, FrameworkInfo> detected) {
        if (content.contains("gem 'rails'") || content.contains("gem \"rails\"")) {
            FrameworkPattern pattern = FRAMEWORK_PATTERNS.get("rails");
            if (pattern != null) {
                addDetection(detected, "rails", pattern, 0.9);
            }
        }
    }
    
    private void scanSourceCode(Path projectPath, Map<String, FrameworkInfo> detected) {
        try {
            Files.walk(projectPath)
                .filter(Files::isRegularFile)
                .filter(path -> isSourceFile(path))
                .limit(100) // Limit to first 100 source files for performance
                .forEach(file -> analyzeSourceFile(file, detected));
        } catch (IOException e) {
            log.debug("Error scanning source code", e);
        }
    }
    
    private boolean isSourceFile(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.endsWith(".java") || fileName.endsWith(".py") || 
               fileName.endsWith(".js") || fileName.endsWith(".ts") ||
               fileName.endsWith(".go") || fileName.endsWith(".rb") ||
               fileName.endsWith(".php") || fileName.endsWith(".cs") ||
               fileName.endsWith(".dart") || fileName.endsWith(".rs");
    }
    
    private void analyzeSourceFile(Path file, Map<String, FrameworkInfo> detected) {
        try {
            String content = Files.readString(file);
            
            for (Map.Entry<String, FrameworkPattern> entry : FRAMEWORK_PATTERNS.entrySet()) {
                FrameworkPattern pattern = entry.getValue();
                if (pattern.codePatterns != null) {
                    for (String codePattern : pattern.codePatterns) {
                        Pattern regex = Pattern.compile(codePattern, Pattern.CASE_INSENSITIVE);
                        if (regex.matcher(content).find()) {
                            addDetection(detected, entry.getKey(), pattern, 0.6);
                        }
                    }
                }
            }
        } catch (IOException e) {
            // Ignore individual file read errors
        }
    }
    
    private void checkFrameworkMarkers(Path projectPath, Map<String, FrameworkInfo> detected) {
        for (Map.Entry<String, FrameworkPattern> entry : FRAMEWORK_PATTERNS.entrySet()) {
            FrameworkPattern pattern = entry.getValue();
            if (pattern.markerFiles != null) {
                for (String marker : pattern.markerFiles) {
                    Path markerPath = projectPath.resolve(marker);
                    if (Files.exists(markerPath)) {
                        addDetection(detected, entry.getKey(), pattern, 0.7);
                    }
                }
            }
        }
    }
    
    private void addDetection(Map<String, FrameworkInfo> detected, String key, 
                              FrameworkPattern pattern, double confidence) {
        detected.compute(key, (k, existing) -> {
            if (existing == null) {
                return new FrameworkInfo(pattern, confidence);
            } else {
                existing.increaseConfidence(confidence);
                return existing;
            }
        });
    }
    
    private DetectedFramework createDetectedFramework(FrameworkInfo info) {
        FrameworkPattern pattern = info.pattern;
        
        return DetectedFramework.builder()
            .name(pattern.name)
            .language(pattern.language)
            .category(pattern.category)
            .confidence(Math.min(1.0, info.confidence))
            .source(DetectedTechnology.DetectionSource.DEPENDENCY_FILE)
            .runtime(pattern.runtime)
            .build();
    }
    
    @Override
    public int getPriority() {
        return 90; // High priority, run after language detection
    }
    
    @Override
    public String getName() {
        return "Framework Detector";
    }
    
    private static class FrameworkPattern {
        final String name;
        final String language;
        final String category;
        final List<String> markerFiles;
        final List<String> dependencies;
        final List<String> codePatterns;
        final String runtime;
        
        FrameworkPattern(String name, String language, String category,
                        List<String> markerFiles, List<String> dependencies,
                        List<String> codePatterns, String runtime) {
            this.name = name;
            this.language = language;
            this.category = category;
            this.markerFiles = markerFiles;
            this.dependencies = dependencies;
            this.codePatterns = codePatterns;
            this.runtime = runtime;
        }
    }
    
    private static class FrameworkInfo {
        final FrameworkPattern pattern;
        double confidence;
        
        FrameworkInfo(FrameworkPattern pattern, double confidence) {
            this.pattern = pattern;
            this.confidence = confidence;
        }
        
        void increaseConfidence(double amount) {
            confidence = Math.min(1.0, confidence + amount * 0.5); // Diminishing returns
        }
    }
}