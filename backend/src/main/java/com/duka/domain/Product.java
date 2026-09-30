package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(unique = true)
    private String barcode;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private String unit = "each";

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal cost;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(name = "tax_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal taxRate = new BigDecimal("0.1600");

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel = 0;

    @Column(nullable = false)
    private String emoji = "📦";

    @Column(nullable = false)
    private String color = "#EDEBE3";

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(nullable = false)
    private String brand = "";

    @Column(name = "tax_category", nullable = false)
    private String taxCategory = "STANDARD";

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "parent_product_id")
    private Long parentProductId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
