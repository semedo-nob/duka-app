package com.duka.repo;

import com.duka.domain.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    List<Warehouse> findByBusinessIdOrderByNameAsc(Long businessId);
}
