package com.devorchestrator.infrastructure.terraform;

import com.devorchestrator.exception.DevOrchestratorException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class TerraformExecutor {

    private static final int TERRAFORM_TIMEOUT_MINUTES = 30;
    private final ObjectMapper objectMapper;

    public TerraformExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void init(Path workspacePath) throws IOException, InterruptedException {
        log.info("Initializing Terraform in workspace: {}", workspacePath);
        executeCommand(workspacePath, "terraform", "init", "-no-color");
    }

    public String plan(Path workspacePath, String variablesJson) throws IOException, InterruptedException {
        log.info("Running Terraform plan in workspace: {}", workspacePath);
        
        List<String> command = new ArrayList<>();
        command.add("terraform");
        command.add("plan");
        command.add("-no-color");
        command.add("-out=terraform.plan");
        
        if (variablesJson != null && !variablesJson.isEmpty()) {
            Map<String, Object> variables = parseVariables(variablesJson);
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                command.add("-var");
                command.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        
        return executeCommand(workspacePath, command.toArray(new String[0]));
    }

    public Map<String, String> apply(Path workspacePath) throws IOException, InterruptedException {
        log.info("Applying Terraform configuration in workspace: {}", workspacePath);
        executeCommand(workspacePath, "terraform", "apply", "-no-color", "-auto-approve", "terraform.plan");
        
        return getOutputs(workspacePath);
    }

    public void destroy(Path workspacePath) throws IOException, InterruptedException {
        log.info("Destroying Terraform resources in workspace: {}", workspacePath);
        executeCommand(workspacePath, "terraform", "destroy", "-no-color", "-auto-approve");
    }

    public Map<String, String> getOutputs(Path workspacePath) throws IOException, InterruptedException {
        log.info("Getting Terraform outputs from workspace: {}", workspacePath);
        String outputJson = executeCommand(workspacePath, "terraform", "output", "-json");
        
        Map<String, String> outputs = new HashMap<>();
        try {
            Map<String, Map<String, Object>> rawOutputs = objectMapper.readValue(outputJson, Map.class);
            for (Map.Entry<String, Map<String, Object>> entry : rawOutputs.entrySet()) {
                Object value = entry.getValue().get("value");
                outputs.put(entry.getKey(), value != null ? value.toString() : "");
            }
        } catch (Exception e) {
            log.warn("Failed to parse Terraform outputs", e);
        }
        
        return outputs;
    }

    public boolean validate(Path workspacePath) throws IOException, InterruptedException {
        log.info("Validating Terraform configuration in workspace: {}", workspacePath);
        try {
            executeCommand(workspacePath, "terraform", "validate", "-no-color");
            return true;
        } catch (Exception e) {
            log.warn("Terraform validation failed", e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseVariables(String variablesJson) {
        try {
            return objectMapper.readValue(variablesJson, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse Terraform variables JSON, using empty map", e);
            return new HashMap<>();
        }
    }

    private String executeCommand(Path workingDirectory, String... command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.redirectErrorStream(true);
        
        Map<String, String> env = processBuilder.environment();
        env.put("TF_IN_AUTOMATION", "true");
        env.put("TF_CLI_ARGS", "-no-color");
        
        log.debug("Executing command: {} in directory: {}", String.join(" ", command), workingDirectory);
        
        Process process = processBuilder.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.debug("Terraform output: {}", line);
            }
        }
        
        boolean finished = process.waitFor(TERRAFORM_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new DevOrchestratorException("Terraform command timed out after " + TERRAFORM_TIMEOUT_MINUTES + " minutes");
        }
        
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new DevOrchestratorException("Terraform command failed with exit code " + exitCode + ": " + output);
        }
        
        return output.toString();
    }
}