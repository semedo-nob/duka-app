package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "businesses")
@Getter
@Setter
@NoArgsConstructor
public class Business {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "legal_name", nullable = false)
    private String legalName = "";

    @Column(name = "business_type", nullable = false)
    private String businessType = "";

    @Column(nullable = false)
    private String phone = "";

    @Column(nullable = false)
    private String email = "";

    @Column(nullable = false)
    private String address = "";

    @Column(nullable = false)
    private String category = "";

    @Column(name = "registration_number", nullable = false)
    private String registrationNumber = "";

    @Column(name = "kra_pin", nullable = false)
    private String kraPin = "";

    @Column(nullable = false)
    private String currency = "KES";

    @Column(nullable = false)
    private String timezone = "Africa/Nairobi";

    @Column(name = "receipt_footer", nullable = false)
    private String receiptFooter = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BusinessStatus status = BusinessStatus.ACTIVE;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "retention_until")
    private Instant retentionUntil;

    @Column(name = "status_reason", nullable = false)
    private String statusReason = "";

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "status_changed_by", nullable = false)
    private String statusChangedBy = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
