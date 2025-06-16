package com.devorchestrator.integration;

import com.devorchestrator.DevEnvironmentOrchestratorApplication;
import com.devorchestrator.entity.User;
import com.devorchestrator.entity.UserRole;
import com.devorchestrator.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = DevEnvironmentOrchestratorApplication.class)
@AutoConfigureWebMvc
@Testcontainers
@Transactional
class UserIntegrationTest {

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

    private User testUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .username("testuser")
            .email("test@example.com")
            .role(UserRole.USER)
            .maxEnvironments(5)
            .build();
        testUser = userRepository.save(testUser);

        adminUser = User.builder()
            .username("admin")
            .email("admin@example.com")
            .role(UserRole.ADMIN)
            .maxEnvironments(10)
            .build();
        adminUser = userRepository.save(adminUser);
    }

    @Test
    @DisplayName("Should require authentication for user endpoints")
    void shouldRequireAuth_ForUserEndpoints() throws Exception {
        mockMvc.perform(get("/v1/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should allow users to view their own profile")
    @WithMockUser(username = "testuser", roles = "USER", authorities = {"sub:1"})
    void shouldAllowUsers_ToViewOwnProfile() throws Exception {
        mockMvc.perform(get("/v1/users/profile"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.username").value("testuser"))
            .andExpect(jsonPath("$.email").value("test@example.com"))
            .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("Should allow admins to list all users")
    @WithMockUser(username = "admin", roles = "ADMIN", authorities = {"sub:2"})
    void shouldAllowAdmins_ToListAllUsers() throws Exception {
        mockMvc.perform(get("/v1/users"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @DisplayName("Should prevent regular users from listing all users")
    @WithMockUser(username = "testuser", roles = "USER", authorities = {"sub:1"})
    void shouldPreventUsers_FromListingAllUsers() throws Exception {
        mockMvc.perform(get("/v1/users"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should allow users to update their own profile")
    @WithMockUser(username = "testuser", roles = "USER", authorities = {"sub:1"})
    void shouldAllowUsers_ToUpdateOwnProfile() throws Exception {
        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("email", "newemail@example.com");

        mockMvc.perform(put("/v1/users/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("newemail@example.com"));

        // Verify update in database
        User updated = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updated.getEmail()).isEqualTo("newemail@example.com");
    }

    @Test
    @DisplayName("Should validate email format in profile updates")
    @WithMockUser(username = "testuser", roles = "USER", authorities = {"sub:1"})
    void shouldValidateEmail_InProfileUpdates() throws Exception {
        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("email", "invalid-email");

        mockMvc.perform(put("/v1/users/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isBadRequest());
    }
}