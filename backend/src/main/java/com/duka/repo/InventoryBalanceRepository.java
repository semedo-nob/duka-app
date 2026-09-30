package com.duka.repo;

import com.duka.domain.InventoryBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from InventoryBalance b where b.productId = :id")
    Optional<InventoryBalance> lockByProductId(@Param("id") Long id);
}
