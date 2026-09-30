package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "support_cases")
@Getter
@Setter
@NoArgsConstructor
public class SupportCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "opened_by")
    private Long openedBy;

    @Column(nullable = false)
    private String category = "OTHER";

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String summary;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String priority = "NORMAL";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
