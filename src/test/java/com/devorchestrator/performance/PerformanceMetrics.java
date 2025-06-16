package com.devorchestrator.performance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PerformanceMetrics {
    
    private static final Map<String, Long> metrics = new ConcurrentHashMap<>();
    private static final Map<String, Double> doubleMetrics = new ConcurrentHashMap<>();
    
    private PerformanceMetrics() {
        // Utility class
    }
    
    public static void recordMetric(String name, long value) {
        metrics.put(name, value);
        System.out.printf("Recorded metric '%s': %d%n", name, value);
    }
    
    public static void recordMetric(String name, double value) {
        doubleMetrics.put(name, value);
        System.out.printf("Recorded metric '%s': %.2f%n", name, value);
    }
    
    public static long getMetric(String name) {
        return metrics.getOrDefault(name, 0L);
    }
    
    public static double getDoubleMetric(String name) {
        return doubleMetrics.getOrDefault(name, 0.0);
    }
    
    public static void printAllMetrics() {
        System.out.println("\n=== Performance Metrics Summary ===");
        metrics.forEach((name, value) -> 
            System.out.printf("%s: %d%n", name, value));
        doubleMetrics.forEach((name, value) -> 
            System.out.printf("%s: %.2f%n", name, value));
        System.out.println("===================================\n");
    }
    
    public static void clearMetrics() {
        metrics.clear();
        doubleMetrics.clear();
    }
    
    public static Map<String, Object> getAllMetrics() {
        Map<String, Object> allMetrics = new ConcurrentHashMap<>();
        allMetrics.putAll(metrics);
        allMetrics.putAll(doubleMetrics);
        return allMetrics;
    }
    
    public static double calculateImprovementPercentage(String baselineMetric, String optimizedMetric) {
        long baseline = getMetric(baselineMetric);
        long optimized = getMetric(optimizedMetric);
        
        if (baseline == 0) {
            return 0.0;
        }
        
        return ((double) (baseline - optimized) / baseline) * 100.0;
    }
    
    public static void recordThroughput(String operationName, int operationsCount, long totalTimeMs) {
        double operationsPerSecond = (double) operationsCount / (totalTimeMs / 1000.0);
        recordMetric(operationName + "_throughput_ops_per_sec", operationsPerSecond);
        recordMetric(operationName + "_total_time_ms", totalTimeMs);
        recordMetric(operationName + "_operations_count", operationsCount);
    }
}