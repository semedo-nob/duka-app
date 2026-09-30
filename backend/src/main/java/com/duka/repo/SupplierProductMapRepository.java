package com.duka.repo;

import com.duka.domain.SupplierProductMap;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SupplierProductMapRepository extends JpaRepository<SupplierProductMap, Long> {
    Optional<SupplierProductMap> findBySupplierNameIgnoreCaseAndRawNameIgnoreCase(String supplierName, String rawName);
}
