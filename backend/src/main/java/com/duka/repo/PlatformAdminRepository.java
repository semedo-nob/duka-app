package com.duka.repo;

import com.duka.domain.PlatformAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, Long> {
    Optional<PlatformAdmin> findByPhone(String phone);
}
