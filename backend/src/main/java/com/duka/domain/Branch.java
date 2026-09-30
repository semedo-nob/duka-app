package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "branches")
@Getter
@Setter
@NoArgsConstructor
public class Branch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal sales = BigDecimal.ZERO;

    @Column(nullable = false)
    private int staff = 1;

    @Column(name = "low_stock", nullable = false)
    private int lowStock = 0;

    @Column(name = "business_id", nullable = false)
    private Long businessId;
}
