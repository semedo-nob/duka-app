package com.duka.repo;

import com.duka.domain.StockReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StockReceiptRepository extends JpaRepository<StockReceipt, Long> {
    List<StockReceipt> findTop50ByOrderByCreatedAtDesc();

    List<StockReceipt> findBySupplierIdOrderByCreatedAtDesc(Long supplierId);
}
