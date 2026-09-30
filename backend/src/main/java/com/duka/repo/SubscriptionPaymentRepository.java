package com.duka.repo;

import com.duka.domain.SubscriptionPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {
    List<SubscriptionPayment> findByBusinessIdOrderByCreatedAtDesc(Long businessId);

    Optional<SubscriptionPayment> findByProviderAndProviderRef(String provider, String providerRef);

    long countByProviderAndProviderRefAndStatus(String provider, String providerRef, String status);
}
