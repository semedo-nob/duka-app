package com.duka.repo;

import com.duka.domain.SubscriptionAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionAccountRepository extends JpaRepository<SubscriptionAccount, Long> {
    List<SubscriptionAccount> findByBusinessIdOrderByCreatedAtDesc(Long businessId);

    Optional<SubscriptionAccount> findByProviderAndProviderRef(String provider, String providerRef);
}
