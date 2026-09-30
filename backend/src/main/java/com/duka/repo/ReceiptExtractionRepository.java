package com.duka.repo;

import com.duka.domain.ReceiptExtraction;
import com.duka.domain.ReceiptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReceiptExtractionRepository extends JpaRepository<ReceiptExtraction, Long> {
    List<ReceiptExtraction> findByStatusOrderByCreatedAtDesc(ReceiptStatus status);
}
