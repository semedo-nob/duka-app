package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "billing_events")
@Getter
@Setter
@NoArgsConstructor
public class BillingEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String provider;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "business_id")
    private Long businessId;

    @Column(name = "provider_ref", nullable = false)
    private String providerRef = "";

    @Column(nullable = false)
    private String summary = "";

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @Column(nullable = false)
    private boolean processed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
