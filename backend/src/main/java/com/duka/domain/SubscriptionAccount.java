package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "plan_code", nullable = false)
    private String planCode;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String provider = "unconfigured";

    @Column(name = "provider_ref")
    private String providerRef;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "renews_at")
    private Instant renewsAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "grace_until")
    private Instant graceUntil;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
