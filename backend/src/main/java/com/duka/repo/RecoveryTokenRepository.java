package com.duka.repo;

import com.duka.domain.RecoveryToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecoveryTokenRepository extends JpaRepository<RecoveryToken, Long> {
    Optional<RecoveryToken> findByTokenHash(String tokenHash);
}
