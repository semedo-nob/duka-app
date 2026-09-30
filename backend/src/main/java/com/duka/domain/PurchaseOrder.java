package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrder {
    @Id
    private String id;

    @Column(nullable = false)
    private String supplier;

    @Column(nullable = false)
    private int items;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Column(nullable = false)
    private String status;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "stock_applied", nullable = false)
    private boolean stockApplied;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
