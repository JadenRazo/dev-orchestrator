package com.devorchestrator.infrastructure.state;

import com.devorchestrator.exception.DevOrchestratorException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class TerraformStateService {

    private final Path stateStoragePath;

    public TerraformStateService(@Value("${terraform.state.storage-path:./terraform-states}") String stateStoragePath) {
        this.stateStoragePath = Paths.get(stateStoragePath);
        createStateStorageDirectory();
    }

    public String saveState(String workspaceId, Path workspacePath) {
        String stateId = UUID.randomUUID().toString();
        Path statePath = stateStoragePath.resolve(stateId + ".zip");
        
        try {
            log.info("Saving Terraform state for workspace {} to {}", workspaceId, statePath);
            
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(statePath))) {
                Files.walkFileTree(workspacePath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (shouldIncludeInState(file)) {
                            Path relativePath = workspacePath.relativize(file);
                            ZipEntry entry = new ZipEntry(relativePath.toString());
                            zos.putNextEntry(entry);
                            Files.copy(file, zos);
                            zos.closeEntry();
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            
            log.info("Successfully saved Terraform state with ID: {}", stateId);
            return stateId;
            
        } catch (IOException e) {
            log.error("Failed to save Terraform state", e);
            throw new DevOrchestratorException("Failed to save Terraform state", e);
        }
    }

    public Path restoreWorkspace(String stateId) {
        Path statePath = stateStoragePath.resolve(stateId + ".zip");
        if (!Files.exists(statePath)) {
            throw new DevOrchestratorException("Terraform state not found: " + stateId);
        }
        
        Path workspacePath = Paths.get(System.getProperty("java.io.tmpdir"), "terraform-restore-" + UUID.randomUUID());
        
        try {
            log.info("Restoring Terraform state {} to workspace {}", stateId, workspacePath);
            Files.createDirectories(workspacePath);
            
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(statePath))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path targetPath = workspacePath.resolve(entry.getName());
                    
                    if (entry.isDirectory()) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    
                    zis.closeEntry();
                }
            }
            
            log.info("Successfully restored Terraform state to workspace: {}", workspacePath);
            return workspacePath;
            
        } catch (IOException e) {
            log.error("Failed to restore Terraform state", e);
            throw new DevOrchestratorException("Failed to restore Terraform state", e);
        }
    }

    public void deleteState(String stateId) {
        Path statePath = stateStoragePath.resolve(stateId + ".zip");
        
        try {
            if (Files.exists(statePath)) {
                Files.delete(statePath);
                log.info("Deleted Terraform state: {}", stateId);
            }
        } catch (IOException e) {
            log.error("Failed to delete Terraform state", e);
            throw new DevOrchestratorException("Failed to delete Terraform state", e);
        }
    }

    public boolean stateExists(String stateId) {
        return Files.exists(stateStoragePath.resolve(stateId + ".zip"));
    }

    public long getStateSize(String stateId) {
        Path statePath = stateStoragePath.resolve(stateId + ".zip");
        try {
            return Files.size(statePath);
        } catch (IOException e) {
            return 0;
        }
    }

    public void cleanupOldStates(int daysToKeep) {
        log.info("Cleaning up Terraform states older than {} days", daysToKeep);
        
        try (Stream<Path> files = Files.list(stateStoragePath)) {
            long cutoffTime = System.currentTimeMillis() - (daysToKeep * 24L * 60L * 60L * 1000L);
            
            files.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".zip"))
                .filter(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toMillis() < cutoffTime;
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        log.info("Deleted old Terraform state: {}", path.getFileName());
                    } catch (IOException e) {
                        log.warn("Failed to delete old state: {}", path, e);
                    }
                });
        } catch (IOException e) {
            log.error("Failed to cleanup old Terraform states", e);
        }
    }

    private boolean shouldIncludeInState(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.endsWith(".tfstate") ||
               fileName.endsWith(".tfstate.backup") ||
               fileName.equals("terraform.tfvars") ||
               fileName.equals("main.tf") ||
               fileName.equals(".terraform.lock.hcl") ||
               file.toString().contains(".terraform/");
    }

    private void createStateStorageDirectory() {
        try {
            Files.createDirectories(stateStoragePath);
            log.info("Created Terraform state storage directory: {}", stateStoragePath);
        } catch (IOException e) {
            throw new DevOrchestratorException("Failed to create state storage directory", e);
        }
    }
}