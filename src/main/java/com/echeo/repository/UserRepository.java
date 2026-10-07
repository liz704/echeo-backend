package com.echeo.repository;

import com.echeo.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByWeekPlanToken(UUID weekPlanToken);

    Optional<User> findByEmailVerificationToken(UUID emailVerificationToken);

    List<User> findByWeekPlanEnabledTrue();
}
