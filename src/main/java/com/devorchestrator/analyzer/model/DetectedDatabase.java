package com.devorchestrator.analyzer.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class DetectedDatabase extends DetectedTechnology {
    
    private DatabaseType type;
    
    private String connectionString;
    
    private String host;
    
    private Integer port;
    
    private String databaseName;
    
    private String username;
    
    private List<String> ormLibraries = new ArrayList<>();
    
    private List<String> migrationTools = new ArrayList<>();
    
    private Boolean hasExistingData;
    
    private String dockerImage;
    
    private Integer defaultPort;
    
    public enum DatabaseType {
        RELATIONAL,
        DOCUMENT,
        KEY_VALUE,
        GRAPH,
        TIME_SERIES,
        SEARCH,
        WIDE_COLUMN,
        EMBEDDED
    }
    
    public static DetectedDatabase of(String name, DatabaseType type, Double confidence) {
        return DetectedDatabase.builder()
            .name(name)
            .type(type)
            .confidence(confidence)
            .build();
    }
}