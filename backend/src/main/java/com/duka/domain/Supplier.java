package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
public class Supplier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String contact = "";

    @Column(nullable = false)
    private String products = "";

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(nullable = false)
    private String phone = "";

    @Column(nullable = false)
    private String email = "";

    @Column(nullable = false)
    private String address = "";

    @Column(nullable = false)
    private String category = "";

    @Column(nullable = false)
    private String notes = "";

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, precision = 14, scale = 2)
    private java.math.BigDecimal balance = java.math.BigDecimal.ZERO;
}
