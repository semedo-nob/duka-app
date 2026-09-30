package com.duka.repo;

import com.duka.domain.PlanModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanModuleRepository extends JpaRepository<PlanModule, Long> {
    List<PlanModule> findByPlanCode(String planCode);

    void deleteByPlanCode(String planCode);
}
