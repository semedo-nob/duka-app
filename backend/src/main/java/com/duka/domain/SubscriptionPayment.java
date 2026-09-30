package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "subscription_payments")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Column(name = "plan_code", nullable = false)
    private String planCode;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency = "KES";

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_ref")
    private String providerRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "failure_reason", nullable = false)
    private String failureReason = "";
}
