package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "supplier_product_maps")
@Getter
@Setter
@NoArgsConstructor
public class SupplierProductMap {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_name", nullable = false)
    private String supplierName;

    @Column(name = "raw_name", nullable = false)
    private String rawName;

    @Column(name = "product_id", nullable = false)
    private Long productId;
}
