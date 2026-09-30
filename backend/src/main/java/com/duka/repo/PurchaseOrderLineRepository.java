package com.duka.repo;

import com.duka.domain.PurchaseOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, Long> {
    List<PurchaseOrderLine> findByOrderId(String orderId);
}
