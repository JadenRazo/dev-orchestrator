package com.devorchestrator.integration;

import com.devorchestrator.DevEnvironmentOrchestratorApplication;
import com.devorchestrator.entity.Environment;
import com.devorchestrator.entity.EnvironmentStatus;
import com.devorchestrator.entity.EnvironmentTemplate;
import com.devorchestrator.entity.User;
import com.devorchestrator.entity.UserRole;
import com.devorchestrator.repository.EnvironmentRepository;
import com.devorchestrator.repository.EnvironmentTemplateRepository;
import com.devorchestrator.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = DevEnvironmentOrchestratorApplication.class)
@AutoConfigureWebMvc
@Testcontainers
@Transactional
class EnvironmentIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EnvironmentTemplateRepository templateRepository;

    @Autowired
    private EnvironmentRepository environmentRepository;

    private User testUser;
    private EnvironmentTemplate testTemplate;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .username("testuser")
            .email("test@example.com")
            .role(UserRole.USER)
            .maxEnvironments(5)
            .build();
        testUser = userRepository.save(testUser);

        testTemplate = EnvironmentTemplate.builder()
            .id("web-dev-template")
            .name("Web Development Template")
            .description("Full-stack web development environment")
            .cpuLimit(2.0)
            .memoryLimitMb(4096)
            .dockerComposeContent("version: '3'\nservices:\n  web:\n    image: nginx:alpine")
            .isPublic(true)
            .build();
        testTemplate = templateRepository.save(testTemplate);
    }

    @Test
    @DisplayName("Should get all templates without authentication")
    void shouldGetTemplates_WithoutAuth() throws Exception {
        mockMvc.perform(get("/v1/templates"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].id").value("web-dev-template"))
            .andExpect(jsonPath("$[0].name").value("Web Development Template"));
    }

    @Test
    @DisplayName("Should require authentication for protected endpoints")
    void shouldRequireAuth_ForProtectedEndpoints() throws Exception {
        Map<String, Object> createRequest = new HashMap<>();
        createRequest.put("templateId", "web-dev-template");
        createRequest.put("name", "Test Environment");

        mockMvc.perform(post("/v1/environments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should create environment with valid authentication")
    @WithMockUser(username = "testuser", roles = "USER")
    void shouldCreateEnvironment_WithAuth() throws Exception {
        Map<String, Object> createRequest = new HashMap<>();
        createRequest.put("templateId", "web-dev-template");
        createRequest.put("name", "Test Environment");

        mockMvc.perform(post("/v1/environments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.name").value("Test Environment"))
            .andExpect(jsonPath("$.templateId").value("web-dev-template"));

        // Verify environment was created in database
        assertThat(environmentRepository.count()).isEqualTo(1);
        Environment created = environmentRepository.findAll().get(0);
        assertThat(created.getName()).isEqualTo("Test Environment");
        assertThat(created.getTemplate().getId()).isEqualTo("web-dev-template");
    }

    @Test
    @DisplayName("Should list user environments with authentication")
    @WithMockUser(username = "testuser", roles = "USER")
    void shouldListUserEnvironments_WithAuth() throws Exception {
        // Create test environment
        Environment testEnv = Environment.builder()
            .id("test-env-123")
            .name("Test Environment")
            .template(testTemplate)
            .owner(testUser)
            .status(EnvironmentStatus.RUNNING)
            .build();
        environmentRepository.save(testEnv);

        mockMvc.perform(get("/v1/environments"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content[0].id").value("test-env-123"))
            .andExpect(jsonPath("$.content[0].name").value("Test Environment"))
            .andExpect(jsonPath("$.content[0].status").value("RUNNING"));
    }

    @Test
    @DisplayName("Should return 404 for non-existent environment")
    @WithMockUser(username = "testuser", roles = "USER")
    void shouldReturn404_ForNonExistentEnvironment() throws Exception {
        mockMvc.perform(get("/v1/environments/non-existent-id"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should require admin role for template creation")
    @WithMockUser(username = "testuser", roles = "USER")
    void shouldRequireAdminRole_ForTemplateCreation() throws Exception {
        Map<String, Object> templateRequest = new HashMap<>();
        templateRequest.put("id", "new-template");
        templateRequest.put("name", "New Template");
        templateRequest.put("dockerImage", "ubuntu:22.04");

        mockMvc.perform(post("/v1/templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(templateRequest)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should allow admin to create templates")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldAllowAdmin_ToCreateTemplates() throws Exception {
        Map<String, Object> templateRequest = new HashMap<>();
        templateRequest.put("id", "new-template");
        templateRequest.put("name", "New Template");
        templateRequest.put("description", "A new template");
        templateRequest.put("dockerImage", "ubuntu:22.04");
        templateRequest.put("cpuLimit", 1.0);
        templateRequest.put("memoryLimit", 2048);

        mockMvc.perform(post("/v1/templates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(templateRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("new-template"))
            .andExpect(jsonPath("$.name").value("New Template"));
    }

    @Test
    @DisplayName("Should validate template existence for environment creation")
    @WithMockUser(username = "testuser", roles = "USER")
    void shouldValidateTemplate_ForEnvironmentCreation() throws Exception {
        Map<String, Object> createRequest = new HashMap<>();
        createRequest.put("templateId", "non-existent-template");
        createRequest.put("name", "Test Environment");

        mockMvc.perform(post("/v1/environments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("TEMPLATE_NOT_FOUND"));
    }
}