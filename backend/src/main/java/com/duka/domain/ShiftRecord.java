package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "shifts")
@Getter
@Setter
@NoArgsConstructor
public class ShiftRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "opening_cash", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingCash = BigDecimal.ZERO;

    @Column(name = "expected_cash", precision = 14, scale = 2)
    private BigDecimal expectedCash;

    @Column(name = "declared_cash", precision = 14, scale = 2)
    private BigDecimal declaredCash;

    @Column(precision = 14, scale = 2)
    private BigDecimal variance;
}
