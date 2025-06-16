package com.devorchestrator.service;

import com.devorchestrator.exception.PortAllocationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class PortAllocationService {

    @Value("${app.docker.port-range-start:8000}")
    private int portRangeStart;
    
    @Value("${app.docker.port-range-end:9000}")
    private int portRangeEnd;

    private final Set<Integer> allocatedPorts = ConcurrentHashMap.newKeySet();
    private final Set<Integer> reservedPorts = Set.of(22, 80, 443, 3306, 5432, 6379, 8080, 8443);

    public synchronized Integer allocatePort() {
        int maxAttempts = (portRangeEnd - portRangeStart) * 2;
        int attempts = 0;
        
        while (attempts < maxAttempts) {
            int candidatePort = generateRandomPort();
            
            if (isPortAvailable(candidatePort)) {
                allocatedPorts.add(candidatePort);
                log.debug("Allocated port: {}", candidatePort);
                return candidatePort;
            }
            
            attempts++;
        }
        
        throw new PortAllocationException(
            String.format("Unable to allocate port after %d attempts in range %d-%d", 
                maxAttempts, portRangeStart, portRangeEnd)
        );
    }

    public synchronized void releasePort(Integer port) {
        if (port != null && allocatedPorts.remove(port)) {
            log.debug("Released port: {}", port);
        }
    }

    public boolean isPortAvailable(int port) {
        // Check if port is in valid range
        if (port < portRangeStart || port > portRangeEnd) {
            return false;
        }
        
        // Check if port is reserved
        if (reservedPorts.contains(port)) {
            return false;
        }
        
        // Check if port is already allocated
        if (allocatedPorts.contains(port)) {
            return false;
        }
        
        // Check if port is actually available on the system
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public Set<Integer> getAllocatedPorts() {
        return Set.copyOf(allocatedPorts);
    }

    public int getAvailablePortCount() {
        int totalRange = portRangeEnd - portRangeStart + 1;
        return totalRange - allocatedPorts.size() - reservedPorts.size();
    }

    private int generateRandomPort() {
        return ThreadLocalRandom.current().nextInt(portRangeStart, portRangeEnd + 1);
    }

    public void validatePortRange() {
        if (portRangeStart >= portRangeEnd) {
            throw new IllegalArgumentException(
                String.format("Invalid port range: start=%d must be less than end=%d", 
                    portRangeStart, portRangeEnd)
            );
        }
        
        if (portRangeStart < 1024) {
            log.warn("Port range starts below 1024, which may require elevated privileges");
        }
        
        log.info("Port allocation service initialized with range: {}-{}", portRangeStart, portRangeEnd);
    }
}