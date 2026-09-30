package com.duka.repo;

import com.duka.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findByBusinessIdOrderByNameAsc(Long businessId);
}
