package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
public class Expense {
    @Id
    private String id;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String method;

    @Column(nullable = false)
    private String description = "";

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
