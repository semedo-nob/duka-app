package com.duka.repo;

import com.duka.domain.AccountStatus;
import com.duka.domain.UserAccount;
import com.duka.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByPhone(String phone);

    List<UserAccount> findByBusinessIdOrderByNameAsc(Long businessId);

    Optional<UserAccount> findByIdAndBusinessId(Long id, Long businessId);

    long countByBusinessIdAndRoleAndStatus(Long businessId, UserRole role, AccountStatus status);
}
