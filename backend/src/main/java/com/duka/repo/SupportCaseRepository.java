package com.duka.repo;

import com.duka.domain.SupportCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportCaseRepository extends JpaRepository<SupportCase, Long> {
    List<SupportCase> findByBusinessIdOrderByUpdatedAtDesc(Long businessId);
}
