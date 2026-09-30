package com.duka.repo;

import com.duka.domain.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {
    List<UserSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<UserSession> findByBusinessIdOrderByCreatedAtDesc(Long businessId);

    Optional<UserSession> findByIdAndUserId(String id, Long userId);

    Optional<UserSession> findByIdAndBusinessId(String id, Long businessId);
}
