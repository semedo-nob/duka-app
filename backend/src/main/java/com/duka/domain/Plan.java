package com.duka.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
public class Plan {
    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description = "";

    @Column(name = "price_amount", nullable = false)
    private BigDecimal priceAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private String currency = "KES";

    @Column(name = "billing_interval", nullable = false)
    private String billingInterval = "MONTH";

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "branch_limit")
    private Integer branchLimit;

    @Column(name = "user_limit")
    private Integer userLimit;

    @Column(name = "device_limit")
    private Integer deviceLimit;

    @Column(name = "product_limit")
    private Integer productLimit;
}
