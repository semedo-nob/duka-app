package com.duka.repo;

import com.duka.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {
    List<AuditEvent> findTop100ByBusinessIdOrderByCreatedAtDesc(Long businessId);

    List<AuditEvent> findTop50ByOrderByCreatedAtDesc();
}
