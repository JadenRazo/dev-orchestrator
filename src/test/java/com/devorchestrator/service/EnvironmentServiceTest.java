package com.devorchestrator.service;

import com.devorchestrator.entity.Environment;
import com.devorchestrator.entity.EnvironmentStatus;
import com.devorchestrator.entity.EnvironmentTemplate;
import com.devorchestrator.entity.User;
import com.devorchestrator.entity.UserRole;
import com.devorchestrator.exception.EnvironmentNotFoundException;
import com.devorchestrator.exception.TemplateNotFoundException;
import com.devorchestrator.repository.EnvironmentRepository;
import com.devorchestrator.repository.EnvironmentTemplateRepository;
import com.devorchestrator.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvironmentServiceTest {

    @Mock
    private EnvironmentRepository environmentRepository;
    
    @Mock
    private EnvironmentTemplateRepository templateRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private ContainerOrchestrationService containerService;
    
    @Mock
    private ResourceMonitoringService resourceService;
    
    @Mock
    private WebSocketNotificationService notificationService;
    
    @InjectMocks
    private EnvironmentService environmentService;

    private User testUser;
    private EnvironmentTemplate testTemplate;
    private Environment testEnvironment;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(environmentService, "maxEnvironmentsPerUser", 5);
        
        testUser = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .role(UserRole.USER)
            .isActive(true)
            .build();

        testTemplate = EnvironmentTemplate.builder()
            .id("web-dev-template")
            .name("Web Development Template")
            .cpuLimit(2.0)
            .memoryLimitMb(4096)
            .build();

        testEnvironment = Environment.builder()
            .id("env-123")
            .name("Test Environment")
            .template(testTemplate)
            .owner(testUser)
            .status(EnvironmentStatus.CREATING)
            .build();
    }

    @Test
    @DisplayName("Should create environment when template exists and resources available")
    void shouldCreateEnvironment_WhenValidRequest() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(templateRepository.findById("web-dev-template")).thenReturn(Optional.of(testTemplate));
        when(environmentRepository.countActiveEnvironmentsByOwnerId(eq(1L))).thenReturn(0);
        when(resourceService.hasAvailableResources(2, 4096L)).thenReturn(true);
        when(containerService.createEnvironment(any(Environment.class), any(EnvironmentTemplate.class)))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        when(environmentRepository.save(any(Environment.class))).thenReturn(testEnvironment);
        
        // When
        Environment result = environmentService.createEnvironment("web-dev-template", 1L, "Test Environment");

        // Then
        assertThat(result.getName()).isEqualTo("Test Environment");
        assertThat(result.getTemplate().getId()).isEqualTo("web-dev-template");
        assertThat(result.getOwner().getId()).isEqualTo(1L);
        verify(containerService).createEnvironment(any(Environment.class), eq(testTemplate));
    }

    @Test
    @DisplayName("Should throw exception when template not found")
    void shouldThrowException_WhenTemplateNotFound() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(templateRepository.findById("invalid-template")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> environmentService.createEnvironment("invalid-template", 1L, "Test"))
            .isInstanceOf(TemplateNotFoundException.class);
    }

    @Test
    @DisplayName("Should get user environments with pagination")
    void shouldGetUserEnvironments_WithPagination() {
        // Given
        List<Environment> environments = List.of(testEnvironment);
        Page<Environment> environmentPage = new PageImpl<>(environments);
        when(environmentRepository.findByOwnerId(eq(1L), any(Pageable.class))).thenReturn(environmentPage);

        // When
        Page<Environment> result = environmentService.getUserEnvironments(1L, Pageable.unpaged());

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo("env-123");
    }

    @Test
    @DisplayName("Should throw exception when environment not found")
    void shouldThrowException_WhenEnvironmentNotFound() {
        // Given
        when(environmentRepository.findByIdAndOwnerId("invalid-env", 1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> environmentService.getEnvironment("invalid-env", 1L))
            .isInstanceOf(EnvironmentNotFoundException.class);
    }

    @Test
    @DisplayName("Should start stopped environment successfully")
    void shouldStartEnvironment_WhenStopped() {
        // Given
        testEnvironment.setStatus(EnvironmentStatus.STOPPED);
        when(environmentRepository.findByIdAndOwnerId("env-123", 1L)).thenReturn(Optional.of(testEnvironment));
        when(environmentRepository.findById("env-123")).thenReturn(Optional.of(testEnvironment));
        when(containerService.startEnvironment(testEnvironment))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        // When
        Environment result = environmentService.startEnvironment("env-123", 1L);

        // Then
        verify(containerService).startEnvironment(testEnvironment);
        verify(environmentRepository).updateEnvironmentStatus("env-123", EnvironmentStatus.STARTING);
    }

    @Test
    @DisplayName("Should stop running environment successfully")
    void shouldStopEnvironment_WhenRunning() {
        // Given
        testEnvironment.setStatus(EnvironmentStatus.RUNNING);
        when(environmentRepository.findByIdAndOwnerId("env-123", 1L)).thenReturn(Optional.of(testEnvironment));
        when(environmentRepository.findById("env-123")).thenReturn(Optional.of(testEnvironment));
        when(containerService.stopEnvironment(testEnvironment))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        // When
        Environment result = environmentService.stopEnvironment("env-123", 1L);

        // Then
        verify(containerService).stopEnvironment(testEnvironment);
        verify(environmentRepository).updateEnvironmentStatus("env-123", EnvironmentStatus.STOPPING);
    }

    @Test
    @DisplayName("Should delete environment and cleanup containers")
    void shouldDeleteEnvironment_WithCleanup() {
        // Given
        testEnvironment.setStatus(EnvironmentStatus.RUNNING);
        when(environmentRepository.findByIdAndOwnerId("env-123", 1L)).thenReturn(Optional.of(testEnvironment));
        when(containerService.stopEnvironment(testEnvironment))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        when(containerService.destroyEnvironment(testEnvironment))
            .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        // When
        environmentService.deleteEnvironment("env-123", 1L);

        // Then
        verify(environmentRepository).updateEnvironmentStatus("env-123", EnvironmentStatus.DELETING);
    }

    @Test
    @DisplayName("Should find stale environments correctly")
    void shouldFindStaleEnvironments() {
        // Given
        List<Environment> staleEnvironments = List.of(testEnvironment);
        when(environmentRepository.findByStatusAndLastAccessedBefore(eq(EnvironmentStatus.RUNNING), any(LocalDateTime.class)))
            .thenReturn(staleEnvironments);

        // When
        List<Environment> result = environmentService.findStaleEnvironments(24);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("env-123");
    }
}