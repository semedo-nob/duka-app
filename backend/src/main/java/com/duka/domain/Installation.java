package com.duka.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "installations")
@Getter
@Setter
@NoArgsConstructor
public class Installation {
    @Id
    private String id;

    @Column(name = "business_id")
    private Long businessId;

    @Column(name = "app_version", nullable = false)
    private String appVersion = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
}
