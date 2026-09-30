package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "etims_submissions")
@Getter
@Setter
@NoArgsConstructor
public class EtimsSubmission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sale_id", nullable = false, unique = true)
    private Long saleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EtimsStatus status;

    private String message;

    @Column(name = "external_ref")
    private String externalRef;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
