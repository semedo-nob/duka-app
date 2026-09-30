package com.duka.repo;

import com.duka.domain.Sale;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    Optional<Sale> findByIdempotencyKey(String idempotencyKey);

    List<Sale> findTop50ByBusinessIdOrderByCreatedAtDesc(String businessId);

    List<Sale> findByBusinessIdOrderByCreatedAtDesc(String businessId);

    List<Sale> findByBusinessIdAndCustomerIdOrderByCreatedAtDesc(String businessId, Long customerId);

    List<Sale> findByBusinessIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(String businessId, Instant from);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> lockById(@Param("id") Long id);
}
