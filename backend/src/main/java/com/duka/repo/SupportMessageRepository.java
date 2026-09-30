package com.duka.repo;

import com.duka.domain.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    List<SupportMessage> findByCaseIdOrderByCreatedAtAsc(Long caseId);
}
