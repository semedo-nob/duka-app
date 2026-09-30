package com.duka.repo;

import com.duka.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByBusinessIdOrderByNameAsc(Long businessId);

    Optional<Customer> findByIdAndBusinessId(Long id, Long businessId);
}
