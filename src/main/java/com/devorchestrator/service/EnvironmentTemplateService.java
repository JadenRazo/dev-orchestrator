package com.devorchestrator.service;

import com.devorchestrator.entity.EnvironmentTemplate;
import com.devorchestrator.exception.TemplateNotFoundException;
import com.devorchestrator.repository.EnvironmentTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@Slf4j
public class EnvironmentTemplateService {

    private final EnvironmentTemplateRepository templateRepository;

    public EnvironmentTemplateService(EnvironmentTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Cacheable(value = "templates", key = "#templateId")
    public EnvironmentTemplate getTemplate(String templateId) {
        return templateRepository.findById(templateId)
            .orElseThrow(() -> new TemplateNotFoundException(templateId));
    }

    @Cacheable(value = "templates", key = "'all'")
    public List<EnvironmentTemplate> getAllTemplates() {
        return templateRepository.findAll();
    }

    public Page<EnvironmentTemplate> getTemplates(Pageable pageable) {
        return templateRepository.findAll(pageable);
    }

    @Cacheable(value = "templates", key = "'active'")
    public List<EnvironmentTemplate> getActiveTemplates() {
        return templateRepository.findByIsPublicTrue();
    }

    public boolean isTemplateValid(String templateId) {
        try {
            EnvironmentTemplate template = getTemplate(templateId);
            return validateTemplateConfiguration(template);
        } catch (TemplateNotFoundException e) {
            return false;
        }
    }

    private boolean validateTemplateConfiguration(EnvironmentTemplate template) {
        try {
            // Validate template has required fields
            if (template.getName() == null || template.getName().trim().isEmpty()) {
                log.warn("Template {} has empty name", template.getId());
                return false;
            }

            if (template.getDockerComposeContent() == null || template.getDockerComposeContent().isEmpty()) {
                log.warn("Template {} has empty configuration", template.getId());
                return false;
            }

            // Validate resource limits are reasonable
            if (template.getCpuLimit() <= 0 || template.getCpuLimit() > 4000) {
                log.warn("Template {} has invalid CPU limit: {}", template.getId(), template.getCpuLimit());
                return false;
            }

            if (template.getMemoryLimit() <= 0 || template.getMemoryLimit() > 16384) {
                log.warn("Template {} has invalid memory limit: {}", template.getId(), template.getMemoryLimit());
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("Error validating template {}: {}", template.getId(), e.getMessage());
            return false;
        }
    }
}