package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales")
@Getter
@Setter
@NoArgsConstructor
public class Sale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SaleStatus status;

    @Column(nullable = false)
    private String method;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "stock_applied", nullable = false)
    private boolean stockApplied;

    @Column(nullable = false)
    private boolean refunded;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "installation_id")
    private String installationId;

    @Column(name = "client_sale_no")
    private String clientSaleNo;

    @Column(name = "business_id")
    private String businessId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<SaleItem> items = new ArrayList<>();
}
