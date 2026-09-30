package com.duka.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {
    @Id
    private String id;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String action;

    @Column(name = "business_id")
    private Long businessId;

    @Column(name = "entity_type", nullable = false)
    private String entityType = "";

    @Column(name = "entity_id", nullable = false)
    private String entityId = "";

    @Column(name = "device_id", nullable = false)
    private String deviceId = "";

    @Column(nullable = false)
    private String detail = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
