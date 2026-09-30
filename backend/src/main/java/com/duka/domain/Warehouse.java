package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "warehouses")
@Getter
@Setter
@NoArgsConstructor
public class Warehouse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String branch;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "stock_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal stockValue = BigDecimal.ZERO;
}
