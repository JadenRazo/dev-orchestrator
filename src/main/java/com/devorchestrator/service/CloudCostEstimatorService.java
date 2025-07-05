package com.devorchestrator.service;

import com.devorchestrator.entity.*;
import com.devorchestrator.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class CloudCostEstimatorService {
    
    private final CloudMigrationPlanRepository migrationPlanRepository;
    private final CloudCostTrackingRepository costTrackingRepository;
    private final UsageReportRepository usageReportRepository;
    private final ProjectRegistrationRepository projectRepository;
    private final ObjectMapper objectMapper;
    
    // Cloud provider pricing (simplified examples - should be externalized)
    private static final Map<String, InstancePricing> AWS_PRICING = new HashMap<>();
    private static final Map<String, InstancePricing> AZURE_PRICING = new HashMap<>();
    private static final Map<String, InstancePricing> GCP_PRICING = new HashMap<>();
    private static final Map<String, InstancePricing> DIGITAL_OCEAN_PRICING = new HashMap<>();
    
    static {
        // AWS EC2 Pricing (example)
        AWS_PRICING.put("t3.micro", new InstancePricing(1, 1, new BigDecimal("0.0104")));
        AWS_PRICING.put("t3.small", new InstancePricing(2, 2, new BigDecimal("0.0208")));
        AWS_PRICING.put("t3.medium", new InstancePricing(2, 4, new BigDecimal("0.0416")));
        AWS_PRICING.put("t3.large", new InstancePricing(2, 8, new BigDecimal("0.0832")));
        AWS_PRICING.put("m5.large", new InstancePricing(2, 8, new BigDecimal("0.096")));
        AWS_PRICING.put("m5.xlarge", new InstancePricing(4, 16, new BigDecimal("0.192")));
        
        // Azure VM Pricing (example)
        AZURE_PRICING.put("B1s", new InstancePricing(1, 1, new BigDecimal("0.012")));
        AZURE_PRICING.put("B2s", new InstancePricing(2, 4, new BigDecimal("0.0416")));
        AZURE_PRICING.put("D2s_v3", new InstancePricing(2, 8, new BigDecimal("0.096")));
        AZURE_PRICING.put("D4s_v3", new InstancePricing(4, 16, new BigDecimal("0.192")));
        
        // GCP Compute Engine Pricing (example)
        GCP_PRICING.put("e2-micro", new InstancePricing(1, 1, new BigDecimal("0.0084")));
        GCP_PRICING.put("e2-small", new InstancePricing(2, 2, new BigDecimal("0.0168")));
        GCP_PRICING.put("e2-medium", new InstancePricing(2, 4, new BigDecimal("0.0336")));
        GCP_PRICING.put("n2-standard-2", new InstancePricing(2, 8, new BigDecimal("0.0971")));
        
        // Digital Ocean Droplet Pricing (example)
        DIGITAL_OCEAN_PRICING.put("basic-1vcpu-1gb", new InstancePricing(1, 1, new BigDecimal("0.00744")));
        DIGITAL_OCEAN_PRICING.put("basic-2vcpu-2gb", new InstancePricing(2, 2, new BigDecimal("0.01488")));
        DIGITAL_OCEAN_PRICING.put("general-2vcpu-8gb", new InstancePricing(2, 8, new BigDecimal("0.0893")));
    }
    
    public CloudCostEstimatorService(CloudMigrationPlanRepository migrationPlanRepository,
                                   CloudCostTrackingRepository costTrackingRepository,
                                   UsageReportRepository usageReportRepository,
                                   ProjectRegistrationRepository projectRepository,
                                   ObjectMapper objectMapper) {
        this.migrationPlanRepository = migrationPlanRepository;
        this.costTrackingRepository = costTrackingRepository;
        this.usageReportRepository = usageReportRepository;
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Estimate cloud migration costs for a project
     */
    public CloudMigrationPlan estimateMigrationCosts(String projectId, 
                                                   InfrastructureProvider targetProvider,
                                                   CloudMigrationPlan.MigrationStrategy strategy) {
        log.info("Estimating migration costs for project {} to {}", projectId, targetProvider);
        
        ProjectRegistration project = projectRepository.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        
        CloudMigrationPlan plan = CloudMigrationPlan.builder()
            .id(UUID.randomUUID().toString())
            .project(project)
            .targetProvider(targetProvider)
            .migrationStrategy(strategy)
            .status(CloudMigrationPlan.MigrationStatus.DRAFT)
            .validationStatus(CloudMigrationPlan.ValidationStatus.NOT_STARTED)
            .build();
        
        // Analyze recent usage to determine resource requirements
        ResourceRequirements requirements = analyzeResourceRequirements(projectId);
        
        // Map to cloud instance types
        Map<String, InstanceRecommendation> recommendations = 
            mapToCloudInstances(requirements, targetProvider);
        
        // Calculate monthly costs
        BigDecimal monthlyCost = calculateMonthlyCost(recommendations);
        plan.setEstimatedMonthlyCost(monthlyCost);
        
        // Generate cost comparison across providers
        Map<String, BigDecimal> costComparison = generateCostComparison(requirements);
        
        // Create migration steps
        List<MigrationStep> migrationSteps = generateMigrationSteps(project, strategy);
        
        try {
            plan.setInstanceTypeRecommendationsJson(
                objectMapper.writeValueAsString(recommendations));
            plan.setCostComparisonJson(
                objectMapper.writeValueAsString(costComparison));
            plan.setMigrationStepsJson(
                objectMapper.writeValueAsString(migrationSteps));
            plan.setTotalSteps(migrationSteps.size());
        } catch (JsonProcessingException e) {
            log.error("Error serializing migration plan data", e);
        }
        
        return migrationPlanRepository.save(plan);
    }
    
    /**
     * Analyze resource requirements based on usage history
     */
    private ResourceRequirements analyzeResourceRequirements(String projectId) {
        // Get recent usage reports
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(30);
        
        List<UsageReport> reports = usageReportRepository
            .findByProjectIdAndPeriodStartBetweenOrderByPeriodStartDesc(
                projectId, startTime, endTime, null).getContent();
        
        ResourceRequirements requirements = new ResourceRequirements();
        
        if (!reports.isEmpty()) {
            // Calculate average and peak requirements
            BigDecimal avgCpu = reports.stream()
                .map(UsageReport::getPeakCpuPercent)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(reports.size()), 2, RoundingMode.HALF_UP);
            
            Integer maxMemory = reports.stream()
                .map(UsageReport::getPeakMemoryMb)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(2048);
            
            // Convert CPU percentage to cores (assuming current system has 4 cores)
            requirements.setCpuCores(avgCpu.multiply(new BigDecimal("0.04"))
                .max(new BigDecimal("1")));
            requirements.setMemoryGb(new BigDecimal(maxMemory)
                .divide(new BigDecimal("1024"), 2, RoundingMode.HALF_UP)
                .max(new BigDecimal("2")));
            
            // Network and storage from reports
            BigDecimal avgNetwork = reports.stream()
                .map(UsageReport::getTotalNetworkGb)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(reports.size()), 2, RoundingMode.HALF_UP);
            
            requirements.setNetworkGbPerMonth(avgNetwork.multiply(new BigDecimal("30")));
            requirements.setStorageGb(new BigDecimal("50")); // Default storage
        } else {
            // Default requirements if no usage data
            requirements.setCpuCores(new BigDecimal("2"));
            requirements.setMemoryGb(new BigDecimal("4"));
            requirements.setNetworkGbPerMonth(new BigDecimal("100"));
            requirements.setStorageGb(new BigDecimal("50"));
        }
        
        return requirements;
    }
    
    /**
     * Map requirements to cloud instance types
     */
    private Map<String, InstanceRecommendation> mapToCloudInstances(
            ResourceRequirements requirements, InfrastructureProvider provider) {
        
        Map<String, InstanceRecommendation> recommendations = new HashMap<>();
        Map<String, InstancePricing> pricing = getPricingForProvider(provider);
        
        // Find suitable instances
        List<Map.Entry<String, InstancePricing>> suitableInstances = pricing.entrySet().stream()
            .filter(entry -> {
                InstancePricing instance = entry.getValue();
                return instance.getCpuCores() >= requirements.getCpuCores().intValue() &&
                       instance.getMemoryGb() >= requirements.getMemoryGb().intValue();
            })
            .sorted(Comparator.comparing(entry -> entry.getValue().getHourlyPrice()))
            .collect(Collectors.toList());
        
        if (!suitableInstances.isEmpty()) {
            // Recommend cheapest suitable instance
            Map.Entry<String, InstancePricing> cheapest = suitableInstances.get(0);
            recommendations.put("compute", new InstanceRecommendation(
                cheapest.getKey(),
                cheapest.getValue(),
                1, // instance count
                "Primary compute instance"
            ));
            
            // Add recommendations for other services
            recommendations.put("storage", new InstanceRecommendation(
                "standard-storage",
                null,
                requirements.getStorageGb().intValue(),
                "Block storage"
            ));
            
            recommendations.put("network", new InstanceRecommendation(
                "standard-network",
                null,
                requirements.getNetworkGbPerMonth().intValue(),
                "Network transfer"
            ));
        }
        
        return recommendations;
    }
    
    /**
     * Calculate total monthly cost
     */
    private BigDecimal calculateMonthlyCost(Map<String, InstanceRecommendation> recommendations) {
        BigDecimal totalCost = BigDecimal.ZERO;
        
        for (InstanceRecommendation recommendation : recommendations.values()) {
            if (recommendation.getPricing() != null) {
                // Compute costs (hourly * 730 hours per month)
                BigDecimal monthlyCost = recommendation.getPricing().getHourlyPrice()
                    .multiply(new BigDecimal("730"))
                    .multiply(new BigDecimal(recommendation.getCount()));
                totalCost = totalCost.add(monthlyCost);
            } else {
                // Storage and network costs (simplified)
                if (recommendation.getType().contains("storage")) {
                    BigDecimal storageCost = new BigDecimal(recommendation.getCount())
                        .multiply(new BigDecimal("0.10")); // $0.10 per GB
                    totalCost = totalCost.add(storageCost);
                } else if (recommendation.getType().contains("network")) {
                    BigDecimal networkCost = new BigDecimal(recommendation.getCount())
                        .multiply(new BigDecimal("0.12")); // $0.12 per GB
                    totalCost = totalCost.add(networkCost);
                }
            }
        }
        
        return totalCost.setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * Generate cost comparison across all providers
     */
    private Map<String, BigDecimal> generateCostComparison(ResourceRequirements requirements) {
        Map<String, BigDecimal> comparison = new HashMap<>();
        
        for (InfrastructureProvider provider : InfrastructureProvider.values()) {
            if (provider == InfrastructureProvider.DOCKER) continue;
            
            Map<String, InstanceRecommendation> recommendations = 
                mapToCloudInstances(requirements, provider);
            BigDecimal cost = calculateMonthlyCost(recommendations);
            comparison.put(provider.name(), cost);
        }
        
        return comparison;
    }
    
    /**
     * Generate migration steps based on strategy
     */
    private List<MigrationStep> generateMigrationSteps(ProjectRegistration project,
                                                      CloudMigrationPlan.MigrationStrategy strategy) {
        List<MigrationStep> steps = new ArrayList<>();
        
        // Common steps for all strategies
        steps.add(new MigrationStep(1, "Backup current data and configurations", 
            "Create comprehensive backup of all project data"));
        steps.add(new MigrationStep(2, "Provision cloud infrastructure", 
            "Create cloud resources using Terraform"));
        steps.add(new MigrationStep(3, "Configure networking and security", 
            "Set up VPC, security groups, and firewall rules"));
        
        // Strategy-specific steps
        switch (strategy) {
            case LIFT_AND_SHIFT:
                steps.add(new MigrationStep(4, "Create container images", 
                    "Build Docker images from current configuration"));
                steps.add(new MigrationStep(5, "Deploy containers to cloud", 
                    "Deploy containerized application to cloud platform"));
                break;
                
            case REPLATFORM:
                steps.add(new MigrationStep(4, "Optimize for cloud services", 
                    "Replace self-managed services with cloud-managed alternatives"));
                steps.add(new MigrationStep(5, "Configure auto-scaling", 
                    "Set up auto-scaling policies for dynamic workloads"));
                steps.add(new MigrationStep(6, "Deploy optimized application", 
                    "Deploy application with cloud optimizations"));
                break;
                
            case REFACTOR:
                steps.add(new MigrationStep(4, "Refactor to microservices", 
                    "Break down monolithic components into microservices"));
                steps.add(new MigrationStep(5, "Implement cloud-native patterns", 
                    "Add circuit breakers, service discovery, etc."));
                steps.add(new MigrationStep(6, "Deploy to Kubernetes", 
                    "Deploy refactored application to managed Kubernetes"));
                break;
        }
        
        // Common final steps
        steps.add(new MigrationStep(steps.size() + 1, "Migrate data", 
            "Transfer data to cloud storage/databases"));
        steps.add(new MigrationStep(steps.size() + 2, "Update DNS and routing", 
            "Point traffic to new cloud deployment"));
        steps.add(new MigrationStep(steps.size() + 3, "Validate deployment", 
            "Run comprehensive tests and health checks"));
        steps.add(new MigrationStep(steps.size() + 4, "Monitor and optimize", 
            "Monitor performance and optimize costs"));
        
        return steps;
    }
    
    /**
     * Track actual cloud costs
     */
    public void trackCloudCost(String projectId, InfrastructureProvider provider,
                             CloudCostTracking.ServiceType serviceType, 
                             BigDecimal cost, LocalDateTime periodStart, 
                             LocalDateTime periodEnd) {
        ProjectRegistration project = projectRepository.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        
        CloudCostTracking tracking = CloudCostTracking.builder()
            .project(project)
            .provider(provider)
            .serviceType(serviceType)
            .costAmount(cost)
            .periodStart(periodStart)
            .periodEnd(periodEnd)
            .build();
        
        costTrackingRepository.save(tracking);
    }
    
    /**
     * Get cost trends for a project
     */
    public Map<String, Object> getCostTrends(String projectId, int months) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMonths(months);
        
        Map<String, Object> trends = new HashMap<>();
        
        // Total costs by period
        BigDecimal totalCost = costTrackingRepository.getTotalCostForPeriod(
            projectId, startTime, endTime);
        trends.put("totalCost", totalCost != null ? totalCost : BigDecimal.ZERO);
        
        // Cost breakdown by service type
        List<Object[]> serviceBreakdown = costTrackingRepository
            .getCostBreakdownByServiceType(projectId, startTime, endTime);
        trends.put("serviceBreakdown", serviceBreakdown);
        
        // Cost breakdown by provider
        List<Object[]> providerBreakdown = costTrackingRepository
            .getCostBreakdownByProvider(projectId, startTime, endTime);
        trends.put("providerBreakdown", providerBreakdown);
        
        // Monthly trend
        List<Object[]> monthlyTrend = costTrackingRepository
            .getMonthlyCostTrend(projectId);
        trends.put("monthlyTrend", monthlyTrend);
        
        return trends;
    }
    
    /**
     * Get pricing for provider
     */
    private Map<String, InstancePricing> getPricingForProvider(InfrastructureProvider provider) {
        switch (provider) {
            case AWS:
                return AWS_PRICING;
            case AZURE:
                return AZURE_PRICING;
            case GCP:
                return GCP_PRICING;
            case DIGITAL_OCEAN:
                return DIGITAL_OCEAN_PRICING;
            default:
                return new HashMap<>();
        }
    }
    
    // Helper classes
    private static class ResourceRequirements {
        private BigDecimal cpuCores;
        private BigDecimal memoryGb;
        private BigDecimal storageGb;
        private BigDecimal networkGbPerMonth;
        
        // Getters and setters
        public BigDecimal getCpuCores() { return cpuCores; }
        public void setCpuCores(BigDecimal cpuCores) { this.cpuCores = cpuCores; }
        public BigDecimal getMemoryGb() { return memoryGb; }
        public void setMemoryGb(BigDecimal memoryGb) { this.memoryGb = memoryGb; }
        public BigDecimal getStorageGb() { return storageGb; }
        public void setStorageGb(BigDecimal storageGb) { this.storageGb = storageGb; }
        public BigDecimal getNetworkGbPerMonth() { return networkGbPerMonth; }
        public void setNetworkGbPerMonth(BigDecimal networkGbPerMonth) { 
            this.networkGbPerMonth = networkGbPerMonth; 
        }
    }
    
    private static class InstancePricing {
        private final int cpuCores;
        private final int memoryGb;
        private final BigDecimal hourlyPrice;
        
        public InstancePricing(int cpuCores, int memoryGb, BigDecimal hourlyPrice) {
            this.cpuCores = cpuCores;
            this.memoryGb = memoryGb;
            this.hourlyPrice = hourlyPrice;
        }
        
        public int getCpuCores() { return cpuCores; }
        public int getMemoryGb() { return memoryGb; }
        public BigDecimal getHourlyPrice() { return hourlyPrice; }
    }
    
    private static class InstanceRecommendation {
        private final String type;
        private final InstancePricing pricing;
        private final int count;
        private final String description;
        
        public InstanceRecommendation(String type, InstancePricing pricing, 
                                    int count, String description) {
            this.type = type;
            this.pricing = pricing;
            this.count = count;
            this.description = description;
        }
        
        public String getType() { return type; }
        public InstancePricing getPricing() { return pricing; }
        public int getCount() { return count; }
        public String getDescription() { return description; }
    }
    
    private static class MigrationStep {
        private final int stepNumber;
        private final String title;
        private final String description;
        
        public MigrationStep(int stepNumber, String title, String description) {
            this.stepNumber = stepNumber;
            this.title = title;
            this.description = description;
        }
        
        public int getStepNumber() { return stepNumber; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
    }
}