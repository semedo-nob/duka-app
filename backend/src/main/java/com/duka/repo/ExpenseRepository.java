package com.duka.repo;

import com.duka.domain.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, String> {
    List<Expense> findByBusinessIdOrderByCreatedAtDesc(Long businessId);
}
