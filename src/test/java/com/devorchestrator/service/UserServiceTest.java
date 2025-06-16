package com.devorchestrator.service;

import com.devorchestrator.entity.User;
import com.devorchestrator.entity.UserRole;
import com.devorchestrator.exception.UserNotFoundException;
import com.devorchestrator.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .passwordHash("$2a$10$hashedPassword")
            .role(UserRole.USER)
            .isActive(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    @Test
    @DisplayName("Should load user by username successfully")
    void shouldLoadUserByUsername_WhenUserExists() {
        // Given
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        // When
        UserDetails result = userService.loadUserByUsername("testuser");

        // Then
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getPassword()).isEqualTo("$2a$10$hashedPassword");
        assertThat(result.getAuthorities()).hasSize(1);
        assertThat(result.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_USER");
        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should throw exception when user not found during load")
    void shouldThrowException_WhenUserNotFoundDuringLoad() {
        // Given
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.loadUserByUsername("nonexistent"))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessageContaining("User not found: nonexistent");
    }

    @Test
    @DisplayName("Should get user by ID successfully")
    void shouldGetUser_WhenUserExists() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        User result = userService.getUser(1L);

        // Then
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Should throw exception when user not found by ID")
    void shouldThrowException_WhenUserNotFoundById() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.getUser(999L))
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("Should create new user successfully")
    void shouldCreateUser_WhenValidData() {
        // Given
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        User result = userService.createUser("newuser", "new@example.com", "password123", UserRole.USER);

        // Then
        verify(userRepository).save(any(User.class));
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Should throw exception when username already exists")
    void shouldThrowException_WhenUsernameExists() {
        // Given
        when(userRepository.findByUsername("existinguser")).thenReturn(Optional.of(testUser));

        // When & Then
        assertThatThrownBy(() -> userService.createUser("existinguser", "new@example.com", "password123", UserRole.USER))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Username already exists");
    }

    @Test
    @DisplayName("Should throw exception when email already exists")
    void shouldThrowException_WhenEmailExists() {
        // Given
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(testUser));

        // When & Then
        assertThatThrownBy(() -> userService.createUser("newuser", "existing@example.com", "password123", UserRole.USER))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Email already exists");
    }

    @Test
    @DisplayName("Should update user role successfully")
    void shouldUpdateUserRole_WhenUserExists() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        User result = userService.updateUserRole(1L, UserRole.ADMIN);

        // Then
        verify(userRepository).save(any(User.class));
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Should validate password correctly")
    void shouldValidatePassword() {
        // Given
        when(passwordEncoder.matches("rawPassword", "encodedPassword")).thenReturn(true);
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        // When & Then
        assertThat(userService.validatePassword("rawPassword", "encodedPassword")).isTrue();
        assertThat(userService.validatePassword("wrongPassword", "encodedPassword")).isFalse();
    }

    @Test
    @DisplayName("Should change password when current password is correct")
    void shouldChangePassword_WhenCurrentPasswordCorrect() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("currentPassword", testUser.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("newPassword")).thenReturn("$2a$10$newEncodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        userService.changePassword(1L, "currentPassword", "newPassword");

        // Then
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw exception when current password is incorrect")
    void shouldThrowException_WhenCurrentPasswordIncorrect() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", testUser.getPasswordHash())).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> userService.changePassword(1L, "wrongPassword", "newPassword"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Current password is incorrect");
    }

    @Test
    @DisplayName("Should check if user is admin correctly")
    void shouldCheckIsAdmin() {
        // Given
        User adminUser = User.builder()
            .id(2L)
            .username("adminuser")
            .role(UserRole.ADMIN)
            .build();
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(adminUser));

        // When & Then
        assertThat(userService.isAdmin(1L)).isFalse();
        assertThat(userService.isAdmin(2L)).isTrue();
    }

    @Test
    @DisplayName("Should return false for non-existent user admin check")
    void shouldReturnFalse_WhenUserNotFoundForAdminCheck() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThat(userService.isAdmin(999L)).isFalse();
    }
}