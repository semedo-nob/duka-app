package com.duka.repo;

import com.duka.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByBusinessIdOrderByNameAsc(Long businessId);

    Optional<Category> findByIdAndBusinessId(Long id, Long businessId);

    Optional<Category> findByBusinessIdAndNameIgnoreCaseAndParentIdIsNull(Long businessId, String name);

    Optional<Category> findByBusinessIdAndParentIdAndNameIgnoreCase(Long businessId, Long parentId, String name);

    long countByParentId(Long parentId);
}
