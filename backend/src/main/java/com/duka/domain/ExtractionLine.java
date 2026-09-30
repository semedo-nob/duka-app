package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "extraction_lines")
@Getter
@Setter
@NoArgsConstructor
public class ExtractionLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "extraction_id")
    private ReceiptExtraction extraction;

    @Column(name = "raw_name", nullable = false)
    private String rawName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitCost;

    private String barcode;

    @Column(name = "matched_product_id")
    private Long matchedProductId;

    @Column(name = "suggested_product_id")
    private Long suggestedProductId;

    @Column(name = "match_method")
    private String matchMethod;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(nullable = false)
    private boolean removed;
}
