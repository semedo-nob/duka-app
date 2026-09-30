package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "supplier_offers")
@Getter
@Setter
@NoArgsConstructor
public class SupplierOffer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "supplier_sku", nullable = false)
    private String supplierSku = "";

    @Column(nullable = false)
    private String alias = "";

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;
}
