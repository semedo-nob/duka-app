package com.duka.repo;

import com.duka.domain.BillingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillingEventRepository extends JpaRepository<BillingEvent, Long> {
    Optional<BillingEvent> findByProviderAndEventId(String provider, String eventId);

    List<BillingEvent> findTop100ByOrderByCreatedAtDesc();

    List<BillingEvent> findByBusinessIdOrderByCreatedAtDesc(Long businessId);
}
