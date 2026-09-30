package com.duka.repo;

import com.duka.domain.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    List<InventoryMovement> findTop50ByProductIdOrderByCreatedAtDesc(Long productId);

    List<InventoryMovement> findByProductIdInOrderByCreatedAtDesc(Collection<Long> productIds);
}
