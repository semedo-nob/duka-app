package com.duka.repo;

import com.duka.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    List<Supplier> findByBusinessIdOrderByNameAsc(Long businessId);

    Optional<Supplier> findByIdAndBusinessId(Long id, Long businessId);

    Optional<Supplier> findByBusinessIdAndNameIgnoreCase(Long businessId, String name);
}
