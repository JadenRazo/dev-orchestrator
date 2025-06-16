package com.devorchestrator.service;

import com.devorchestrator.exception.InsufficientResourcesException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class ResourceMonitoringService {

    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    
    @Value("${app.resources.max-cpu-percent}")
    private int maxCpuPercent;
    
    @Value("${app.resources.max-memory-percent}")
    private int maxMemoryPercent;
    
    @Value("${app.resources.max-disk-percent}")
    private int maxDiskPercent;

    private final AtomicLong totalAllocatedMemory = new AtomicLong(0);
    private final AtomicLong totalAllocatedCpu = new AtomicLong(0);

    public ResourceMonitoringService() {
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
    }

    public boolean hasAvailableResources(int cpuMb, long memoryMb) {
        return hasAvailableCpu(cpuMb) && hasAvailableMemory(memoryMb);
    }

    public boolean hasAvailableCpu(int cpuMb) {
        double currentCpuUsage = getCurrentCpuUsage();
        double proposedUsage = currentCpuUsage + (cpuMb / 1000.0); // Convert MB to percentage approximation
        
        boolean available = proposedUsage <= (maxCpuPercent / 100.0);
        
        if (!available) {
            log.warn("Insufficient CPU: current={}%, requested={}MB, max={}%", 
                currentCpuUsage * 100, cpuMb, maxCpuPercent);
        }
        
        return available;
    }

    public boolean hasAvailableMemory(long memoryMb) {
        long availableMemory = getAvailableMemoryMb();
        long currentAllocated = totalAllocatedMemory.get();
        long totalRequired = currentAllocated + memoryMb;
        long maxAllowedMemory = (getTotalSystemMemoryMb() * maxMemoryPercent) / 100;
        
        boolean available = totalRequired <= maxAllowedMemory && memoryMb <= availableMemory;
        
        if (!available) {
            log.warn("Insufficient memory: available={}MB, requested={}MB, allocated={}MB, max={}MB", 
                availableMemory, memoryMb, currentAllocated, maxAllowedMemory);
        }
        
        return available;
    }

    public void allocateResources(int cpuMb, long memoryMb) {
        if (!hasAvailableResources(cpuMb, memoryMb)) {
            throw new InsufficientResourcesException(
                String.format("Cannot allocate resources: CPU=%dMB, Memory=%dMB", cpuMb, memoryMb)
            );
        }
        
        totalAllocatedCpu.addAndGet(cpuMb);
        totalAllocatedMemory.addAndGet(memoryMb);
        
        log.debug("Allocated resources: CPU={}MB, Memory={}MB. Total allocated: CPU={}MB, Memory={}MB",
            cpuMb, memoryMb, totalAllocatedCpu.get(), totalAllocatedMemory.get());
    }

    public void releaseResources(int cpuMb, long memoryMb) {
        totalAllocatedCpu.addAndGet(-cpuMb);
        totalAllocatedMemory.addAndGet(-memoryMb);
        
        // Ensure values don't go negative
        if (totalAllocatedCpu.get() < 0) {
            totalAllocatedCpu.set(0);
        }
        if (totalAllocatedMemory.get() < 0) {
            totalAllocatedMemory.set(0);
        }
        
        log.debug("Released resources: CPU={}MB, Memory={}MB. Total allocated: CPU={}MB, Memory={}MB",
            cpuMb, memoryMb, totalAllocatedCpu.get(), totalAllocatedMemory.get());
    }

    @Scheduled(fixedRateString = "${app.environment.resource-check-interval:30}000")
    public void monitorSystemResources() {
        double cpuUsage = getCurrentCpuUsage();
        long memoryUsage = getCurrentMemoryUsageMb();
        long totalMemory = getTotalSystemMemoryMb();
        double memoryPercentage = (double) memoryUsage / totalMemory * 100;
        
        if (cpuUsage > (maxCpuPercent / 100.0)) {
            log.warn("High CPU usage detected: {:.1f}% (threshold: {}%)", cpuUsage * 100, maxCpuPercent);
        }
        
        if (memoryPercentage > maxMemoryPercent) {
            log.warn("High memory usage detected: {:.1f}% (threshold: {}%)", memoryPercentage, maxMemoryPercent);
        }
        
        log.debug("System resources: CPU={:.1f}%, Memory={:.1f}% ({}/{}MB), Allocated: CPU={}MB, Memory={}MB",
            cpuUsage * 100, memoryPercentage, memoryUsage, totalMemory, 
            totalAllocatedCpu.get(), totalAllocatedMemory.get());
    }

    public double getCurrentCpuUsage() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            return sunOsBean.getProcessCpuLoad();
        }
        return osBean.getSystemLoadAverage() / osBean.getAvailableProcessors();
    }

    public long getCurrentMemoryUsageMb() {
        return (memoryBean.getHeapMemoryUsage().getUsed() + 
                memoryBean.getNonHeapMemoryUsage().getUsed()) / (1024 * 1024);
    }

    public long getAvailableMemoryMb() {
        long totalMemory = getTotalSystemMemoryMb();
        long usedMemory = getCurrentMemoryUsageMb();
        long maxAllowedMemory = (totalMemory * maxMemoryPercent) / 100;
        long allocatedMemory = totalAllocatedMemory.get();
        
        return Math.max(0, Math.min(totalMemory - usedMemory, maxAllowedMemory - allocatedMemory));
    }

    public long getTotalSystemMemoryMb() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            return sunOsBean.getTotalPhysicalMemorySize() / (1024 * 1024);
        }
        // Fallback to heap memory if system memory is not available
        return memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
    }

    public int getAvailableProcessors() {
        return osBean.getAvailableProcessors();
    }

    @Cacheable(value = "system-resources", key = "'current'")
    public ResourceUsageStats getCurrentResourceStats() {
        return ResourceUsageStats.builder()
            .cpuUsagePercent(getCurrentCpuUsage() * 100)
            .memoryUsageMb(getCurrentMemoryUsageMb())
            .totalMemoryMb(getTotalSystemMemoryMb())
            .availableMemoryMb(getAvailableMemoryMb())
            .allocatedCpuMb(totalAllocatedCpu.get())
            .allocatedMemoryMb(totalAllocatedMemory.get())
            .availableProcessors(getAvailableProcessors())
            .build();
    }

    public static class ResourceUsageStats {
        private final double cpuUsagePercent;
        private final long memoryUsageMb;
        private final long totalMemoryMb;
        private final long availableMemoryMb;
        private final long allocatedCpuMb;
        private final long allocatedMemoryMb;
        private final int availableProcessors;

        private ResourceUsageStats(Builder builder) {
            this.cpuUsagePercent = builder.cpuUsagePercent;
            this.memoryUsageMb = builder.memoryUsageMb;
            this.totalMemoryMb = builder.totalMemoryMb;
            this.availableMemoryMb = builder.availableMemoryMb;
            this.allocatedCpuMb = builder.allocatedCpuMb;
            this.allocatedMemoryMb = builder.allocatedMemoryMb;
            this.availableProcessors = builder.availableProcessors;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public double getCpuUsagePercent() { return cpuUsagePercent; }
        public long getMemoryUsageMb() { return memoryUsageMb; }
        public long getTotalMemoryMb() { return totalMemoryMb; }
        public long getAvailableMemoryMb() { return availableMemoryMb; }
        public long getAllocatedCpuMb() { return allocatedCpuMb; }
        public long getAllocatedMemoryMb() { return allocatedMemoryMb; }
        public int getAvailableProcessors() { return availableProcessors; }

        public static class Builder {
            private double cpuUsagePercent;
            private long memoryUsageMb;
            private long totalMemoryMb;
            private long availableMemoryMb;
            private long allocatedCpuMb;
            private long allocatedMemoryMb;
            private int availableProcessors;

            public Builder cpuUsagePercent(double cpuUsagePercent) {
                this.cpuUsagePercent = cpuUsagePercent;
                return this;
            }

            public Builder memoryUsageMb(long memoryUsageMb) {
                this.memoryUsageMb = memoryUsageMb;
                return this;
            }

            public Builder totalMemoryMb(long totalMemoryMb) {
                this.totalMemoryMb = totalMemoryMb;
                return this;
            }

            public Builder availableMemoryMb(long availableMemoryMb) {
                this.availableMemoryMb = availableMemoryMb;
                return this;
            }

            public Builder allocatedCpuMb(long allocatedCpuMb) {
                this.allocatedCpuMb = allocatedCpuMb;
                return this;
            }

            public Builder allocatedMemoryMb(long allocatedMemoryMb) {
                this.allocatedMemoryMb = allocatedMemoryMb;
                return this;
            }

            public Builder availableProcessors(int availableProcessors) {
                this.availableProcessors = availableProcessors;
                return this;
            }

            public ResourceUsageStats build() {
                return new ResourceUsageStats(this);
            }
        }
    }

    public double getCpuUsagePercentage() {
        return getCurrentCpuUsage() * 100;
    }

    public double getMemoryUsagePercentage() {
        long totalMemory = getTotalSystemMemoryMb();
        long usedMemory = getCurrentMemoryUsageMb();
        return totalMemory > 0 ? (double) usedMemory / totalMemory * 100 : 0;
    }

    public long getAvailableMemoryMB() {
        return getAvailableMemoryMb();
    }
}