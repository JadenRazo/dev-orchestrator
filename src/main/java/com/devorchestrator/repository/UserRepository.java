package com.devorchestrator.repository;

import com.devorchestrator.entity.User;
import com.devorchestrator.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findByRole(UserRole role);

    Page<User> findByRole(UserRole role, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.role = :role AND SIZE(u.environments) > 0")
    List<User> findActiveUsersByRole(@Param("role") UserRole role);

    @Query("SELECT COUNT(e) FROM User u JOIN u.environments e WHERE u.id = :userId")
    int countEnvironmentsByUserId(@Param("userId") Long userId);

    @Query("SELECT u FROM User u WHERE SIZE(u.environments) >= u.maxEnvironments")
    List<User> findUsersAtEnvironmentLimit();

    @Query("SELECT AVG(SIZE(u.environments)) FROM User u")
    Double getAverageEnvironmentsPerUser();

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= CURRENT_DATE")
    long countUsersCreatedToday();

    List<User> findByIsActiveTrue();

    long countByIsActiveTrue();

    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginTime WHERE u.id = :userId")
    void updateLastLoginTime(@Param("userId") Long userId, @Param("loginTime") LocalDateTime loginTime);
}