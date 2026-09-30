package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "plan_modules")
@Getter
@Setter
@NoArgsConstructor
public class PlanModule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_code", nullable = false)
    private String planCode;

    @Column(name = "module_key", nullable = false)
    private String moduleKey;
}
