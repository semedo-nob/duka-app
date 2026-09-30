package com.duka.repo;

import com.duka.domain.EtimsSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EtimsSubmissionRepository extends JpaRepository<EtimsSubmission, Long> {
    Optional<EtimsSubmission> findBySaleId(Long saleId);
    List<EtimsSubmission> findTop50ByOrderByCreatedAtDesc();
}
