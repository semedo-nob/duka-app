package com.duka.repo;

import com.duka.domain.SupplierOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierOfferRepository extends JpaRepository<SupplierOffer, Long> {
    List<SupplierOffer> findBySupplierIdOrderByIdAsc(Long supplierId);

    Optional<SupplierOffer> findBySupplierIdAndProductId(Long supplierId, Long productId);
}
