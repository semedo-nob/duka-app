package com.duka.repo;

import com.duka.domain.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, String> {
    List<PurchaseOrder> findByBusinessIdOrderByCreatedAtDesc(Long businessId);

    List<PurchaseOrder> findByBusinessIdAndSupplierIdOrderByCreatedAtDesc(Long businessId, Long supplierId);
}
