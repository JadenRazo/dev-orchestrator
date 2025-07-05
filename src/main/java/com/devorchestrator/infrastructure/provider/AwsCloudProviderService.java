package com.devorchestrator.infrastructure.provider;

import com.devorchestrator.entity.Environment;
import com.devorchestrator.entity.InfrastructureProvider;
import com.devorchestrator.exception.DevOrchestratorException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class AwsCloudProviderService implements CloudProviderService {

    @Override
    public InfrastructureProvider getProvider() {
        return InfrastructureProvider.AWS;
    }

    @Override
    public void preProvision(Environment environment) {
        log.info("Pre-provisioning AWS resources for environment: {}", environment.getId());
        
        // Validate AWS credentials are configured
        validateAwsCredentials();
        
        // Validate region is specified
        if (environment.getTemplate().getCloudRegion() == null) {
            throw new DevOrchestratorException("AWS region must be specified for cloud deployment");
        }
        
        // Set default tags for all resources
        Map<String, String> defaultTags = new HashMap<>();
        defaultTags.put("Environment", environment.getId());
        defaultTags.put("ManagedBy", "DevOrchestrator");
        defaultTags.put("Owner", environment.getOwner().getUsername());
        defaultTags.put("CreatedAt", String.valueOf(System.currentTimeMillis()));
        
        environment.addCloudResource("default_tags", convertMapToString(defaultTags));
    }

    @Override
    public void postProvision(Environment environment) {
        log.info("Post-provisioning AWS resources for environment: {}", environment.getId());
        
        // Verify resources were created successfully
        Map<String, String> resources = environment.getCloudResourceIds();
        
        if (resources.containsKey("instance_id")) {
            log.info("EC2 instance created: {}", resources.get("instance_id"));
        }
        
        if (resources.containsKey("vpc_id")) {
            log.info("VPC created: {}", resources.get("vpc_id"));
        }
        
        // Configure security group rules if needed
        if (resources.containsKey("security_group_id")) {
            configureSecurityGroup(resources.get("security_group_id"), environment);
        }
    }

    @Override
    public void preDestroy(Environment environment) {
        log.info("Pre-destroy AWS resources for environment: {}", environment.getId());
        
        // Take snapshots of any EBS volumes if configured
        if (environment.getCloudResourceIds().containsKey("ebs_volume_id")) {
            log.info("Creating EBS snapshot before destruction");
            // In a real implementation, would use AWS SDK to create snapshot
        }
    }

    @Override
    public void postDestroy(Environment environment) {
        log.info("Post-destroy cleanup for AWS environment: {}", environment.getId());
        
        // Verify all resources were destroyed
        // Clean up any remaining artifacts (logs, snapshots, etc.)
    }

    @Override
    public CompletableFuture<Void> startResources(Environment environment) {
        return CompletableFuture.runAsync(() -> {
            log.info("Starting AWS resources for environment: {}", environment.getId());
            
            Map<String, String> resources = environment.getCloudResourceIds();
            
            // Start EC2 instances
            if (resources.containsKey("instance_id")) {
                startEc2Instance(resources.get("instance_id"));
            }
            
            // Start RDS instances if any
            if (resources.containsKey("rds_instance_id")) {
                startRdsInstance(resources.get("rds_instance_id"));
            }
        });
    }

    @Override
    public CompletableFuture<Void> stopResources(Environment environment) {
        return CompletableFuture.runAsync(() -> {
            log.info("Stopping AWS resources for environment: {}", environment.getId());
            
            Map<String, String> resources = environment.getCloudResourceIds();
            
            // Stop EC2 instances
            if (resources.containsKey("instance_id")) {
                stopEc2Instance(resources.get("instance_id"));
            }
            
            // Stop RDS instances if any
            if (resources.containsKey("rds_instance_id")) {
                stopRdsInstance(resources.get("rds_instance_id"));
            }
        });
    }

    @Override
    public Map<String, Object> getResourceDetails(Environment environment) {
        Map<String, Object> details = new HashMap<>();
        Map<String, String> resources = environment.getCloudResourceIds();
        
        details.put("provider", "AWS");
        details.put("region", environment.getTemplate().getCloudRegion());
        details.put("resources", resources);
        
        // Add instance status
        if (resources.containsKey("instance_id")) {
            details.put("instance_status", getEc2InstanceStatus(resources.get("instance_id")));
        }
        
        // Add public IP if available
        if (resources.containsKey("public_ip")) {
            details.put("public_ip", resources.get("public_ip"));
        }
        
        // Add estimated costs
        details.put("estimated_hourly_cost", calculateEstimatedCost(environment));
        
        return details;
    }

    @Override
    public boolean validateTemplate(String templateContent) {
        // Basic validation of Terraform template for AWS
        if (templateContent == null || templateContent.isEmpty()) {
            return false;
        }
        
        // Check for required AWS provider block
        if (!templateContent.contains("provider \"aws\"")) {
            log.warn("Terraform template missing AWS provider block");
            return false;
        }
        
        // Check for basic resource definitions
        boolean hasResources = templateContent.contains("resource \"aws_") || 
                              templateContent.contains("module \"");
        
        return hasResources;
    }

    @Override
    public Map<String, String> getDefaultVariables() {
        Map<String, String> defaults = new HashMap<>();
        
        defaults.put("instance_type", "t3.micro");
        defaults.put("ami_id", "ami-0c55b159cbfafe1f0"); // Amazon Linux 2
        defaults.put("key_pair_name", "dev-orchestrator-key");
        defaults.put("vpc_cidr", "10.0.0.0/16");
        defaults.put("subnet_cidr", "10.0.1.0/24");
        defaults.put("enable_monitoring", "false");
        defaults.put("enable_public_ip", "true");
        
        return defaults;
    }

    @Override
    public String generateTerraformTemplate(Map<String, Object> specifications) {
        StringBuilder template = new StringBuilder();
        
        // Provider configuration
        template.append("provider \"aws\" {\n");
        template.append("  region = var.aws_region\n");
        template.append("}\n\n");
        
        // Variables
        template.append("variable \"aws_region\" {\n");
        template.append("  description = \"AWS region\"\n");
        template.append("  type        = string\n");
        template.append("}\n\n");
        
        template.append("variable \"environment_name\" {\n");
        template.append("  description = \"Environment name\"\n");
        template.append("  type        = string\n");
        template.append("}\n\n");
        
        // VPC
        template.append("resource \"aws_vpc\" \"main\" {\n");
        template.append("  cidr_block = \"10.0.0.0/16\"\n");
        template.append("  enable_dns_hostnames = true\n");
        template.append("  enable_dns_support = true\n");
        template.append("  tags = {\n");
        template.append("    Name = \"${var.environment_name}-vpc\"\n");
        template.append("  }\n");
        template.append("}\n\n");
        
        // Add more resources based on specifications
        if (specifications.containsKey("instance_type")) {
            template.append(generateEc2Instance(specifications));
        }
        
        // Outputs
        template.append("output \"vpc_id\" {\n");
        template.append("  value = aws_vpc.main.id\n");
        template.append("}\n");
        
        return template.toString();
    }

    private void validateAwsCredentials() {
        // Check for AWS credentials in environment
        String accessKey = System.getenv("AWS_ACCESS_KEY_ID");
        String secretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        
        if (accessKey == null || secretKey == null) {
            throw new DevOrchestratorException("AWS credentials not configured. Please set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY");
        }
    }

    private void configureSecurityGroup(String securityGroupId, Environment environment) {
        log.info("Configuring security group {} for environment {}", securityGroupId, environment.getId());
        // In real implementation, would use AWS SDK to configure security group rules
    }

    private void startEc2Instance(String instanceId) {
        log.info("Starting EC2 instance: {}", instanceId);
        // In real implementation, would use AWS SDK to start instance
    }

    private void stopEc2Instance(String instanceId) {
        log.info("Stopping EC2 instance: {}", instanceId);
        // In real implementation, would use AWS SDK to stop instance
    }

    private void startRdsInstance(String instanceId) {
        log.info("Starting RDS instance: {}", instanceId);
        // In real implementation, would use AWS SDK to start RDS instance
    }

    private void stopRdsInstance(String instanceId) {
        log.info("Stopping RDS instance: {}", instanceId);
        // In real implementation, would use AWS SDK to stop RDS instance
    }

    private String getEc2InstanceStatus(String instanceId) {
        // In real implementation, would query AWS API for instance status
        return "running";
    }

    private double calculateEstimatedCost(Environment environment) {
        // Basic cost calculation based on instance type
        Map<String, Double> instanceCosts = Map.of(
            "t3.micro", 0.0104,
            "t3.small", 0.0208,
            "t3.medium", 0.0416,
            "m5.large", 0.096,
            "m5.xlarge", 0.192
        );
        
        String instanceType = environment.getCloudResourceIds().getOrDefault("instance_type", "t3.micro");
        return instanceCosts.getOrDefault(instanceType, 0.0104);
    }

    private String convertMapToString(Map<String, String> map) {
        return map.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .reduce((a, b) -> a + "," + b)
            .orElse("");
    }

    private String generateEc2Instance(Map<String, Object> specifications) {
        StringBuilder ec2 = new StringBuilder();
        
        ec2.append("resource \"aws_instance\" \"main\" {\n");
        ec2.append("  ami           = var.ami_id\n");
        ec2.append("  instance_type = var.instance_type\n");
        ec2.append("  subnet_id     = aws_subnet.main.id\n");
        ec2.append("  vpc_security_group_ids = [aws_security_group.main.id]\n");
        ec2.append("  tags = {\n");
        ec2.append("    Name = \"${var.environment_name}-instance\"\n");
        ec2.append("  }\n");
        ec2.append("}\n\n");
        
        return ec2.toString();
    }
}