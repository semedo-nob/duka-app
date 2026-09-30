package com.duka.repo;

import com.duka.domain.ShiftRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftRecordRepository extends JpaRepository<ShiftRecord, Long> {
}
