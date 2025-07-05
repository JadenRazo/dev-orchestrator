package com.devorchestrator.analyzer.detector;

import com.devorchestrator.analyzer.model.DetectedLanguage;
import com.devorchestrator.analyzer.model.DetectedTechnology;
import com.devorchestrator.analyzer.model.ProjectAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LanguageDetectorService implements TechnologyDetector {
    
    private static final Map<String, LanguageInfo> LANGUAGE_PATTERNS = new HashMap<>();
    
    static {
        // Java
        LANGUAGE_PATTERNS.put("java", new LanguageInfo("Java", 
            Arrays.asList(".java"), 
            Arrays.asList("pom.xml", "build.gradle", "build.gradle.kts"),
            "maven|gradle", "openjdk:17-alpine"));
        
        // Python
        LANGUAGE_PATTERNS.put("python", new LanguageInfo("Python",
            Arrays.asList(".py", ".pyw"),
            Arrays.asList("requirements.txt", "setup.py", "Pipfile", "pyproject.toml"),
            "pip|pipenv|poetry", "python:3.11-alpine"));
        
        // JavaScript/TypeScript
        LANGUAGE_PATTERNS.put("javascript", new LanguageInfo("JavaScript",
            Arrays.asList(".js", ".mjs", ".cjs"),
            Arrays.asList("package.json", "yarn.lock", "package-lock.json"),
            "npm|yarn|pnpm", "node:18-alpine"));
        
        LANGUAGE_PATTERNS.put("typescript", new LanguageInfo("TypeScript",
            Arrays.asList(".ts", ".tsx"),
            Arrays.asList("tsconfig.json", "package.json"),
            "npm|yarn|pnpm", "node:18-alpine"));
        
        // C#
        LANGUAGE_PATTERNS.put("csharp", new LanguageInfo("C#",
            Arrays.asList(".cs", ".csx"),
            Arrays.asList(".csproj", ".sln", "project.json"),
            "dotnet|nuget", "mcr.microsoft.com/dotnet/sdk:7.0"));
        
        // Go
        LANGUAGE_PATTERNS.put("go", new LanguageInfo("Go",
            Arrays.asList(".go"),
            Arrays.asList("go.mod", "go.sum"),
            "go mod", "golang:1.21-alpine"));
        
        // Rust
        LANGUAGE_PATTERNS.put("rust", new LanguageInfo("Rust",
            Arrays.asList(".rs"),
            Arrays.asList("Cargo.toml", "Cargo.lock"),
            "cargo", "rust:1.75-alpine"));
        
        // Ruby
        LANGUAGE_PATTERNS.put("ruby", new LanguageInfo("Ruby",
            Arrays.asList(".rb", ".rake"),
            Arrays.asList("Gemfile", "Gemfile.lock", ".ruby-version"),
            "gem|bundler", "ruby:3.2-alpine"));
        
        // PHP
        LANGUAGE_PATTERNS.put("php", new LanguageInfo("PHP",
            Arrays.asList(".php", ".phtml"),
            Arrays.asList("composer.json", "composer.lock"),
            "composer", "php:8.2-fpm-alpine"));
        
        // Swift
        LANGUAGE_PATTERNS.put("swift", new LanguageInfo("Swift",
            Arrays.asList(".swift"),
            Arrays.asList("Package.swift", ".swift-version"),
            "swift package", "swift:5.9"));
        
        // Kotlin
        LANGUAGE_PATTERNS.put("kotlin", new LanguageInfo("Kotlin",
            Arrays.asList(".kt", ".kts"),
            Arrays.asList("build.gradle.kts", "settings.gradle.kts"),
            "gradle", "openjdk:17-alpine"));
        
        // Scala
        LANGUAGE_PATTERNS.put("scala", new LanguageInfo("Scala",
            Arrays.asList(".scala", ".sc"),
            Arrays.asList("build.sbt", "project/build.properties"),
            "sbt", "hseeberger/scala-sbt:17.0.2_1.8.2_3.2.2"));
        
        // Dart
        LANGUAGE_PATTERNS.put("dart", new LanguageInfo("Dart",
            Arrays.asList(".dart"),
            Arrays.asList("pubspec.yaml", "pubspec.lock"),
            "pub|dart", "dart:stable"));
        
        // Julia
        LANGUAGE_PATTERNS.put("julia", new LanguageInfo("Julia",
            Arrays.asList(".jl"),
            Arrays.asList("Project.toml", "Manifest.toml"),
            "pkg", "julia:1.9"));
        
        // R
        LANGUAGE_PATTERNS.put("r", new LanguageInfo("R",
            Arrays.asList(".r", ".R", ".Rmd"),
            Arrays.asList("DESCRIPTION", ".Rprofile"),
            "renv|packrat", "r-base:4.3.2"));
        
        // Haskell
        LANGUAGE_PATTERNS.put("haskell", new LanguageInfo("Haskell",
            Arrays.asList(".hs", ".lhs"),
            Arrays.asList("stack.yaml", "cabal.project", "*.cabal"),
            "stack|cabal", "haskell:9.6"));
        
        // Clojure
        LANGUAGE_PATTERNS.put("clojure", new LanguageInfo("Clojure",
            Arrays.asList(".clj", ".cljs", ".cljc"),
            Arrays.asList("project.clj", "deps.edn"),
            "lein|clj", "clojure:temurin-21-lein"));
        
        // Elixir
        LANGUAGE_PATTERNS.put("elixir", new LanguageInfo("Elixir",
            Arrays.asList(".ex", ".exs"),
            Arrays.asList("mix.exs", "mix.lock"),
            "mix", "elixir:1.15-alpine"));
        
        // Erlang
        LANGUAGE_PATTERNS.put("erlang", new LanguageInfo("Erlang",
            Arrays.asList(".erl", ".hrl"),
            Arrays.asList("rebar.config", "erlang.mk"),
            "rebar3", "erlang:26-alpine"));
        
        // Lua
        LANGUAGE_PATTERNS.put("lua", new LanguageInfo("Lua",
            Arrays.asList(".lua"),
            Arrays.asList("rockspec", ".luacheckrc"),
            "luarocks", "nickblah/lua:5.4-alpine"));
        
        // Perl
        LANGUAGE_PATTERNS.put("perl", new LanguageInfo("Perl",
            Arrays.asList(".pl", ".pm", ".pod"),
            Arrays.asList("cpanfile", "Makefile.PL"),
            "cpan|cpanm", "perl:5.38"));
        
        // C/C++
        LANGUAGE_PATTERNS.put("c", new LanguageInfo("C",
            Arrays.asList(".c", ".h"),
            Arrays.asList("Makefile", "CMakeLists.txt", "configure"),
            "make|cmake", "gcc:13"));
        
        LANGUAGE_PATTERNS.put("cpp", new LanguageInfo("C++",
            Arrays.asList(".cpp", ".cc", ".cxx", ".hpp", ".hxx"),
            Arrays.asList("CMakeLists.txt", "Makefile", "conanfile.txt"),
            "cmake|make|conan", "gcc:13"));
        
        // Zig
        LANGUAGE_PATTERNS.put("zig", new LanguageInfo("Zig",
            Arrays.asList(".zig"),
            Arrays.asList("build.zig", "zig.mod"),
            "zig", "euantorano/zig:0.11.0"));
        
        // Nim
        LANGUAGE_PATTERNS.put("nim", new LanguageInfo("Nim",
            Arrays.asList(".nim", ".nims"),
            Arrays.asList("*.nimble", "nim.cfg"),
            "nimble", "nimlang/nim:2.0.0"));
        
        // F#
        LANGUAGE_PATTERNS.put("fsharp", new LanguageInfo("F#",
            Arrays.asList(".fs", ".fsi", ".fsx"),
            Arrays.asList(".fsproj", "paket.dependencies"),
            "dotnet|paket", "mcr.microsoft.com/dotnet/sdk:7.0"));
        
        // OCaml
        LANGUAGE_PATTERNS.put("ocaml", new LanguageInfo("OCaml",
            Arrays.asList(".ml", ".mli"),
            Arrays.asList("dune-project", "_oasis", ".opam"),
            "opam|dune", "ocaml/opam:alpine"));
        
        // MATLAB
        LANGUAGE_PATTERNS.put("matlab", new LanguageInfo("MATLAB",
            Arrays.asList(".m", ".mat"),
            Arrays.asList("matlabroot.txt"),
            "matlab", "mathworks/matlab:r2023b"));
        
        // Fortran
        LANGUAGE_PATTERNS.put("fortran", new LanguageInfo("Fortran",
            Arrays.asList(".f90", ".f95", ".f03", ".f", ".for"),
            Arrays.asList("Makefile", "CMakeLists.txt"),
            "gfortran", "gcc:13"));
        
        // COBOL
        LANGUAGE_PATTERNS.put("cobol", new LanguageInfo("COBOL",
            Arrays.asList(".cob", ".cbl", ".cpy"),
            Arrays.asList("Makefile"),
            "cobc", "ubuntu:22.04"));
        
        // Assembly
        LANGUAGE_PATTERNS.put("assembly", new LanguageInfo("Assembly",
            Arrays.asList(".asm", ".s", ".S"),
            Arrays.asList("Makefile"),
            "nasm|as", "ubuntu:22.04"));
    }
    
    @Override
    public void detect(Path projectPath, ProjectAnalysis analysis) {
        log.info("Starting language detection for project: {}", projectPath);
        
        Map<String, LanguageStats> languageStats = new HashMap<>();
        
        try {
            // Walk through all files in the project
            Files.walkFileTree(projectPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String fileName = file.getFileName().toString();
                    
                    // Skip hidden files and common non-code directories
                    if (shouldSkipFile(file, projectPath)) {
                        return FileVisitResult.CONTINUE;
                    }
                    
                    // Check each language pattern
                    for (Map.Entry<String, LanguageInfo> entry : LANGUAGE_PATTERNS.entrySet()) {
                        String langKey = entry.getKey();
                        LanguageInfo langInfo = entry.getValue();
                        
                        // Check file extensions
                        for (String ext : langInfo.extensions) {
                            if (fileName.endsWith(ext)) {
                                languageStats.computeIfAbsent(langKey, k -> new LanguageStats(langInfo))
                                    .incrementFileCount();
                                
                                // Count lines of code
                                try {
                                    long lines = Files.lines(file).count();
                                    languageStats.get(langKey).addLines(lines);
                                } catch (IOException e) {
                                    // Ignore line count errors
                                }
                            }
                        }
                        
                        // Check config files
                        for (String configFile : langInfo.configFiles) {
                            if (matchesPattern(fileName, configFile)) {
                                languageStats.computeIfAbsent(langKey, k -> new LanguageStats(langInfo))
                                    .incrementConfidence(0.2);
                            }
                        }
                    }
                    
                    return FileVisitResult.CONTINUE;
                }
            });
            
            // Check for specific version files
            detectVersions(projectPath, languageStats);
            
            // Convert stats to detected languages
            List<DetectedLanguage> detectedLanguages = languageStats.entrySet().stream()
                .filter(e -> e.getValue().getConfidence() > 0.1)
                .sorted((a, b) -> Double.compare(b.getValue().getConfidence(), a.getValue().getConfidence()))
                .map(e -> createDetectedLanguage(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
            
            // Mark primary language
            if (!detectedLanguages.isEmpty()) {
                detectedLanguages.get(0).setIsPrimary(true);
            }
            
            // Add all detected languages to analysis
            detectedLanguages.forEach(analysis::addLanguage);
            
            log.info("Detected {} languages in project", detectedLanguages.size());
            
        } catch (IOException e) {
            log.error("Error during language detection", e);
            analysis.addWarning("Language Detection", "Failed to complete language detection: " + e.getMessage());
        }
    }
    
    private void detectVersions(Path projectPath, Map<String, LanguageStats> languageStats) {
        // Python version
        Path pythonVersion = projectPath.resolve(".python-version");
        if (Files.exists(pythonVersion)) {
            try {
                String version = Files.readString(pythonVersion).trim();
                if (languageStats.containsKey("python")) {
                    languageStats.get("python").setVersion(version);
                }
            } catch (IOException e) {
                // Ignore
            }
        }
        
        // Node version
        Path nvmrc = projectPath.resolve(".nvmrc");
        if (Files.exists(nvmrc)) {
            try {
                String version = Files.readString(nvmrc).trim();
                if (languageStats.containsKey("javascript") || languageStats.containsKey("typescript")) {
                    if (languageStats.containsKey("javascript")) {
                        languageStats.get("javascript").setVersion(version);
                    }
                    if (languageStats.containsKey("typescript")) {
                        languageStats.get("typescript").setVersion(version);
                    }
                }
            } catch (IOException e) {
                // Ignore
            }
        }
        
        // Ruby version
        Path rubyVersion = projectPath.resolve(".ruby-version");
        if (Files.exists(rubyVersion)) {
            try {
                String version = Files.readString(rubyVersion).trim();
                if (languageStats.containsKey("ruby")) {
                    languageStats.get("ruby").setVersion(version);
                }
            } catch (IOException e) {
                // Ignore
            }
        }
    }
    
    private DetectedLanguage createDetectedLanguage(String key, LanguageStats stats) {
        LanguageInfo info = stats.info;
        
        return DetectedLanguage.builder()
            .name(info.name)
            .version(stats.version)
            .confidence(stats.getConfidence())
            .source(DetectedTechnology.DetectionSource.FILE_EXTENSION)
            .runtime(info.dockerImage)
            .fileExtensions(new ArrayList<>(info.extensions))
            .filesCount(stats.fileCount)
            .totalLinesOfCode(stats.totalLines)
            .packageManager(info.packageManager)
            .build();
    }
    
    private boolean shouldSkipFile(Path file, Path projectRoot) {
        String pathStr = projectRoot.relativize(file).toString();
        
        // Skip version control
        if (pathStr.contains(".git/") || pathStr.contains(".svn/")) {
            return true;
        }
        
        // Skip dependencies
        if (pathStr.contains("node_modules/") || 
            pathStr.contains("vendor/") ||
            pathStr.contains(".venv/") ||
            pathStr.contains("target/") ||
            pathStr.contains("dist/") ||
            pathStr.contains("build/")) {
            return true;
        }
        
        // Skip IDE files
        if (pathStr.contains(".idea/") || 
            pathStr.contains(".vscode/") ||
            pathStr.contains(".vs/")) {
            return true;
        }
        
        return false;
    }
    
    private boolean matchesPattern(String fileName, String pattern) {
        if (pattern.contains("*")) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return fileName.matches(regex);
        }
        return fileName.equals(pattern);
    }
    
    @Override
    public int getPriority() {
        return 100; // High priority - run first
    }
    
    @Override
    public String getName() {
        return "Language Detector";
    }
    
    private static class LanguageInfo {
        final String name;
        final List<String> extensions;
        final List<String> configFiles;
        final String packageManager;
        final String dockerImage;
        
        LanguageInfo(String name, List<String> extensions, List<String> configFiles, 
                    String packageManager, String dockerImage) {
            this.name = name;
            this.extensions = extensions;
            this.configFiles = configFiles;
            this.packageManager = packageManager;
            this.dockerImage = dockerImage;
        }
    }
    
    private static class LanguageStats {
        final LanguageInfo info;
        int fileCount = 0;
        long totalLines = 0;
        double confidence = 0.0;
        String version = null;
        
        LanguageStats(LanguageInfo info) {
            this.info = info;
        }
        
        void incrementFileCount() {
            fileCount++;
            confidence = Math.min(1.0, confidence + 0.1);
        }
        
        void addLines(long lines) {
            totalLines += lines;
        }
        
        void incrementConfidence(double amount) {
            confidence = Math.min(1.0, confidence + amount);
        }
        
        void setVersion(String version) {
            this.version = version;
        }
        
        double getConfidence() {
            // Boost confidence based on file count
            if (fileCount > 10) {
                confidence = Math.min(1.0, confidence + 0.2);
            }
            return confidence;
        }
    }
}